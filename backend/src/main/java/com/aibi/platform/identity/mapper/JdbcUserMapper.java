package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.UserEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
public class JdbcUserMapper implements UserMapper {

    private static final String SELECT_COLUMNS = """
            id, email, display_name, password_hash, user_status, last_login_at,
            created_at, updated_at, lock_version
            """;
    private static final String SELECT_BY_EMAIL = "SELECT " + SELECT_COLUMNS + """
             FROM sys_user WHERE email=:email AND deleted_at IS NULL
            """;
    private static final String SELECT_BY_ID = "SELECT " + SELECT_COLUMNS + """
             FROM sys_user WHERE id=:id AND deleted_at IS NULL
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_user
                (id, email, display_name, password_hash, user_status, created_at, updated_at, lock_version)
            VALUES
                (:id, :email, :displayName, :passwordHash, :userStatus, :createdAt, :updatedAt, :lockVersion)
            """;
    private static final String UPDATE_SQL = """
            UPDATE sys_user
               SET display_name=:displayName, password_hash=:passwordHash,
                   user_status=:userStatus, last_login_at=:lastLoginAt,
                   updated_at=:updatedAt, lock_version=:lockVersion
             WHERE id=:id AND lock_version=:expectedLockVersion AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OptimisticLockExecutor optimisticLockExecutor;

    public JdbcUserMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            OptimisticLockExecutor optimisticLockExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimisticLockExecutor = optimisticLockExecutor;
    }

    @Override
    public Optional<UserEntity> findByEmail(String normalizedEmail) {
        List<UserEntity> users = jdbcTemplate.query(
                SELECT_BY_EMAIL, Map.of("email", normalizedEmail), this::mapUser);
        return users.stream().findFirst();
    }

    @Override
    public Optional<UserEntity> findById(long userId) {
        List<UserEntity> users = jdbcTemplate.query(
                SELECT_BY_ID, Map.of("id", userId), this::mapUser);
        return users.stream().findFirst();
    }

    @Override
    public UserEntity insert(UserEntity user) {
        try {
            jdbcTemplate.update(INSERT_SQL, parameters(user));
            return user;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("邮箱已注册");
        }
    }

    @Override
    public UserEntity update(UserEntity user, long expectedLockVersion) {
        MapSqlParameterSource parameters = parameters(user)
                .addValue("expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_SQL, parameters.getValues());
        return user;
    }

    private MapSqlParameterSource parameters(UserEntity user) {
        return new MapSqlParameterSource()
                .addValue("id", user.id())
                .addValue("email", user.email())
                .addValue("displayName", user.displayName())
                .addValue("passwordHash", user.passwordHash())
                .addValue("userStatus", user.userStatus())
                .addValue("lastLoginAt", timestamp(user.lastLoginAt()))
                .addValue("createdAt", Timestamp.from(user.createdAt()))
                .addValue("updatedAt", Timestamp.from(user.updatedAt()))
                .addValue("lockVersion", user.lockVersion());
    }

    private UserEntity mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp lastLogin = resultSet.getTimestamp("last_login_at");
        return new UserEntity(
                resultSet.getLong("id"), resultSet.getString("email"),
                resultSet.getString("display_name"), resultSet.getString("password_hash"),
                resultSet.getString("user_status"), lastLogin == null ? null : lastLogin.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("lock_version"));
    }

    private Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
