package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.WorkspaceEntity;
import com.aibi.platform.identity.entity.WorkspaceMemberEntity;
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
public class JdbcWorkspaceMapper implements WorkspaceMapper {

    private static final String COLUMNS = """
            id, tenant_id, workspace_code, workspace_name, workspace_status,
            owner_member_id, created_at, updated_at, lock_version
            """;
    private static final String UPDATE_SQL = """
            UPDATE sys_workspace
               SET workspace_name=:name, workspace_status=:status,
                   updated_at=:updatedAt, lock_version=:lockVersion
             WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedLockVersion
               AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OptimisticLockExecutor optimisticLockExecutor;

    public JdbcWorkspaceMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            OptimisticLockExecutor optimisticLockExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimisticLockExecutor = optimisticLockExecutor;
    }

    @Override
    public WorkspaceEntity insert(WorkspaceEntity workspace) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_workspace
                        (id, tenant_id, workspace_code, workspace_name, workspace_status,
                         owner_member_id, created_at, updated_at, lock_version)
                    VALUES
                        (:id, :tenantId, :code, :name, :status, :ownerMemberId,
                         :createdAt, :updatedAt, :lockVersion)
                    """, parameters(workspace));
            return workspace;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("工作空间编码已存在");
        }
    }

    @Override
    public Optional<WorkspaceEntity> findById(long tenantId, long workspaceId) {
        return query("SELECT " + COLUMNS + " FROM sys_workspace"
                        + " WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL",
                Map.of("tenantId", tenantId, "id", workspaceId)).stream().findFirst();
    }

    @Override
    public List<WorkspaceEntity> findByTenantId(long tenantId) {
        return query("SELECT " + COLUMNS + " FROM sys_workspace"
                        + " WHERE tenant_id=:tenantId AND deleted_at IS NULL ORDER BY workspace_code",
                Map.of("tenantId", tenantId));
    }

    @Override
    public WorkspaceEntity update(WorkspaceEntity workspace, long expectedLockVersion) {
        Map<String, Object> parameters = new HashMap<>(parameters(workspace));
        parameters.put("expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_SQL, parameters);
        return workspace;
    }

    @Override
    public WorkspaceMemberEntity addMember(WorkspaceMemberEntity member) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_workspace_member
                        (id, tenant_id, workspace_id, member_id, member_status)
                    VALUES (:id, :tenantId, :workspaceId, :memberId, :status)
                    """, Map.of(
                    "id", member.id(), "tenantId", member.tenantId(),
                    "workspaceId", member.workspaceId(), "memberId", member.memberId(),
                    "status", member.memberStatus()));
            return member;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("成员已获得工作空间授权");
        }
    }

    @Override
    public boolean removeMember(long tenantId, long workspaceId, long memberId) {
        return jdbcTemplate.update("""
                UPDATE sys_workspace_member
                   SET deleted_at=UTC_TIMESTAMP(3), member_status='REMOVED',
                       updated_at=UTC_TIMESTAMP(3), lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND workspace_id=:workspaceId AND member_id=:memberId
                   AND deleted_at IS NULL
                """, Map.of(
                "tenantId", tenantId, "workspaceId", workspaceId, "memberId", memberId)) == 1;
    }

    @Override
    public boolean isActiveMember(long tenantId, long workspaceId, long memberId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_workspace_member
                 WHERE tenant_id=:tenantId AND workspace_id=:workspaceId AND member_id=:memberId
                   AND member_status='ACTIVE' AND deleted_at IS NULL
                """, Map.of(
                "tenantId", tenantId, "workspaceId", workspaceId, "memberId", memberId), Long.class);
        return count != null && count == 1;
    }

    private Map<String, Object> parameters(WorkspaceEntity workspace) {
        return Map.ofEntries(
                Map.entry("id", workspace.id()), Map.entry("tenantId", workspace.tenantId()),
                Map.entry("code", workspace.workspaceCode()), Map.entry("name", workspace.workspaceName()),
                Map.entry("status", workspace.workspaceStatus()),
                Map.entry("ownerMemberId", workspace.ownerMemberId()),
                Map.entry("createdAt", Timestamp.from(workspace.createdAt())),
                Map.entry("updatedAt", Timestamp.from(workspace.updatedAt())),
                Map.entry("lockVersion", workspace.lockVersion()));
    }

    private List<WorkspaceEntity> query(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapWorkspace);
    }

    private WorkspaceEntity mapWorkspace(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkspaceEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getString("workspace_code"), resultSet.getString("workspace_name"),
                resultSet.getString("workspace_status"), resultSet.getLong("owner_member_id"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("lock_version"));
    }
}
