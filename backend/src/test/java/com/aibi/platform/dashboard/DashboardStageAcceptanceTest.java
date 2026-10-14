package com.aibi.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.dashboard.dto.DashboardDtos.CreateChartRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateDashboardRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardQueryRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.UpdateChartRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.WidgetInput;
import com.aibi.platform.dashboard.manager.ChartSpecValidator;
import com.aibi.platform.dashboard.manager.DashboardDefinitionValidator;
import com.aibi.platform.dashboard.mapper.InMemoryDashboardMapper;
import com.aibi.platform.dashboard.service.ChartService;
import com.aibi.platform.dashboard.service.DashboardService;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.query.dto.QueryDtos.Column;
import com.aibi.platform.query.dto.QueryDtos.PublicationSnapshot;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.aibi.platform.query.service.QueryService;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.mapper.InMemorySemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardStageAcceptanceTest {

    private static final String TOKEN = "Bearer token";
    private static final String VIEWER_TOKEN = "Bearer viewer-token";
    private static final long TENANT = 1;
    private static final long WORKSPACE = 20;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void completesVersionedChartDashboardAndPermissionAwareQueryLoop() throws Exception {
        InMemoryDashboardMapper mapper = new InMemoryDashboardMapper();
        InMemorySemanticMapper semantics = new InMemorySemanticMapper();
        semantics.insertDatasetVersion(new DatasetVersion(101, TENANT, 100, 1, 10L,
                List.of(), List.of(), List.of(), "hash", "PUBLISHED", Instant.now(), 0));
        SemanticAccessManager access = access();
        QueryService queries = mock(QueryService.class);
        QueryPlan plan = new QueryPlan(101, List.of(201L), List.of(301L),
                null, List.of(), List.of(), 100);
        when(queries.validate(TOKEN, TENANT, plan)).thenReturn(
                new ValidationData(true, "plan", 101, List.of(201L), List.of()));
        ChartService charts = new ChartService(mapper, semantics, access,
                new ChartSpecValidator(), queries);
        var spec = json.readTree("""
                {"type":"BAR","categoryField":"dimension_301",
                 "interactionFieldId":301,"valueFields":["metric_201"],
                 "drill":{"enabled":true,"target":"DETAIL","dimensionField":"dimension_301"}}
                """);

        var created = charts.create(TOKEN, TENANT,
                new CreateChartRequest(WORKSPACE, "net_sales_by_region", "区域净销售额",
                        101, plan, spec));
        var published = charts.publish(TOKEN, TENANT, created.chart().id());
        var edited = charts.update(TOKEN, TENANT, created.chart().id(),
                new UpdateChartRequest("区域净销售额（调整）", 101, plan, spec, 1));

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(edited.draft().versionNo()).isEqualTo(2);
        assertThat(mapper.findChartVersion(TENANT, published.id()).orElseThrow().status())
                .isEqualTo("PUBLISHED");

        DashboardService dashboards = new DashboardService(mapper, access,
                new DashboardDefinitionValidator(), queries);
        var dashboard = dashboards.create(TOKEN, TENANT, new CreateDashboardRequest(
                WORKSPACE, "sales_overview", "销售总览", json.readTree("{\"columns\":12}"),
                json.readTree("[]"), List.of(new WidgetInput("sales", published.id(),
                        json.readTree("{\"x\":0,\"y\":0,\"w\":6,\"h\":4}")))));
        var dashboardVersion = dashboards.publish(TOKEN, TENANT, dashboard.dashboard().id());
        QueryResult metricResult = new QueryResult("q1", "SUCCEEDED",
                List.of(new Column("dimension_301", "区域", "STRING", "DIMENSION"),
                        new Column("metric_201", "净销售额", "DECIMAL", "METRIC")),
                List.of(Map.of("dimension_301", "华东", "metric_201", new BigDecimal("129.50"))),
                1, false, new PublicationSnapshot(7, 8, 9), 12);
        when(queries.execute(eq(TOKEN), eq(TENANT), any(QueryPlan.class))).thenReturn(metricResult);

        var result = dashboards.query(TOKEN, TENANT, dashboard.dashboard().id(),
                new DashboardQueryRequest(null, List.of(), List.of(), false));
        QueryResult viewerMetricResult = new QueryResult("q2", "SUCCEEDED",
                metricResult.columns(),
                List.of(Map.of("dimension_301", "华东",
                        "metric_201", new BigDecimal("79.25"))),
                1, false, new PublicationSnapshot(7, 8, 9), 10);
        when(queries.execute(eq(VIEWER_TOKEN), eq(TENANT), any(QueryPlan.class)))
                .thenReturn(viewerMetricResult);
        var viewerResult = dashboards.query(VIEWER_TOKEN, TENANT, dashboard.dashboard().id(),
                new DashboardQueryRequest(null, List.of(), List.of(), false));

        assertThat(dashboardVersion.status()).isEqualTo("PUBLISHED");
        assertThat(result.widgets()).hasSize(1);
        assertThat(result.widgets().getFirst().status()).isEqualTo("SUCCEEDED");
        assertThat(result.widgets().getFirst().result().rows().getFirst().get("metric_201"))
                .isEqualTo(new BigDecimal("129.50"));
        assertThat(viewerResult.widgets().getFirst().result().rows().getFirst().get("metric_201"))
                .isEqualTo(new BigDecimal("79.25"));
        assertThat(viewerResult.widgets().getFirst().result().rows().getFirst().get("metric_201"))
                .isNotEqualTo(result.widgets().getFirst().result().rows().getFirst().get("metric_201"));
    }

    private SemanticAccessManager access() {
        SemanticAccessManager access = mock(SemanticAccessManager.class);
        TokenService.TenantClaims claims = new TokenService.TenantClaims(
                1, 2, TENANT, 3, 0, 0, Instant.now().plusSeconds(300));
        when(access.requireWorkspace(TOKEN, TENANT, WORKSPACE)).thenReturn(claims);
        when(access.requireTenant(TOKEN, TENANT)).thenReturn(claims);
        when(access.canAccessWorkspace(TENANT, WORKSPACE, 3)).thenReturn(true);
        when(access.hasPermission(any(), eq(TENANT), eq(WORKSPACE), any(), any()))
                .thenReturn(true);
        doNothing().when(access).requirePermission(any(), eq(TENANT), eq(WORKSPACE), any(), any());
        return access;
    }
}
