package com.aibi.platform.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.semantic.dto.SemanticDtos.CalculationResult;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateMetricRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.MetricData;
import com.aibi.platform.semantic.dto.SemanticDtos.OrderSample;
import com.aibi.platform.semantic.manager.MetricCalculationManager;
import com.aibi.platform.semantic.manager.MetricExpressionValidator;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.manager.SemanticDefinitionManager;
import com.aibi.platform.semantic.mapper.InMemorySemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.aibi.platform.semantic.service.MetricService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NetSalesMetricStageAcceptanceTest {

    private static final String AUTHORIZATION = "Bearer acceptance-token";
    private static final long TENANT_ID = 1;
    private static final long WORKSPACE_ID = 20;
    private static final long DATASET_ID = 10;
    private static final long DATASET_VERSION_ID = 104;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsValidatesAndPublishesExplainableNetSalesMetric() throws Exception {
        InMemorySemanticMapper mapper = new InMemorySemanticMapper();
        seedPublishedOrdersDataset(mapper);
        SemanticAccessManager access = mock(SemanticAccessManager.class);
        when(access.requireWorkspace(AUTHORIZATION, TENANT_ID, WORKSPACE_ID)).thenReturn(
                new TokenService.TenantClaims(TENANT_ID, 2, 1, 99, 0, 0,
                        Instant.now().plusSeconds(300)));
        MetricService service = new MetricService(mapper, access,
                new SemanticDefinitionManager(mapper, objectMapper),
                new MetricExpressionValidator(mapper));

        MetricData created = service.create(AUTHORIZATION, TENANT_ID, new CreateMetricRequest(
                WORKSPACE_ID, "net_sales", "净销售额", "CNY", DATASET_VERSION_ID,
                netSalesFormula(), timeSpec(), currencySpec(),
                List.of("region_id", "business_time"), 2, List.of()));

        assertThat(created.metric().status()).isEqualTo("DRAFT");
        assertThat(created.draft().datasetVersionId()).isEqualTo(DATASET_VERSION_ID);
        assertThat(service.validate(AUTHORIZATION, TENANT_ID, created.metric().id()).valid()).isTrue();

        MetricVersion published = service.publish(AUTHORIZATION, TENANT_ID, created.metric().id());
        Metric current = mapper.findMetric(TENANT_ID, created.metric().id()).orElseThrow();

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.versionNo()).isEqualTo(1);
        assertThat(published.datasetVersionId()).isEqualTo(DATASET_VERSION_ID);
        assertThat(published.definitionHash()).hasSize(64);
        assertThat(current.status()).isEqualTo("PUBLISHED");
        assertThat(current.currentVersionId()).isEqualTo(published.id());
        assertThat(service.versions(AUTHORIZATION, TENANT_ID, current.id()))
                .extracting(MetricVersion::status).containsExactly("PUBLISHED");
        assertThat(published.formulaAst().toString())
                .contains("order_status", "PAID", "paid_minor_units", "refunded_minor_units");

        CalculationResult sample = new MetricCalculationManager().netSales(List.of(
                order("PAID", "100", "0"),
                order("PAID", "200", "50"),
                order("CANCELLED", "300", "0")), "CNY", Map.of(), 2);
        assertThat(sample).isEqualTo(new CalculationResult("DEFINED", "250.00", "CNY"));
    }

    private void seedPublishedOrdersDataset(InMemorySemanticMapper mapper) {
        mapper.insertDataset(new Dataset(DATASET_ID, TENANT_ID, WORKSPACE_ID,
                "sales_orders", "销售订单", "PUBLISHED", DATASET_VERSION_ID, 99,
                Instant.now(), Instant.now(), 0));
        mapper.insertDatasetVersion(new DatasetVersion(DATASET_VERSION_ID, TENANT_ID,
                DATASET_ID, 4, 1001L, List.of(
                        field(1001, "order_status", "order_status", "订单状态", "STRING", "DIMENSION"),
                        field(1002, "paid_minor_units", "paid_minor_units", "实付金额（分）", "LONG", "MEASURE"),
                        field(1003, "refunded_minor_units", "refunded_minor_units",
                                "已确认退款金额（分）", "LONG", "MEASURE"),
                        field(1004, "business_time", "business_time", "支付业务时间", "DATETIME", "DIMENSION"),
                        field(1005, "currency", "currency", "币种", "STRING", "DIMENSION"),
                        field(1006, "region_id", "region_id", "区域", "STRING", "DIMENSION")),
                List.of(), List.of(), "a".repeat(64), "PUBLISHED", Instant.now(), 0));
    }

    private DatasetField field(long id, String key, String physicalName, String businessName,
            String dataType, String role) {
        return new DatasetField(id, key, id, physicalName, businessName,
                dataType, role, "INTERNAL");
    }

    private JsonNode netSalesFormula() throws Exception {
        return objectMapper.readTree("""
                {"op":"DIV","args":[
                  {"op":"SUM","args":[
                    {"op":"CASE","args":[
                      {"op":"EQ","args":[
                        {"op":"FIELD","field":"order_status"},
                        {"op":"LITERAL","value":"PAID"}]},
                      {"op":"SUB","args":[
                        {"op":"FIELD","field":"paid_minor_units"},
                        {"op":"FIELD","field":"refunded_minor_units"}]},
                      {"op":"LITERAL","value":0}]}
                  ]},
                  {"op":"LITERAL","value":100}
                ]}
                """);
    }

    private JsonNode timeSpec() throws Exception {
        return objectMapper.readTree("""
                {"timezone":"Asia/Shanghai","eventTimeField":"business_time",
                 "startInclusive":true,"endExclusive":true}
                """);
    }

    private JsonNode currencySpec() throws Exception {
        return objectMapper.readTree("""
                {"mode":"SINGLE","currencyField":"currency"}
                """);
    }

    private OrderSample order(String status, String paid, String refunded) {
        return new OrderSample(status, "CNY", new BigDecimal(paid), new BigDecimal(refunded));
    }
}
