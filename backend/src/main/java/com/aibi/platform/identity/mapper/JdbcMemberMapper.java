package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.MemberEntity;
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
public class JdbcMemberMapper implements MemberMapper {

    private static final String COLUMNS = """
            id, tenant_id, user_id, member_name, member_status, is_owner,
            permission_version, joined_at, created_at, updated_at, lock_version
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_tenant_member
                (id, tenant_id, user_id, member_name, member_status, is_owner,
                 permission_version, joined_at, created_at, updated_at, lock_version)
            VALUES
                (:id, :tenantId, :userId, :memberName, :memberStatus, :owner,
                 :permissionVersion, :joinedAt, :createdAt, :updatedAt, :lockVersion)
            """;
    private static final String UPDATE_SQL = """
            UPDATE sys_tenant_member
               SET member_name=:memberName, member_status=:memberStatus,
                   permission_version=:permissionVersion, updated_at=:updatedAt,
                   lock_version=:lockVersion
             WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedLockVersion
               AND deleted_at IS NULL
            """;
    private static final String DELETE_SQL = """
            UPDATE sys_tenant_member SET deleted_at=UTC_TIMESTAMP(3), lock_version=lock_version+1
             WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedLockVersion
               AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OptimisticLockExecutor optimisticLockExecutor;

    public JdbcMemberMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            OptimisticLockExecutor optimisticLockExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimisticLockExecutor = optimisticLockExecutor;
    }

    @Override
    public MemberEntity insert(MemberEntity member) {
        try {
            jdbcTemplate.update(INSERT_SQL, parameters(member));
            return member;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("用户已是该租户成员");
        }
    }

    @Override
    public Optional<MemberEntity> findById(long tenantId, long memberId) {
        return query("SELECT " + COLUMNS + " FROM sys_tenant_member"
                + " WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL",
                Map.of("tenantId", tenantId, "id", memberId)).stream().findFirst();
    }

    @Override
    public Optional<MemberEntity> findByTenantAndUser(long tenantId, long userId) {
        return query("SELECT " + COLUMNS + " FROM sys_tenant_member"
                + " WHERE tenant_id=:tenantId AND user_id=:userId AND deleted_at IS NULL",
                Map.of("tenantId", tenantId, "userId", userId)).stream().findFirst();
    }

    @Override
    public Optional<MemberEntity> findByTenantAndUserForUpdate(long tenantId, long userId) {
        return query("SELECT " + COLUMNS + " FROM sys_tenant_member"
                + " WHERE tenant_id=:tenantId AND user_id=:userId AND deleted_at IS NULL FOR UPDATE",
                Map.of("tenantId", tenantId, "userId", userId)).stream().findFirst();
    }

    @Override
    public List<MemberEntity> findByTenantId(long tenantId) {
        return query("SELECT " + COLUMNS + " FROM sys_tenant_member"
                + " WHERE tenant_id=:tenantId AND deleted_at IS NULL ORDER BY id",
                Map.of("tenantId", tenantId));
    }

    @Override
    public List<MemberEntity> findByUserId(long userId) {
        return query("SELECT " + COLUMNS + " FROM sys_tenant_member"
                + " WHERE user_id=:userId AND deleted_at IS NULL ORDER BY tenant_id",
                Map.of("userId", userId));
    }

    @Override
    public MemberEntity update(MemberEntity member, long expectedLockVersion) {
        Map<String, Object> parameters = new java.util.HashMap<>(parameters(member));
        parameters.put("expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_SQL, parameters);
        return member;
    }

    @Override
    public boolean softDelete(long tenantId, long memberId, long expectedLockVersion) {
        return jdbcTemplate.update(DELETE_SQL, Map.of(
                "tenantId", tenantId, "id", memberId,
                "expectedLockVersion", expectedLockVersion)) == 1;
    }

    private List<MemberEntity> query(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapMember);
    }

    private Map<String, Object> parameters(MemberEntity member) {
        return Map.ofEntries(
                Map.entry("id", member.id()), Map.entry("tenantId", member.tenantId()),
                Map.entry("userId", member.userId()), Map.entry("memberName", member.memberName()),
                Map.entry("memberStatus", member.memberStatus()), Map.entry("owner", member.owner()),
                Map.entry("permissionVersion", member.permissionVersion()),
                Map.entry("joinedAt", Timestamp.from(member.joinedAt())),
                Map.entry("createdAt", Timestamp.from(member.createdAt())),
                Map.entry("updatedAt", Timestamp.from(member.updatedAt())),
                Map.entry("lockVersion", member.lockVersion()));
    }

    private MemberEntity mapMember(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MemberEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"), resultSet.getLong("user_id"),
                resultSet.getString("member_name"), resultSet.getString("member_status"),
                resultSet.getBoolean("is_owner"), resultSet.getLong("permission_version"),
                resultSet.getTimestamp("joined_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("lock_version"));
    }
}
