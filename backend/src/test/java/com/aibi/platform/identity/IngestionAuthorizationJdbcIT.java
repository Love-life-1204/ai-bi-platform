package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.entity.AssetEntity;
import com.aibi.platform.identity.entity.UserEntity;
import com.aibi.platform.identity.mapper.AssetAccessMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.service.ResourceAccessManager;
import com.aibi.platform.identity.service.SessionService;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.aibi.platform.ingestion.service.IngestionTaskAuthorization;
import com.aibi.platform.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Persistence contract against an owned isolated DB; authorization decisions are covered by the API/worker tests. */
class IngestionAuthorizationJdbcIT {
    @Test
    void freshWorkerReloadsExactSnapshotAndWrongTenantOrMissingSnapshotFailClosed() {
        try (var context = new AnnotationConfigApplicationContext(TenantOnboardingJdbcIT.JdbcConfig.class)) {
            Instant now = Instant.now();
            long userId = new java.security.SecureRandom().nextLong(1, Long.MAX_VALUE);
            var user = context.getBean(UserMapper.class).insert(new UserEntity(userId, UUID.randomUUID()+"@example.invalid",
                    "授权快照夹具", "test-only", "ACTIVE", null, now, now, 0));
            String auth = "Bearer " + context.getBean(SessionService.class).createSession(user, null).accessToken();
            long tenant = context.getBean(TenantService.class).createTenant(auth,
                    new CreateTenantRequest("snapshot_"+UUID.randomUUID().toString().replace("-", ""), "隔离快照", "UTC", "CNY")).id();
            long member = context.getBean(MemberMapper.class).findByTenantAndUser(tenant, user.id()).orElseThrow().id();
            long id = 9223372036854775707L;
            var snapshot = new TaskAuthorizationSnapshot(9007199254740993L, userId, tenant, member, 1, 1,
                    new AccessCheckRequest(7001L, "import:run", 7002L, "run"));
            var tasks = mock(TaskAuthorizationService.class);
            when(tasks.capture(anyString(), eq(tenant), any())).thenReturn(snapshot);
            var assets = mock(AssetAccessMapper.class);
            when(assets.findAssetByReference(tenant, "IMPORT_JOB", id)).thenReturn(Optional.of(new AssetEntity(
                    7002, tenant, 7001, "IMPORT_JOB", id, member, "ACTIVE")));
            var provider = context.getBeanProvider(NamedParameterJdbcTemplate.class);
            var access = mock(ResourceAccessManager.class);
            var sources = mock(com.aibi.platform.source.store.DatasourceStore.class);
            var semantics = mock(com.aibi.platform.semantic.mapper.SemanticMapper.class);
            var ingestion = mock(com.aibi.platform.ingestion.mapper.IngestionMapper.class);
            var first = new IngestionTaskAuthorization(provider, new ObjectMapper(), tasks, access, assets, sources, semantics, ingestion);
            first.capture("Bearer ephemeral-fixture-only", tenant, "IMPORT_JOB", id, 7001, "import:run", List.of());
            var fresh = new IngestionTaskAuthorization(provider, new ObjectMapper(), tasks, access, assets, sources, semantics, ingestion);
            fresh.require(tenant, "IMPORT_JOB", id);
            verify(tasks).reauthorize(snapshot);
            assertThrows(ForbiddenException.class, () -> fresh.require(tenant+1, "IMPORT_JOB", id));
            assertThrows(ForbiddenException.class, () -> fresh.require(tenant, "SYNC_RUN", id));
            String stored = new JdbcTemplate(context.getBean(DataSource.class)).queryForObject(
                    "SELECT snapshot_json FROM bi_ingestion_auth_snapshot WHERE tenant_id=? AND task_type='IMPORT_JOB' AND task_id=?", String.class, tenant, id);
            assertFalse(stored.contains("Bearer"));
            assertFalse(stored.contains("ephemeral-fixture-only"));
            assertTrue(stored.contains("9007199254740993"));
        }
    }
}
