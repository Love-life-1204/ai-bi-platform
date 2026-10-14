package com.aibi.platform.p0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.ai.mapper.InMemoryAiStore;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.ai.service.AiSemanticToolService;
import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateChartRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateDashboardRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.WidgetInput;
import com.aibi.platform.dashboard.manager.ChartSpecValidator;
import com.aibi.platform.dashboard.manager.DashboardDefinitionValidator;
import com.aibi.platform.dashboard.mapper.InMemoryDashboardMapper;
import com.aibi.platform.dashboard.service.ChartService;
import com.aibi.platform.dashboard.service.DashboardService;
import com.aibi.platform.identity.dto.AccessDecisionData;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.AccessDecisionService;
import com.aibi.platform.identity.service.DataPolicyService;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateSyncJobRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.ingestion.manager.FieldMappingManager;
import com.aibi.platform.ingestion.manager.FileImportManager;
import com.aibi.platform.ingestion.manager.ImportWorkerManager;
import com.aibi.platform.ingestion.manager.IngestionProperties;
import com.aibi.platform.ingestion.manager.QualityManager;
import com.aibi.platform.ingestion.manager.SourceTableReader;
import com.aibi.platform.ingestion.manager.SyncWorkerManager;
import com.aibi.platform.ingestion.mapper.InMemoryIngestionMapper;
import com.aibi.platform.ingestion.service.IngestionServiceImpl;
import com.aibi.platform.manager.TenantManager;
import com.aibi.platform.mapper.InMemoryTenantMapper;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.aibi.platform.query.service.QueryService;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateDatasetRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateMetricRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.FieldInput;
import com.aibi.platform.semantic.manager.DatasetPreviewManager;
import com.aibi.platform.semantic.manager.MetricExpressionValidator;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.manager.SemanticDefinitionManager;
import com.aibi.platform.semantic.mapper.InMemorySemanticMapper;
import com.aibi.platform.semantic.service.DatasetService;
import com.aibi.platform.semantic.service.MetricService;
import com.aibi.platform.service.impl.TenantServiceImpl;
import com.aibi.platform.source.connector.ConnectorRegistry;
import com.aibi.platform.source.connector.ConnectorResult;
import com.aibi.platform.source.connector.SourceConnector;
import com.aibi.platform.source.dto.CreateDatasourceRequest;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import com.aibi.platform.source.model.SourceColumn;
import com.aibi.platform.source.model.SourceObject;
import com.aibi.platform.source.security.EncryptedCredentialStore;
import com.aibi.platform.source.service.DatasourceServiceImpl;
import com.aibi.platform.source.store.InMemoryDatasourceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** P0 golden path: one enterprise reaches a published dashboard and an AI-visible metric. */
class P0BusinessJourneyAcceptanceTest {

