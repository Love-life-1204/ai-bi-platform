package com.aibi.platform.ingestion;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.aibi.platform.identity.IdentityApiTestSupport;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import com.aibi.platform.ingestion.manager.SyncWorkerManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.aibi.platform.ingestion.manager.SourceTableReader;
import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.identity.mapper.AssetAccessMapper;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncAuthorizationRepairTest extends IdentityApiTestSupport {
    @Autowired private IngestionMapper mapper;
    @MockitoSpyBean private SyncWorkerManager worker;
    @MockitoBean private SourceTableReader reader;
    @Autowired private AssetAccessMapper assets;

    @BeforeEach
    void holdQueueAndIsolateSource() {
        doNothing().when(worker).submit(anyLong(), anyLong());
        when(reader.read(any(), any(), any(), anyInt())).thenReturn(new SourcePage(List.of(Map.of("id", "A")), false));
    }

    @Test
    void revokedQueuedRunNeverReadsSourceOrAdvancesPublication() throws Exception {
        var fixture = queued("queued_sync");
        deny(fixture, "SYNC_JOB", fixture.job(), "run");
        worker.execute(fixture.owner().tenantId(), fixture.run());
        verify(reader, never()).read(any(), any(), any(), anyInt());
        assertRevoked(fixture);
    }

    @Test
    void revokeDuringSourceReadStopsBeforeRowsAndPublication() throws Exception {
        var fixture = queued("reading_sync");
        when(reader.read(any(), any(), any(), anyInt())).thenAnswer(call -> {
            deny(fixture, "DATASOURCE", fixture.source(), "read");
            return new SourcePage(List.of(Map.of("id", "A")), false);
        });
        worker.execute(fixture.owner().tenantId(), fixture.run());
        verify(reader, times(1)).read(any(), any(), any(), anyInt());
        assertRevoked(fixture);
        var run = mapper.findRun(fixture.owner().tenantId(), fixture.run()).orElseThrow();
        assertEquals(0, mapper.listBatchRows(fixture.owner().tenantId(), run.batchId()).size());
    }

    @Test
    void disabledDatasourceStopsQueuedRunWithoutRequiringPermissionVersionChange() throws Exception {
        var fixture = queued("disabled_sync");
        request(post("/api/datasources/{id}/deactivate", fixture.source()).param("lockVersion", "0")
                .header("X-Tenant-Id", fixture.owner().tenantId()), null, fixture.owner().tenantToken(), status().isOk());
        worker.execute(fixture.owner().tenantId(), fixture.run());
        verify(reader, never()).read(any(), any(), any(), anyInt());
        assertRevoked(fixture);
    }

    @Test
    void authorizedRunPublishesAndExactIdempotentReplayDoesNotCreateAnotherRun() throws Exception {
        var fixture = queued("positive_sync");
        worker.execute(fixture.owner().tenantId(), fixture.run());
        assertEquals("SUCCESS", mapper.findRun(fixture.owner().tenantId(), fixture.run()).orElseThrow().status());
        assertNotNull(mapper.publication(fixture.owner().tenantId(), fixture.dataset()).activeGeneration());
        long replay = request(post("/api/sync-jobs/{id}/run", fixture.job()).header("X-Tenant-Id", fixture.owner().tenantId())
                .header("Idempotency-Key", "authorized-run"), null, fixture.owner().tenantToken(), status().isAccepted()).path("taskId").asLong();
        assertEquals(fixture.run(), replay);
        assertEquals(1, mapper.listRuns(fixture.owner().tenantId(), fixture.job()).size());
    }

    private record Queued(TenantFixture owner, long source, long dataset, long job, long run) { }

    private Queued queued(String prefix) throws Exception {
        var owner = tenantFixture(prefix);
        long workspace = workspace(owner, "sync");
        long source = source(owner, workspace);
        long dataset = dataset(owner, workspace);
        long job = request(post("/api/sync-jobs").header("X-Tenant-Id", owner.tenantId()), body(source, dataset),
                owner.tenantToken(), status().isCreated()).path("id").asLong();
        long run = request(post("/api/sync-jobs/{id}/run", job).header("X-Tenant-Id", owner.tenantId())
                .header("Idempotency-Key", "authorized-run"), null, owner.tenantToken(), status().isAccepted()).path("taskId").asLong();
        return new Queued(owner, source, dataset, job, run);
    }

    private void deny(Queued fixture, String type, long id, String action) throws Exception {
        var owner = fixture.owner();
        long asset = assets.findAssetByReference(owner.tenantId(), type, id).orElseThrow().id();
        request(post("/api/tenants/{t}/assets/{a}/acl", owner.tenantId(), asset),
                "{\"principalType\":\"MEMBER\",\"principalId\":"+owner.memberId()+",\"actionCode\":\""+action+"\",\"effect\":\"DENY\"}",
                owner.tenantToken(), status().isNoContent());
    }

    private void assertRevoked(Queued fixture) {
        var run = mapper.findRun(fixture.owner().tenantId(), fixture.run()).orElseThrow();
        assertEquals("FAILED", run.status());
        assertEquals("AUTHORIZATION_REVOKED", run.errorCode());
        assertTrue(run.deadLetter());
        assertNull(mapper.publication(fixture.owner().tenantId(), fixture.dataset()).activeGeneration());
        assertNull(mapper.findSyncJob(fixture.owner().tenantId(), fixture.job()).orElseThrow().watermarkTime());
        assertEquals(1, mapper.listRuns(fixture.owner().tenantId(), fixture.job()).size());
    }

    @Test
    void rolelessMemberCannotCreateSync() throws Exception {
        var owner = tenantFixture("sync_role");
        long workspace = workspace(owner, "sync");
        long source = source(owner, workspace);
        long dataset = dataset(owner, workspace);
        String member = member(owner, workspace);
        request(post("/api/sync-jobs").header("X-Tenant-Id", owner.tenantId()),
                body(source, dataset), member, status().isForbidden());
        assertEquals(0, mapper.listSyncJobs(owner.tenantId()).size());
    }

    @Test
    void rolelessMemberCannotChangeTargetDatasetQualityRules() throws Exception {
        var owner = tenantFixture("quality_role");
        long workspace = workspace(owner, "quality");
        long dataset = dataset(owner, workspace);
        String member = member(owner, workspace);
        request(post("/api/quality-rules").header("X-Tenant-Id", owner.tenantId()),
                "{\"datasetId\":"+dataset+",\"ruleCode\":\"required_id\",\"ruleType\":\"REQUIRED\",\"field\":\"id\",\"severity\":\"BLOCK\"}",
                member, status().isForbidden());
        assertEquals(0, mapper.listQualityRules(owner.tenantId(), dataset).size());
    }

    @Test
    void syncCannotCombineDatasourceAndDatasetFromDifferentWorkspaces() throws Exception {
        var owner = tenantFixture("sync_scope");
        long first = workspace(owner, "first");
        long second = workspace(owner, "second");
        request(post("/api/sync-jobs").header("X-Tenant-Id", owner.tenantId()),
                body(source(owner, first), dataset(owner, second)), owner.tenantToken(), status().isNotFound());
        assertEquals(0, mapper.listSyncJobs(owner.tenantId()).size());
    }

    @Test
    void rolelessMemberCannotListReadOrRunExistingSync() throws Exception {
        var owner = tenantFixture("sync_actions");
        long workspace = workspace(owner, "sync");
        long job = request(post("/api/sync-jobs").header("X-Tenant-Id", owner.tenantId()),
                body(source(owner, workspace), dataset(owner, workspace)), owner.tenantToken(), status().isCreated()).path("id").asLong();
        String member = member(owner, workspace);
        assertEquals(0, request(get("/api/sync-jobs").header("X-Tenant-Id", owner.tenantId()), null, member, status().isOk()).size());
        request(get("/api/sync-jobs/{id}", job).header("X-Tenant-Id", owner.tenantId()), null, member, status().isForbidden());
        request(post("/api/sync-jobs/{id}/run", job).header("X-Tenant-Id", owner.tenantId()).header("Idempotency-Key", "denied-run"), null, member, status().isForbidden());
        assertEquals(0, mapper.listRuns(owner.tenantId(), job).size());
    }

    private long workspace(TenantFixture owner, String code) throws Exception {
        return request(post("/api/tenants/{t}/workspaces", owner.tenantId()),
                "{\"workspaceCode\":\""+code+"\",\"workspaceName\":\"同步空间\"}", owner.tenantToken(), status().isCreated()).path("id").asLong();
    }
    private long dataset(TenantFixture owner, long workspace) throws Exception {
        return request(post("/api/datasets").header("X-Tenant-Id", owner.tenantId()),
                "{\"workspaceId\":"+workspace+",\"code\":\"sync_target\",\"name\":\"目标\",\"fields\":[{\"stableFieldKey\":\"id\",\"physicalName\":\"id\",\"businessName\":\"编号\",\"dataType\":\"String\",\"role\":\"DIMENSION\",\"sensitivity\":\"PUBLIC\"}]}", owner.tenantToken(), status().isCreated()).path("dataset").path("id").asLong();
    }
    private long source(TenantFixture owner, long workspace) throws Exception {
        return request(post("/api/datasources").header("X-Tenant-Id", owner.tenantId()),
                "{\"workspaceId\":"+workspace+",\"code\":\"sync_source\",\"name\":\"源\",\"type\":\"MYSQL\",\"endpoint\":{\"host\":\"db.example.invalid\",\"port\":3306,\"database\":\"sales\",\"tls\":true},\"credential\":{\"username\":\"fake\",\"password\":\"test-only\"}}", owner.tenantToken(), status().isCreated()).path("id").asLong();
    }
    private String member(TenantFixture owner, long workspace) throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        var user=request(post("/api/auth/register"),"{\"email\":\""+email+"\",\"displayName\":\"无角色\",\"password\":\"correct-horse-battery-staple\"}",null,status().isCreated());
        String global=request(post("/api/auth/login"),"{\"email\":\""+email+"\",\"password\":\"correct-horse-battery-staple\"}",null,status().isOk()).path("accessToken").asText();
        long member=request(post("/api/tenants/{t}/members",owner.tenantId()),"{\"userId\":"+user.path("id").asLong()+",\"memberName\":\"无角色\"}",owner.tenantToken(),status().isCreated()).path("id").asLong();
        request(post("/api/tenants/{t}/workspaces/{w}/members",owner.tenantId(),workspace),"{\"memberId\":"+member+",\"memberLockVersion\":0}",owner.tenantToken(),status().isNoContent());
        return request(post("/api/auth/switch-tenant"),"{\"tenantId\":"+owner.tenantId()+"}",global,status().isOk()).path("tenantAccessToken").asText();
    }
    private String body(long source, long dataset) {
        return "{\"datasourceId\":"+source+",\"datasetId\":"+dataset+",\"jobCode\":\"sync_job\",\"syncMode\":\"FULL\",\"sourceSchema\":\"sales\",\"sourceTable\":\"orders\",\"primaryKeyField\":\"id\",\"batchSize\":100,\"timezone\":\"UTC\",\"enabled\":true,\"mappings\":[{\"sourceField\":\"id\",\"targetField\":\"id\",\"targetType\":\"STRING\",\"required\":true}]}";
    }
}
