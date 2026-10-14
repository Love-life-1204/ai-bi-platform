package com.aibi.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.common.dto.ChangeTenantStatusRequest;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.UserEntity;
import com.aibi.platform.identity.mapper.AccessControlMapper;
import com.aibi.platform.identity.mapper.JdbcAccessControlMapper;
import com.aibi.platform.identity.mapper.JdbcMemberMapper;
import com.aibi.platform.identity.mapper.JdbcSessionMapper;
import com.aibi.platform.identity.mapper.JdbcUserMapper;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.identity.service.SessionService;
import com.aibi.platform.identity.service.TenantActionAuthorizer;
import com.aibi.platform.manager.TenantManager;
import com.aibi.platform.mapper.JdbcTenantMapper;
import com.aibi.platform.service.TenantService;
import com.aibi.platform.service.impl.TenantServiceImpl;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 必须由隔离库启动器显式执行；缺少隔离配置即失败，不连接默认业务库。 */
class TenantOnboardingJdbcIT {

    @Test
    void ownerInsertFailureRollsBackTenantAndRetrySucceeds() {
        try (var context = new AnnotationConfigApplicationContext(JdbcConfig.class)) {
            var service = context.getBean(TenantService.class);
            var sessions = context.getBean(SessionService.class);
            var users = context.getBean(UserMapper.class);
            var members = context.getBean(MemberMapper.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            String auth = login(users, sessions);
            String code = "rollback_" + UUID.randomUUID().toString().replace("-", "");
            doThrow(new IllegalStateException("injected owner insert failure")).doCallRealMethod()
                    .when(members).insert(any());
            assertThrows(IllegalStateException.class,
                    () -> service.createTenant(auth, new CreateTenantRequest(code, "事务回滚", "UTC", "CNY")));
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_tenant WHERE tenant_code=?", Long.class, code));
            var tenant = service.createTenant(auth, new CreateTenantRequest(code, "事务回滚", "UTC", "CNY"));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_tenant_member WHERE tenant_id=? AND is_owner=1", Long.class, tenant.id()));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_member_role WHERE tenant_id=?", Long.class, tenant.id()));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action_code='TENANT_CREATED'", Long.class, tenant.id()));
        }
    }

    @Test
    void freezeAndRecoveryPersistVersionsWithoutRevivingOldToken() {
        try (var context = new AnnotationConfigApplicationContext(JdbcConfig.class)) {
            var service = context.getBean(TenantService.class);
            var sessions = context.getBean(SessionService.class);
            var users = context.getBean(UserMapper.class);
            var permissions = context.getBean(PermissionContextService.class);
            String auth = login(users, sessions);
            var tenant = service.createTenant(auth, new CreateTenantRequest(
                    "freeze_" + UUID.randomUUID().toString().replace("-", ""), "真实JDBC", "UTC", "CNY"));
            var switched = permissions.switchTenant(auth, new com.aibi.platform.identity.dto.SwitchTenantRequest(tenant.id()));
            String old = "Bearer " + switched.tenantAccessToken();
            assertEquals(2, service.freezeTenant(old, tenant.id(), new ChangeTenantStatusRequest(0)).permissionVersion());
            assertThrows(ForbiddenException.class, () -> service.getTenant(old, tenant.id()));
            assertEquals(3, service.unfreezeTenant(auth, tenant.id(), new ChangeTenantStatusRequest(1)).permissionVersion());
            assertThrows(ForbiddenException.class, () -> service.getTenant(old, tenant.id()));
            assertTrue(permissions.switchTenant(auth, new com.aibi.platform.identity.dto.SwitchTenantRequest(tenant.id()))
                    .tenantPermissionVersion() > switched.tenantPermissionVersion());
        }
    }

    private String login(UserMapper users, SessionService sessions) {
        Instant now = Instant.now();
        long id = new java.security.SecureRandom().nextLong(1, Long.MAX_VALUE);
        var user = users.insert(new UserEntity(id, UUID.randomUUID()+"@example.invalid", "隔离所有者",
                "not-a-production-login-hash", "ACTIVE", null, now, now, 0));
        return "Bearer " + sessions.createSession(user, null).accessToken();
    }

    @Test
    void concurrentSameKeyCreatesExactlyOneTenantAndOwner() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(JdbcConfig.class)) {
            var service = context.getBean(TenantService.class);
            String auth = login(context.getBean(UserMapper.class), context.getBean(SessionService.class));
            String code = "concurrent_" + UUID.randomUUID().toString().replace("-", "");
            String key = "same-key-" + UUID.randomUUID();
            var request = new CreateTenantRequest(code, "并发入驻", "UTC", "CNY");
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> service.createTenant(auth, request, key));
                var second = pool.submit(() -> service.createTenant(auth, request, key));
                assertEquals(first.get().id(), second.get().id());
            } finally {
                pool.shutdownNow();
            }
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_tenant WHERE tenant_code=?", Long.class, code));
            long id = service.createTenant(auth, request, key).id();
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_tenant_member WHERE tenant_id=? AND is_owner=1", Long.class, id));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_event WHERE tenant_id=? AND action_code='TENANT_CREATED'", Long.class, id));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class JdbcConfig {

        @Bean
        DataSource source() {
            String url = System.getenv("AIBI_REPAIR_JDBC_URL");
            if (url == null || !url.matches("jdbc:mysql://[^/]+/aibi_repair_[a-f0-9]{16}\\?.+")) {
                throw new IllegalStateException("Explicit isolated JDBC URL required");
            }
            var data = new DriverManagerDataSource(url, System.getenv("AIBI_REPAIR_JDBC_USER"),
                    System.getenv("AIBI_REPAIR_JDBC_PASSWORD"));
            String database = new JdbcTemplate(data).queryForObject("SELECT DATABASE()", String.class);
            if (database == null || !database.matches("aibi_repair_[a-f0-9]{16}")) {
                throw new IllegalStateException("Refusing business database");
            }
            return data;
        }

        @Bean(name = "mysqlTransactionManager")
        PlatformTransactionManager transactions(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        NamedParameterJdbcTemplate jdbc(DataSource source) {
            return new NamedParameterJdbcTemplate(source);
        }

        @Bean
        OptimisticLockExecutor locks(NamedParameterJdbcTemplate jdbc) {
            return new OptimisticLockExecutor(jdbc);
        }

        @Bean
        TenantManager manager(NamedParameterJdbcTemplate jdbc, OptimisticLockExecutor locks) {
            return new TenantManager(new JdbcTenantMapper(jdbc, locks));
        }

        @Bean
        MemberMapper members(NamedParameterJdbcTemplate jdbc, OptimisticLockExecutor locks) {
            return spy(new JdbcMemberMapper(jdbc, locks));
        }

        @Bean
        UserMapper users(NamedParameterJdbcTemplate jdbc, OptimisticLockExecutor locks) {
            return new JdbcUserMapper(jdbc, locks);
        }

        @Bean
        AccessControlMapper access(NamedParameterJdbcTemplate jdbc, OptimisticLockExecutor locks) {
            return new JdbcAccessControlMapper(jdbc, locks);
        }

        @Bean
        TokenService tokens() { return new TokenService(); }

        @Bean
        SessionService sessions(NamedParameterJdbcTemplate jdbc, TokenService tokens, UserMapper users) {
            return new SessionService(new JdbcSessionMapper(jdbc), tokens, users);
        }

        @Bean
        PermissionContextService contexts(SessionService sessions, TenantManager manager,
                MemberMapper members, TokenService tokens) {
            return new PermissionContextService(sessions, manager, members, tokens);
        }

        @Bean
        TenantActionAuthorizer actions(PermissionContextService contexts, MemberMapper members,
                AccessControlMapper access) {
            return new TenantActionAuthorizer(contexts, members, access);
        }

        @Bean
        TenantService tenantService(TenantManager manager, SessionService sessions,
                PermissionContextService contexts, TenantActionAuthorizer actions,
                MemberMapper members, UserMapper users, AccessControlMapper access,
                NamedParameterJdbcTemplate jdbc) {
            return new TenantServiceImpl(manager, sessions, contexts, actions, members, users, access,
                    new com.aibi.platform.identity.service.SecurityAudit(jdbc));
        }
    }
}
