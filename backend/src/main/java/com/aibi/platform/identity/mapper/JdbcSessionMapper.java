package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.SessionEntity;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcSessionMapper implements SessionMapper {

    private static final String COLUMNS = """
            id, user_id, token_hash, expires_at, revoked_at, device_fingerprint,
            rotated_from_id, created_at, lock_version
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_login_session
                (id, user_id, token_hash, expires_at, revoked_at, device_fingerprint,
                 rotated_from_id, created_at, updated_at, lock_version)
            VALUES
                (:id, :userId, :tokenHash, :expiresAt, :revokedAt, :deviceFingerprint,
                 :rotatedFromId, :createdAt, :createdAt, :lockVersion)
            """;
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + """
             FROM sys_login_session WHERE id=:id AND deleted_at IS NULL
            """;
    private static final String SELECT_BY_HASH = "SELECT " + COLUMNS + """
             FROM sys_login_session WHERE token_hash=:tokenHash AND deleted_at IS NULL
            """;
    private static final String REVOKE_SQL = """
            UPDATE sys_login_session
               SET revoked_at=:revokedAt, updated_at=:revokedAt, lock_version=lock_version+1
             WHERE id=:id AND revoked_at IS NULL AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSessionMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SessionEntity insert(SessionEntity session) {
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("id", session.id()).addValue("userId", session.userId())
                    .addValue("tokenHash", session.refreshTokenHash())
                    .addValue("expiresAt", Timestamp.from(session.expiresAt()))
                    .addValue("revokedAt", session.revokedAt() == null
                            ? null : Timestamp.from(session.revokedAt()))
                    .addValue("deviceFingerprint", session.deviceFingerprint())
                    .addValue("rotatedFromId", session.rotatedFromId())
                    .addValue("createdAt", Timestamp.from(session.createdAt()))
                    .addValue("lockVersion", session.lockVersion());
            jdbcTemplate.update(INSERT_SQL, parameters);
            return session;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("会话标识冲突");
        }
    }

    @Override
    public Optional<SessionEntity> findById(long sessionId) {
        return query(SELECT_BY_ID, Map.of("id", sessionId));
    }

    @Override
    public Optional<SessionEntity> findByRefreshTokenHash(String tokenHash) {
        return query(SELECT_BY_HASH, Map.of("tokenHash", tokenHash));
    }

    @Override
    public boolean revoke(long sessionId, Instant revokedAt) {
        return jdbcTemplate.update(REVOKE_SQL,
                Map.of("id", sessionId, "revokedAt", Timestamp.from(revokedAt))) == 1;
    }

    private Optional<SessionEntity> query(String sql, Map<String, ?> parameters) {
        List<SessionEntity> sessions = jdbcTemplate.query(sql, parameters, this::mapSession);
        return sessions.stream().findFirst();
    }

    private SessionEntity mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
        long rotatedFrom = resultSet.getLong("rotated_from_id");
        boolean rotatedFromWasNull = resultSet.wasNull();
        return new SessionEntity(
                resultSet.getLong("id"), resultSet.getLong("user_id"),
                resultSet.getString("token_hash"), resultSet.getTimestamp("expires_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(), resultSet.getString("device_fingerprint"),
                rotatedFromWasNull ? null : rotatedFrom, resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getLong("lock_version"));
    }
}
