package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.PermissionEntity;
import com.aibi.platform.identity.entity.RoleEntity;
import com.aibi.platform.identity.entity.RolePermissionEntity;
import com.aibi.platform.identity.entity.MemberRoleEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
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
public class JdbcAccessControlMapper implements AccessControlMapper {

    private static final String PERMISSION_COLUMNS = """
            id, permission_code, permission_name, resource_type, action_code, lock_version
            """;
    private static final String ROLE_COLUMNS = """
            id, tenant_id, role_code, role_name, role_status, is_builtin, lock_version
            """;
    private static final String UPDATE_ROLE_SQL = """
            UPDATE sys_role SET role_name=:roleName, role_status=:roleStatus,
                   lock_version=:lockVersion, updated_at=UTC_TIMESTAMP(3)
             WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedLockVersion
               AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OptimisticLockExecutor optimisticLockExecutor;

    public JdbcAccessControlMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            OptimisticLockExecutor optimisticLockExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimisticLockExecutor = optimisticLockExecutor;
    }

    @Override
    public PermissionEntity insertPermission(PermissionEntity permission) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_permission
                        (id, permission_code, permission_name, resource_type, action_code, lock_version)
                    VALUES (:id, :code, :name, :resourceType, :actionCode, :lockVersion)
                    """, Map.of(
                    "id", permission.id(), "code", permission.permissionCode(),
                    "name", permission.permissionName(), "resourceType", permission.resourceType(),
                    "actionCode", permission.actionCode(), "lockVersion", permission.lockVersion()));
            return permission;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("权限编码已存在");
        }
    }

    @Override
    public Optional<PermissionEntity> findPermission(long permissionId) {
        return queryPermissions("SELECT " + PERMISSION_COLUMNS + " FROM sys_permission"
                + " WHERE id=:id AND deleted_at IS NULL", Map.of("id", permissionId)).stream().findFirst();
    }

    @Override
    public Optional<PermissionEntity> findPermissionByCode(String permissionCode) {
        return queryPermissions("SELECT " + PERMISSION_COLUMNS + " FROM sys_permission"
                        + " WHERE permission_code=:code AND deleted_at IS NULL",
                Map.of("code", permissionCode)).stream().findFirst();
    }

    @Override
    public List<PermissionEntity> findPermissions() {
        return queryPermissions("SELECT " + PERMISSION_COLUMNS
                + " FROM sys_permission WHERE deleted_at IS NULL ORDER BY permission_code", Map.of());
    }

    @Override
    public RoleEntity insertRole(RoleEntity role) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_role
                        (id, tenant_id, role_code, role_name, role_status, is_builtin, lock_version)
                    VALUES (:id, :tenantId, :roleCode, :roleName, :roleStatus, :builtin, :lockVersion)
                    """, roleParameters(role));
            return role;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("角色编码已存在");
        }
    }

    @Override
    public Optional<RoleEntity> findRole(long tenantId, long roleId) {
        return queryRoles("SELECT " + ROLE_COLUMNS + " FROM sys_role"
                        + " WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL",
                Map.of("tenantId", tenantId, "id", roleId)).stream().findFirst();
    }

    @Override
    public List<RoleEntity> findRoles(long tenantId) {
        return queryRoles("SELECT " + ROLE_COLUMNS + " FROM sys_role"
                        + " WHERE tenant_id=:tenantId AND deleted_at IS NULL ORDER BY role_code",
                Map.of("tenantId", tenantId));
    }

    @Override
    public RoleEntity updateRole(RoleEntity role, long expectedLockVersion) {
        Map<String, Object> parameters = new HashMap<>(roleParameters(role));
        parameters.put("expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_ROLE_SQL, parameters);
        return role;
    }

    @Override
    public RolePermissionEntity setRolePermission(RolePermissionEntity value) {
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission
                    (id, tenant_id, role_id, permission_id, effect)
                VALUES (:id, :tenantId, :roleId, :permissionId, :effect)
                ON DUPLICATE KEY UPDATE effect=:effect, updated_at=UTC_TIMESTAMP(3),
                                        lock_version=lock_version+1
                """, Map.of(
                "id", value.id(), "tenantId", value.tenantId(), "roleId", value.roleId(),
                "permissionId", value.permissionId(), "effect", value.effect()));
        return value;
    }

    @Override
    public List<RolePermissionEntity> findRolePermissions(long tenantId, long roleId) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, role_id, permission_id, effect
                  FROM sys_role_permission
                 WHERE tenant_id=:tenantId AND role_id=:roleId AND deleted_at IS NULL
                 ORDER BY permission_id
                """, Map.of("tenantId", tenantId, "roleId", roleId), this::mapRolePermission);
    }

    @Override
    public MemberRoleEntity insertMemberRole(MemberRoleEntity value) {
        try {
            org.springframework.jdbc.core.namedparam.MapSqlParameterSource parameters =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("id", value.id()).addValue("tenantId", value.tenantId())
                            .addValue("memberId", value.memberId()).addValue("roleId", value.roleId())
                            .addValue("workspaceId", value.workspaceId()).addValue("scopeKey", value.scopeKey())
                            .addValue("expiresAt", value.expiresAt() == null
                                    ? null : Timestamp.from(value.expiresAt()));
            jdbcTemplate.update("""
                    INSERT INTO sys_member_role
                        (id, tenant_id, member_id, role_id, workspace_id, scope_key, expires_at)
                    VALUES
                        (:id, :tenantId, :memberId, :roleId, :workspaceId, :scopeKey, :expiresAt)
                    """, parameters);
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("成员角色已分配");
        }
    }

    @Override
    public Optional<MemberRoleEntity> findMemberRole(long tenantId, long assignmentId) {
        return queryMemberRoles("""
                SELECT id, tenant_id, member_id, role_id, workspace_id, scope_key, expires_at
                  FROM sys_member_role
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "id", assignmentId)).stream().findFirst();
    }

    @Override
    public List<MemberRoleEntity> findMemberRoles(long tenantId, long memberId) {
        return queryMemberRoles("""
                SELECT id, tenant_id, member_id, role_id, workspace_id, scope_key, expires_at
                  FROM sys_member_role
                 WHERE tenant_id=:tenantId AND member_id=:memberId AND deleted_at IS NULL
                 ORDER BY id
                """, Map.of("tenantId", tenantId, "memberId", memberId));
    }

    @Override
    public boolean revokeMemberRole(long tenantId, long assignmentId) {
        return jdbcTemplate.update("""
                UPDATE sys_member_role SET deleted_at=UTC_TIMESTAMP(3), updated_at=UTC_TIMESTAMP(3),
                       lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "id", assignmentId)) == 1;
    }

    private List<PermissionEntity> queryPermissions(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapPermission);
    }

    private List<RoleEntity> queryRoles(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapRole);
    }

    private List<MemberRoleEntity> queryMemberRoles(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapMemberRole);
    }

    private Map<String, Object> roleParameters(RoleEntity role) {
        return Map.of(
                "id", role.id(), "tenantId", role.tenantId(), "roleCode", role.roleCode(),
                "roleName", role.roleName(), "roleStatus", role.roleStatus(),
                "builtin", role.builtin(), "lockVersion", role.lockVersion());
    }

    private PermissionEntity mapPermission(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PermissionEntity(
                resultSet.getLong("id"), resultSet.getString("permission_code"),
                resultSet.getString("permission_name"), resultSet.getString("resource_type"),
                resultSet.getString("action_code"), resultSet.getLong("lock_version"));
    }

    private RoleEntity mapRole(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RoleEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getString("role_code"), resultSet.getString("role_name"),
                resultSet.getString("role_status"), resultSet.getBoolean("is_builtin"),
                resultSet.getLong("lock_version"));
    }

    private RolePermissionEntity mapRolePermission(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RolePermissionEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("role_id"), resultSet.getLong("permission_id"),
                resultSet.getString("effect"));
    }

    private MemberRoleEntity mapMemberRole(ResultSet resultSet, int rowNumber) throws SQLException {
        long workspaceId = resultSet.getLong("workspace_id");
        Long workspace = resultSet.wasNull() ? null : workspaceId;
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new MemberRoleEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("member_id"), resultSet.getLong("role_id"),
                workspace, resultSet.getString("scope_key"),
                expiresAt == null ? null : expiresAt.toInstant());
    }
}
