package com.aibi.platform.mapper;

import com.aibi.platform.common.exception.TenantCodeAlreadyExistsException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.entity.TenantEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcTenantMapper implements TenantMapper {

    private static final String EXISTS_SQL = """
            SELECT COUNT(*)
              FROM sys_tenant
             WHERE tenant_code = :tenantCode
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_tenant
                (id, tenant_code, tenant_name, tenant_status, timezone, default_currency,
                 permission_version, created_at, updated_at, lock_version)
            VALUES
                (:id, :tenantCode, :tenantName, :tenantStatus, :timezone, :defaultCurrency,
                 :permissionVersion, :createdAt, :createdAt, :lockVersion)
            """;
    private static final String SELECT_SQL = """
            SELECT id, tenant_code, tenant_name, tenant_status, timezone, default_currency,
                   permission_version, created_at, updated_at, lock_version
              FROM sys_tenant
             WHERE id = :tenantId
               AND deleted_at IS NULL
            """;
    private static final String UPDATE_BASIC_INFO_SQL = """
            UPDATE sys_tenant
               SET tenant_name = :tenantName,
                   timezone = :timezone,
                   default_currency = :defaultCurrency,
                   updated_at = :updatedAt,
                   lock_version = :lockVersion
             WHERE id = :id
               AND lock_version = :expectedLockVersion
               AND deleted_at IS NULL
            """;
    private static final String UPDATE_STATUS_SQL = """
            UPDATE sys_tenant
               SET tenant_status = :tenantStatus,
                   permission_version = :permissionVersion,
                   updated_at = :updatedAt,
                   lock_version = :lockVersion
             WHERE id = :id
               AND lock_version = :expectedLockVersion
               AND deleted_at IS NULL
            """;
    private static final String INCREMENT_PERMISSION_VERSION_SQL = """
            UPDATE sys_tenant
               SET permission_version = permission_version + 1,
                   updated_at = UTC_TIMESTAMP(3)
             WHERE id = :tenantId
               AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OptimisticLockExecutor optimisticLockExecutor;

    public JdbcTenantMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            OptimisticLockExecutor optimisticLockExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimisticLockExecutor = optimisticLockExecutor;
    }

    @Override
    public boolean existsByTenantCode(String tenantCode) {
        Long count = jdbcTemplate.queryForObject(EXISTS_SQL, Map.of("tenantCode", tenantCode), Long.class);
        return count != null && count > 0;
    }

    @Override
    public TenantEntity insert(TenantEntity tenant) {
        Map<String, Object> parameters = Map.of(
                "id", tenant.id(),
                "tenantCode", tenant.tenantCode(),
                "tenantName", tenant.tenantName(),
                "tenantStatus", tenant.tenantStatus(),
                "timezone", tenant.timezone(),
                "defaultCurrency", tenant.defaultCurrency(),
                "permissionVersion", tenant.permissionVersion(),
                "createdAt", Timestamp.from(tenant.createdAt()),
                "lockVersion", tenant.lockVersion());
        try {
            jdbcTemplate.update(INSERT_SQL, parameters);
            return tenant;
        } catch (DuplicateKeyException exception) {
            if (existsByTenantCode(tenant.tenantCode())) {
                // 并发创建以数据库唯一索引为最终裁决，不能只依赖创建前查询。
                throw new TenantCodeAlreadyExistsException();
            }
            throw exception;
        }
    }

    @Override
    public Optional<TenantEntity> findById(long tenantId) {
        List<TenantEntity> tenants = jdbcTemplate.query(
                SELECT_SQL,
                Map.of("tenantId", tenantId),
                this::mapTenant);
        return tenants.stream().findFirst();
    }

    @Override
    public Optional<TenantEntity> findByIdForUpdate(long tenantId) {
        return jdbcTemplate.query(SELECT_SQL + " FOR UPDATE", Map.of("tenantId", tenantId), this::mapTenant)
                .stream().findFirst();
    }

    @Override
    public TenantEntity updateBasicInfo(TenantEntity tenant, long expectedLockVersion) {
        Map<String, Object> parameters = Map.of(
                "id", tenant.id(),
                "tenantName", tenant.tenantName(),
                "timezone", tenant.timezone(),
                "defaultCurrency", tenant.defaultCurrency(),
                "updatedAt", Timestamp.from(tenant.updatedAt()),
                "lockVersion", tenant.lockVersion(),
                "expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_BASIC_INFO_SQL, parameters);
        return tenant;
    }

    @Override
    public TenantEntity updateStatus(TenantEntity tenant, long expectedLockVersion) {
        Map<String, Object> parameters = Map.of(
                "id", tenant.id(),
                "tenantStatus", tenant.tenantStatus(),
                "permissionVersion", tenant.permissionVersion(),
                "updatedAt", Timestamp.from(tenant.updatedAt()),
                "lockVersion", tenant.lockVersion(),
                "expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_STATUS_SQL, parameters);
        return tenant;
    }

    @Override
    public TenantEntity incrementPermissionVersion(long tenantId) {
        optimisticLockExecutor.updateExactlyOne(
                INCREMENT_PERMISSION_VERSION_SQL, Map.of("tenantId", tenantId));
        return findById(tenantId).orElseThrow();
    }

    private TenantEntity mapTenant(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TenantEntity(
                resultSet.getLong("id"),
                resultSet.getString("tenant_code"),
                resultSet.getString("tenant_name"),
                resultSet.getString("tenant_status"),
                resultSet.getString("timezone"),
                resultSet.getString("default_currency"),
                resultSet.getLong("permission_version"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("lock_version"));
    }
}