    private static final String AUTHORIZATION = "Bearer p0-token";
    private static final long WORKSPACE_ID = 7001;
    private static final long MEMBER_ID = 8001;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onboardsEnterpriseAndPublishesFirstDashboardWithAiCatalog() throws Exception {
        var tenants = new TenantManager(new InMemoryTenantMapper());
        var members = new com.aibi.platform.identity.mapper.InMemoryMemberMapper();
        var users = new com.aibi.platform.identity.mapper.InMemoryUserMapper();
        var tokens = new TokenService();
        var sessions = new com.aibi.platform.identity.service.SessionService(
                new com.aibi.platform.identity.mapper.InMemorySessionMapper(), tokens, users);
        Instant registered = Instant.now();
        var user = users.insert(new com.aibi.platform.identity.entity.UserEntity(
                5001, "p0-owner@example.com", "P0所有者", "not-used-for-session", "ACTIVE",
                null, registered, registered, 0));
        var contexts = new com.aibi.platform.identity.service.PermissionContextService(sessions, tenants, members, tokens);
        var actions = new com.aibi.platform.identity.service.TenantActionAuthorizer(
                contexts, members, new com.aibi.platform.identity.mapper.InMemoryAccessControlMapper());
        String onboarding = "Bearer " + sessions.createSession(user, null).accessToken();
        var tenant = new TenantServiceImpl(tenants, sessions, contexts, actions, members, users,
                new com.aibi.platform.identity.mapper.InMemoryAccessControlMapper(),
                com.aibi.platform.identity.service.SecurityAudit.inMemory())
                .createTenant(onboarding, new CreateTenantRequest(
                        "p0_enterprise", "P0 试点企业", "Asia/Shanghai", "CNY"));
        long tenantId = tenant.id();
        SemanticAccessManager access = permittedAccess(tenantId);

        DatasourceServiceImpl sources = datasourceService();
        var datasource = sources.create(AUTHORIZATION, tenantId, new CreateDatasourceRequest(
                WORKSPACE_ID, "orders_mysql", "订单库", DatasourceType.MYSQL,
                new DatasourceEndpoint("db.example.com", 3306, "sales", true),
                new DatasourceCredential("readonly", "not-returned")));
        assertThat(sources.collectMetadata(AUTHORIZATION, tenantId, datasource.id()).objects()).hasSize(1);

        InMemorySemanticMapper semantics = new InMemorySemanticMapper();
        DatasetService datasets = new DatasetService(semantics, access,
                new SemanticDefinitionManager(semantics, json), mock(DataPolicyService.class),
                new DatasetPreviewManager(), com.aibi.platform.source.DatasourceTestAccess.authorized());
        var dataset = datasets.create(AUTHORIZATION, tenantId, new CreateDatasetRequest(
                WORKSPACE_ID, "sales_orders", "销售订单", 1001L,
                List.of(field("order_status", "STRING", "DIMENSION"),
                        field("paid_minor_units", "LONG", "MEASURE"),
                        field("refunded_minor_units", "LONG", "MEASURE"),
                        field("business_time", "DATETIME", "DIMENSION"),
                        field("currency", "STRING", "DIMENSION"),
                        field("region", "STRING", "DIMENSION")),
                List.of(), List.of()));
        var datasetVersion = datasets.publish(AUTHORIZATION, tenantId, dataset.dataset().id());

        assertSuccessfulSync(tenantId, datasource.id(), dataset.dataset().id());

        MetricService metrics = new MetricService(semantics, access,
                new SemanticDefinitionManager(semantics, json),
                new MetricExpressionValidator(semantics));
        var metric = metrics.create(AUTHORIZATION, tenantId, new CreateMetricRequest(
                WORKSPACE_ID, "net_sales", "净销售额", "CNY", datasetVersion.id(),
                json.readTree("""
                        {"op":"DIV","args":[{"op":"SUM","args":[
                          {"op":"SUB","args":[{"op":"FIELD","field":"paid_minor_units"},
                          {"op":"FIELD","field":"refunded_minor_units"}]}]},
                          {"op":"LITERAL","value":100}]}
                        """),
                json.readTree("""
                        {"timezone":"Asia/Shanghai","eventTimeField":"business_time",
                         "startInclusive":true,"endExclusive":true}
                        """),
                json.readTree("{\"mode\":\"SINGLE\",\"currencyField\":\"currency\"}"),
                List.of("region", "business_time"), 2, List.of()));
        var metricVersion = metrics.publish(AUTHORIZATION, tenantId, metric.metric().id());

        QueryService queries = mock(QueryService.class);
        QueryPlan plan = new QueryPlan(datasetVersion.id(), List.of(metricVersion.id()),
                List.of(), null, List.of(), List.of(), 100);
        when(queries.validate(AUTHORIZATION, tenantId, plan)).thenReturn(
                new ValidationData(true, "p0-plan", datasetVersion.id(),
                        List.of(metricVersion.id()), List.of()));
        InMemoryDashboardMapper visualization = new InMemoryDashboardMapper();
        ChartService charts = new ChartService(visualization, semantics, access,
                new ChartSpecValidator(), queries);
        var chart = charts.create(AUTHORIZATION, tenantId, new CreateChartRequest(
                WORKSPACE_ID, "net_sales_kpi", "净销售额", datasetVersion.id(), plan,
                json.readTree("{\"type\":\"KPI\",\"valueFields\":[\"metric_"
                        + metricVersion.id() + "\"]}")));
        var chartVersion = charts.publish(AUTHORIZATION, tenantId, chart.chart().id());
        DashboardService dashboards = new DashboardService(visualization, access,
                new DashboardDefinitionValidator(), queries);
        var dashboard = dashboards.create(AUTHORIZATION, tenantId, new CreateDashboardRequest(
                WORKSPACE_ID, "sales_overview", "销售总览", json.readTree("{\"columns\":12}"),
                json.readTree("[]"), List.of(new WidgetInput("net-sales", chartVersion.id(),
                        json.readTree("{\"x\":0,\"y\":0,\"w\":4,\"h\":3}")))));
        var publishedDashboard = dashboards.publish(
                AUTHORIZATION, tenantId, dashboard.dashboard().id());

        assertThat(publishedDashboard.status()).isEqualTo("PUBLISHED");
        assertThat(aiCatalog(tenantId, semantics).metrics())
                .singleElement().satisfies(value -> {
                    assertThat(value.code()).isEqualTo("net_sales");
                    assertThat(value.metricVersionId()).isEqualTo(metricVersion.id());
                });
    }

