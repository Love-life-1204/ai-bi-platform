package com.aibi.platform.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.ingestion.dto.IngestionDtos.PublicationData;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.TimeRange;
import com.aibi.platform.query.manager.PhysicalMappingManager;
import com.aibi.platform.query.manager.QueryPlanCompiler;
import com.aibi.platform.query.model.AuthorizedQuery;
import com.aibi.platform.query.model.QueryModels.BoundParameter;
import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import com.aibi.platform.semantic.mapper.InMemorySemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryPlanCompilerTest {

    @Test
    void compilesNetSalesCaseExpressionToParameterizedClickHouseIf() throws Exception {
        DatasetField status = field(1001, "order_status", "order_status", "STRING", "DIMENSION");
        DatasetField paid = field(1002, "paid_minor_units", "paid_minor_units", "LONG", "MEASURE");
        DatasetField refunded = field(1003, "refunded_minor_units", "refunded_minor_units", "LONG", "MEASURE");
        Dataset dataset = new Dataset(10, 1, 20, "sales_orders", "销售订单",
                "PUBLISHED", 104L, 99, Instant.now(), Instant.now(), 0);
        DatasetVersion datasetVersion = new DatasetVersion(104, 1, 10, 4, 1001L,
                List.of(status, paid, refunded), List.of(), List.of(), "a".repeat(64),
                "PUBLISHED", Instant.now(), 0);
        Metric metric = new Metric(30, 1, 20, "net_sales", "净销售额", "CNY",
                "PUBLISHED", 301L, 99, Instant.now(), Instant.now(), 0);
        MetricVersion metricVersion = new MetricVersion(301, 1, 30, 1, 104,
                new ObjectMapper().readTree("""
                        {"op":"DIV","args":[
                          {"op":"SUM","args":[{"op":"CASE","args":[
                            {"op":"EQ","args":[
                              {"op":"FIELD","field":"order_status"},
                              {"op":"LITERAL","value":"PAID"}]},
                            {"op":"SUB","args":[
                              {"op":"FIELD","field":"paid_minor_units"},
                              {"op":"FIELD","field":"refunded_minor_units"}]},
                            {"op":"LITERAL","value":0}]}]},
                          {"op":"LITERAL","value":100}]}
                        """), new ObjectMapper().createObjectNode(),
                new ObjectMapper().createObjectNode(), List.of(), 2, List.of(),
                "b".repeat(64), "PUBLISHED", Instant.now(), 0);
        QueryPlan plan = new QueryPlan(104, List.of(301L), List.of(), null,
                List.of(), List.of(), 100);
        AuthorizedQuery query = new AuthorizedQuery(1, 99, 0, 0, plan, dataset,
                datasetVersion, List.of(metric), List.of(metricVersion),
                new PublicationData(10, 1L, 7, 1, 8L, 0),
                new DataPolicyEvaluationData(10, Map.of(), List.of()),
                "plan-hash", "policy-hash");

        CompiledQuery compiled = new QueryPlanCompiler(
                new PhysicalMappingManager(), new InMemorySemanticMapper()).compile(query);

        assertThat(compiled.sql()).contains("sum(if(", "facts.`field_1001` = ?",
                "facts.`field_1002` - facts.`field_1003`", "/ nullIf(?, 0)");
        assertThat(compiled.parameters()).extracting(BoundParameter::value)
                .startsWith("PAID", 0L, 100L);
    }

    @Test
    void leapDayTimeRangeUsesUtcInstantsAndHalfOpenBoundary() throws Exception {
        DatasetField eventTime = field(1004, "business_time", "business_time",
                "DATETIME", "DIMENSION");
        Dataset dataset = new Dataset(10, 1, 20, "sales_orders", "销售订单",
                "PUBLISHED", 104L, 99, Instant.now(), Instant.now(), 0);
        DatasetVersion datasetVersion = new DatasetVersion(104, 1, 10, 4, 1001L,
                List.of(eventTime), List.of(), List.of(), "a".repeat(64),
                "PUBLISHED", Instant.now(), 0);
        Metric metric = new Metric(30, 1, 20, "orders", "订单量", "COUNT",
                "PUBLISHED", 301L, 99, Instant.now(), Instant.now(), 0);
        MetricVersion metricVersion = new MetricVersion(301, 1, 30, 1, 104,
                new ObjectMapper().readTree("{\"op\":\"COUNT\",\"args\":[]}"),
                new ObjectMapper().createObjectNode(), new ObjectMapper().createObjectNode(),
                List.of(), 0, List.of(), "b".repeat(64), "PUBLISHED", Instant.now(), 0);
        TimeRange range = new TimeRange(1004,
                OffsetDateTime.parse("2024-02-29T00:00:00+08:00"),
                OffsetDateTime.parse("2024-03-01T00:00:00+08:00"));
        QueryPlan plan = new QueryPlan(104, List.of(301L), List.of(), range,
                List.of(), List.of(), 100);
        AuthorizedQuery query = new AuthorizedQuery(1, 99, 0, 0, plan, dataset,
                datasetVersion, List.of(metric), List.of(metricVersion),
                new PublicationData(10, 1L, 7, 1, 8L, 0),
                new DataPolicyEvaluationData(10, Map.of(), List.of()), "plan", "policy");

        CompiledQuery compiled = new QueryPlanCompiler(
                new PhysicalMappingManager(), new InMemorySemanticMapper()).compile(query);

        assertThat(compiled.sql()).contains("facts.`field_1004` >= ?", "facts.`field_1004` < ?");
        assertThat(compiled.parameters()).extracting(BoundParameter::value)
                .contains(java.sql.Timestamp.from(Instant.parse("2024-02-28T16:00:00Z")),
                        java.sql.Timestamp.from(Instant.parse("2024-02-29T16:00:00Z")));
    }

    private DatasetField field(long id, String key, String physicalName,
            String dataType, String role) {
        return new DatasetField(id, key, id, physicalName, key, dataType, role, "INTERNAL");
    }
}
