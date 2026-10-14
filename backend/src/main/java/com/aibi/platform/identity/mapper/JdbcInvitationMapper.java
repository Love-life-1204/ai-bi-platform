package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.InvitationEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcInvitationMapper implements InvitationMapper {

    private static final String COLUMNS = """
            id, tenant_id, email, invite_token_hash, inviter_member_id, workspace_id, role_id,
            expires_at, accepted_at, invitation_status, created_at, updated_at, lock_version
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_invitation
                (id, tenant_id, email, invite_token_hash, inviter_member_id, workspace_id, role_id,
                 expires_at, accepted_at, invitation_status, created_at, updated_at, lock_version)
            VALUES
                (:id, :tenantId, :email, :tokenHash, :inviterMemberId, :workspaceId, :roleId,
                 :expiresAt, :acceptedAt, :status, :createdAt, :updatedAt, :lockVersion)
            """;
    private static final String ACCEPT_SQL = """
            UPDATE sys_invitation
               SET accepted_at=:acceptedAt, invitation_status='ACCEPTED', updated_at=:acceptedAt,
                   lock_version=lock_version+1
             WHERE id=:id AND invitation_status='PENDING' AND lock_version=:expectedLockVersion
               AND accepted_at IS NULL AND expires_at>:acceptedAt AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInvitationMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InvitationEntity insert(InvitationEntity invitation) {
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("id", invitation.id()).addValue("tenantId", invitation.tenantId())
                    .addValue("email", invitation.email()).addValue("tokenHash", invitation.tokenHash())
                    .addValue("inviterMemberId", invitation.inviterMemberId())
                    .addValue("workspaceId", invitation.workspaceId()).addValue("roleId", invitation.roleId())
                    .addValue("expiresAt", Timestamp.from(invitation.expiresAt()))
                    .addValue("acceptedAt", null).addValue("status", invitation.invitationStatus())
                    .addValue("createdAt", Timestamp.from(invitation.createdAt()))
                    .addValue("updatedAt", Timestamp.from(invitation.updatedAt()))
                    .addValue("lockVersion", invitation.lockVersion());
            jdbcTemplate.update(INSERT_SQL, parameters);
            return invitation;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("邀请令牌冲突");
        }
    }

    @Override
    public Optional<InvitationEntity> findByTokenHash(String tokenHash) {
        List<InvitationEntity> values = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sys_invitation"
                        + " WHERE invite_token_hash=:tokenHash AND deleted_at IS NULL",
                Map.of("tokenHash", tokenHash), this::mapInvitation);
        return values.stream().findFirst();
    }

    @Override
    public boolean accept(long invitationId, Instant acceptedAt, long expectedLockVersion) {
        return jdbcTemplate.update(ACCEPT_SQL, Map.of(
                "id", invitationId, "acceptedAt", Timestamp.from(acceptedAt),
                "expectedLockVersion", expectedLockVersion)) == 1;
    }

    private InvitationEntity mapInvitation(ResultSet resultSet, int rowNumber) throws SQLException {
        long workspaceId = resultSet.getLong("workspace_id");
        Long workspace = resultSet.wasNull() ? null : workspaceId;
        long roleId = resultSet.getLong("role_id");
        Long role = resultSet.wasNull() ? null : roleId;
        Timestamp acceptedAt = resultSet.getTimestamp("accepted_at");
        return new InvitationEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"), resultSet.getString("email"),
                resultSet.getString("invite_token_hash"), resultSet.getLong("inviter_member_id"),
                workspace, role, resultSet.getTimestamp("expires_at").toInstant(),
                acceptedAt == null ? null : acceptedAt.toInstant(), resultSet.getString("invitation_status"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("lock_version"));
    }
}
