package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.UserEntity;
import com.aibi.platform.identity.entity.WorkspaceEntity;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.JdbcWorkspaceMapper;
import com.aibi.platform.identity.service.SessionService;
import com.aibi.platform.ingestion.entity.FileEntity;
import com.aibi.platform.ingestion.manager.IngestionProperties;
import com.aibi.platform.ingestion.mapper.JdbcIngestionMapper;
import com.aibi.platform.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Explicit isolated runner only; tests the real SQL column mapping, without ClickHouse writes. */
class FileScopeJdbcIT {
    @Test
    void scopeAndOwnerSurviveRealJdbcRoundTripAndForeignTenantCannotRead() {
        try (var context = new AnnotationConfigApplicationContext(TenantOnboardingJdbcIT.JdbcConfig.class)) {
            var data = context.getBean(DataSource.class);
            var jdbc = new NamedParameterJdbcTemplate(data);
            var now = Instant.now();
            var random = new java.security.SecureRandom();
            var user = context.getBean(UserMapper.class).insert(new UserEntity(random.nextLong(1, Long.MAX_VALUE),
                    UUID.randomUUID()+"@example.invalid", "JDBC文件夹具", "test-only", "ACTIVE", null, now, now, 0));
            String auth = "Bearer " + context.getBean(SessionService.class).createSession(user, null).accessToken();
            long tenant = context.getBean(TenantService.class).createTenant(auth,
                    new CreateTenantRequest("file_"+UUID.randomUUID().toString().replace("-", ""), "隔离文件", "UTC", "CNY")).id();
            long member = context.getBean(MemberMapper.class).findByTenantAndUser(tenant, user.id()).orElseThrow().id();
            var workspaces = new JdbcWorkspaceMapper(jdbc, new OptimisticLockExecutor(jdbc));
            long workspace = random.nextLong(1, Long.MAX_VALUE);
            workspaces.insert(new WorkspaceEntity(workspace, tenant, "scope", "文件范围", "ACTIVE", member, now, now, 0));
            workspaces.addMember(new com.aibi.platform.identity.entity.WorkspaceMemberEntity(
                    random.nextLong(1, Long.MAX_VALUE), tenant, workspace, member, "ACTIVE"));
            var properties = new IngestionProperties();
            properties.setStorageDir(java.nio.file.Path.of("target", "jdbc-file-scope"));
            var mapper = new JdbcIngestionMapper(jdbc, new JdbcTemplate(), new DataSourceTransactionManager(data), new ObjectMapper(), properties);
            long id = random.nextLong(1, Long.MAX_VALUE);
            var file = new FileEntity(id, tenant, "scope.csv", "text/csv", 4, "0".repeat(64),
                    properties.getStorageDir().toAbsolutePath().resolve(Long.toString(tenant)).resolve(id+".csv"), "SAFE", now, workspace, member);
            mapper.insertFile(file);
            var actual = mapper.findFile(tenant, id).orElseThrow();
            assertEquals(workspace, actual.workspaceId());
            assertEquals(member, actual.ownerMemberId());
            assertEquals(file.storagePath(), actual.storagePath());
            assertTrue(mapper.findFile(tenant+1, id).isEmpty());
        }
    }
}