    private DatasourceServiceImpl datasourceService() {
        SourceConnector connector = new SourceConnector() {
            @Override
            public DatasourceType type() {
                return DatasourceType.MYSQL;
            }

            @Override
            public ConnectorResult testConnection(DatasourceEndpoint endpoint,
                    DatasourceCredential credential) {
                return new ConnectorResult(2, "8.4-test");
            }

            @Override
            public List<SourceObject> fetchMetadata(long datasourceId,
                    DatasourceEndpoint endpoint, DatasourceCredential credential) {
                return List.of(new SourceObject("sales", "orders", "TABLE", "", "schema-v1",
                        List.of(new SourceColumn("order_id", "order_id", 1,
                                "VARCHAR", false, true, ""))));
            }
        };
        return new DatasourceServiceImpl(new InMemoryDatasourceStore(),
                new EncryptedCredentialStore(json, ""), new ConnectorRegistry(List.of(connector)), null,
                com.aibi.platform.source.DatasourceTestAccess.authorized());
    }

    private void assertSuccessfulSync(long tenantId, long datasourceId, long datasetId)
            throws Exception {
        InMemoryIngestionMapper mapper = new InMemoryIngestionMapper();
        FieldMappingManager mappings = new FieldMappingManager();
        QualityManager quality = new QualityManager(mapper);
        IngestionProperties properties = new IngestionProperties();
        properties.setStorageDir(Path.of("target", "p0-imports"));
        properties.setMaxAttempts(1);
        SourceTableReader reader = (job, lastTime, lastKey, limit) -> new SourcePage(List.of(
                Map.of("order_id", "A001", "amount", "100.00"),
                Map.of("order_id", "A002", "amount", "150.00")), false);
        SyncWorkerManager worker = new SyncWorkerManager(mapper, reader, mappings, quality, properties,
                mock(com.aibi.platform.ingestion.service.IngestionTaskAuthorization.class));
        ImportWorkerManager importWorker = new ImportWorkerManager(properties);
        var resourceAccess = com.aibi.platform.source.DatasourceTestAccess.authorized();
        when(resourceAccess.requireReference(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> new com.aibi.platform.identity.entity.AssetEntity(1, tenantId, WORKSPACE_ID,
                        call.getArgument(2), call.getArgument(3), 8001, "ACTIVE"));
        var sourceStore = mock(com.aibi.platform.source.store.DatasourceStore.class);
        var sourceFixture = mock(com.aibi.platform.source.model.DatasourceRecord.class);
        when(sourceFixture.workspaceId()).thenReturn(WORKSPACE_ID);
        when(sourceFixture.id()).thenReturn(datasourceId);
        when(sourceFixture.status()).thenReturn("ACTIVE");
        when(sourceStore.find(tenantId, datasourceId)).thenReturn(java.util.Optional.of(sourceFixture));
        var semanticStore = mock(com.aibi.platform.semantic.mapper.SemanticMapper.class);
        var datasetFixture = mock(com.aibi.platform.semantic.model.SemanticModels.Dataset.class);
        when(datasetFixture.workspaceId()).thenReturn(WORKSPACE_ID);
        when(datasetFixture.id()).thenReturn(datasetId);
        when(datasetFixture.status()).thenReturn("PUBLISHED");
        when(semanticStore.findDataset(tenantId, datasetId)).thenReturn(java.util.Optional.of(datasetFixture));
        IngestionServiceImpl ingestion = new IngestionServiceImpl(mapper,
                new FileImportManager(properties), mappings, quality, worker, importWorker,
                resourceAccess, semanticStore, mock(com.aibi.platform.ingestion.service.IngestionTaskAuthorization.class), sourceStore);
        try {
            var job = ingestion.createSyncJob(AUTHORIZATION, tenantId, new CreateSyncJobRequest(
                    datasourceId, datasetId, "orders_full", "FULL", "sales", "orders",
                    "order_id", null, "amount", List.of(
                            new FieldMapping("order_id", "order_id", "STRING", null, true),
                            new FieldMapping("amount", "amount", "DECIMAL", null, true)),
                    100, null, "Asia/Shanghai", true));
            var first = ingestion.runSync(AUTHORIZATION, tenantId, job.id(), "p0-first-sync");
            var replay = ingestion.runSync(AUTHORIZATION, tenantId, job.id(), "p0-first-sync");
            assertThat(replay.taskId()).isEqualTo(first.taskId());
            for (int attempt = 0; attempt < 100; attempt++) {
                var run = ingestion.syncRun(AUTHORIZATION, tenantId, first.taskId());
                if ("SUCCESS".equals(run.status())) {
                    assertThat(run.batch().sourceAmount()).isEqualTo("250.00");
                    assertThat(run.batch().targetAmount()).isEqualTo("250.00");
                    return;
                }
                Thread.sleep(10);
            }
            throw new AssertionError("P0 同步任务未在预期时间内成功");
        } finally {
            worker.close();
            importWorker.close();
        }
    }

    private com.aibi.platform.ai.dto.AiDtos.SemanticCatalogData aiCatalog(
            long tenantId, InMemorySemanticMapper semantics) {
        InMemoryAiStore store = new InMemoryAiStore();
        TaskAuthorizationSnapshot snapshot = new TaskAuthorizationSnapshot(
                1, 2, tenantId, MEMBER_ID, 1, 1,
                new com.aibi.platform.identity.dto.AccessCheckRequest(
                        WORKSPACE_ID, "ai:query", null, "execute"));
        Instant now = Instant.now();
        store.insertTask(new Task(9001, tenantId, 9000, MEMBER_ID, "p0-ai", "p0-thread",
                0, "PENDING", 0, 0, null, now.plusSeconds(60), now, null, null), snapshot);
        TaskAuthorizationService authorization = mock(TaskAuthorizationService.class);
        when(authorization.reauthorize(snapshot)).thenReturn(new AccessDecisionData(true, "ALLOW"));
        AccessDecisionService decision = mock(AccessDecisionService.class);
        when(decision.checkForMember(eq(tenantId), eq(MEMBER_ID), any()))
                .thenReturn(new AccessDecisionData(true, "ALLOW"));
        return new AiSemanticToolService(store, authorization, decision, semantics)
                .authorized(9001, null);
    }

    private SemanticAccessManager permittedAccess(long tenantId) {
        SemanticAccessManager access = mock(SemanticAccessManager.class);
        TokenService.TenantClaims claims = new TokenService.TenantClaims(
                1, 2, tenantId, MEMBER_ID, 1, 1, Instant.now().plusSeconds(300));
        when(access.requireWorkspace(AUTHORIZATION, tenantId, WORKSPACE_ID)).thenReturn(claims);
        when(access.requireTenant(AUTHORIZATION, tenantId)).thenReturn(claims);
        when(access.canAccessWorkspace(tenantId, WORKSPACE_ID, MEMBER_ID)).thenReturn(true);
        when(access.hasPermission(any(), eq(tenantId), eq(WORKSPACE_ID), any(), any()))
                .thenReturn(true);
        doNothing().when(access).requirePermission(any(), eq(tenantId), eq(WORKSPACE_ID),
                any(), any());
        return access;
    }

    private FieldInput field(String key, String type, String role) {
        return new FieldInput(key, 1L, key, key, type, role, "INTERNAL");
    }
}
