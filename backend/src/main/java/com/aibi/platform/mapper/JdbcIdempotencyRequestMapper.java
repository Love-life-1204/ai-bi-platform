package com.aibi.platform.mapper;

import com.aibi.platform.common.idempotency.IdempotencyClaim;
import com.aibi.platform.common.idempotency.IdempotencyScope;
import com.aibi.platform.entity.IdempotencyRequestEntity;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcIdempotencyRequestMapper implements IdempotencyRequestMapper {

    private static final String FIND_SQL = """
            SELECT id, request_hash, request_status, response_ref, expires_at, lock_version
              FROM sys_idempotency_request
             WHERE tenant_id = :tenantId
               AND member_id = :memberId
               AND api_route = :apiRoute
               AND idempotency_key = :idempotencyKey
               AND deleted_at IS NULL
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_idempotency_request
                (id, tenant_id, member_id, api_route, idempotency_key, request_hash,
                 request_status, expires_at, lock_version)
            VALUES
                (:id, :tenantId, :memberId, :apiRoute, :idempotencyKey, :requestHash,
                 'PENDING', :expiresAt, 0)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public JdbcIdempotencyRequestMapper(
            @Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("mysqlTransactionManager") PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Duration ttl) {
        IdempotencyClaim result = transactionTemplate.execute(
                status -> claimInTransaction(scope, requestHash, ttl));
        if (result == null) {
            throw new IllegalStateException("幂等申领事务未返回结果");
        }
        return result;
    }

    @Override
    public void complete(IdempotencyScope scope, String requestHash, String responseJson) {
        transactionTemplate.executeWithoutResult(status -> {
            Map<String, Object> parameters = parameters(scope, requestHash);
            parameters.put("responseJson", responseJson);
            int affected = jdbcTemplate.update("""
                    UPDATE sys_idempotency_request
                       SET request_status = 'COMPLETED',
                           response_ref = CAST(:responseJson AS JSON),
                           lock_version = lock_version + 1
                     WHERE tenant_id = :tenantId
                       AND member_id = :memberId
                       AND api_route = :apiRoute
                       AND idempotency_key = :idempotencyKey
                       AND request_hash = :requestHash
                       AND request_status = 'PENDING'
                       AND deleted_at IS NULL
                    """, parameters);
            if (affected != 1) {
                throw new IllegalStateException("幂等记录完成更新失败");
            }
        });
    }

    @Override
    public void fail(IdempotencyScope scope, String requestHash) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE sys_idempotency_request
                   SET request_status = 'FAILED', lock_version = lock_version + 1
                 WHERE tenant_id = :tenantId
                   AND member_id = :memberId
                   AND api_route = :apiRoute
                   AND idempotency_key = :idempotencyKey
                   AND request_hash = :requestHash
                   AND request_status = 'PENDING'
                   AND deleted_at IS NULL
                """, parameters(scope, requestHash)));
    }

    private IdempotencyClaim claimInTransaction(IdempotencyScope scope, String requestHash, Duration ttl) {
        Instant now = Instant.now();
        for (int attempt = 0; attempt < 3; attempt++) {
            IdempotencyRequestEntity existing = find(scope);
            if (existing == null) {
                try {
                    Map<String, Object> parameters = parameters(scope, requestHash);
                    parameters.put("id", secureRandom.nextLong(1, Long.MAX_VALUE));
                    parameters.put("expiresAt", Timestamp.from(now.plus(ttl)));
                    jdbcTemplate.update(INSERT_SQL, parameters);
                    return IdempotencyClaim.acquired();
                } catch (DuplicateKeyException ignored) {
                    continue;
                }
            }
            if (!existing.requestHash().equals(requestHash) && existing.expiresAt().isAfter(now)) {
                return IdempotencyClaim.hashConflict();
            }
            if (existing.requestHash().equals(requestHash)
                    && "COMPLETED".equals(existing.requestStatus())
                    && existing.expiresAt().isAfter(now)) {
                return IdempotencyClaim.replay(existing.responseJson());
            }
            if (existing.requestHash().equals(requestHash)
                    && "PENDING".equals(existing.requestStatus())
                    && existing.expiresAt().isAfter(now)) {
                return IdempotencyClaim.inProgress();
            }
            if (reset(existing, scope, requestHash, now.plus(ttl))) {
                return IdempotencyClaim.acquired();
            }
        }
        return IdempotencyClaim.inProgress();
    }

    private IdempotencyRequestEntity find(IdempotencyScope scope) {
        List<IdempotencyRequestEntity> rows = jdbcTemplate.query(FIND_SQL, parameters(scope, null),
                (resultSet, rowNumber) -> new IdempotencyRequestEntity(
                        resultSet.getLong("id"),
                        resultSet.getString("request_hash"),
                        resultSet.getString("request_status"),
                        resultSet.getString("response_ref"),
                        resultSet.getTimestamp("expires_at").toInstant(),
                        resultSet.getLong("lock_version")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean reset(IdempotencyRequestEntity existing, IdempotencyScope scope,
            String requestHash, Instant expiresAt) {
        Map<String, Object> parameters = parameters(scope, requestHash);
        parameters.put("id", existing.id());
        parameters.put("lockVersion", existing.lockVersion());
        parameters.put("expiresAt", Timestamp.from(expiresAt));
        return jdbcTemplate.update("""
                UPDATE sys_idempotency_request
                   SET request_hash = :requestHash,
                       request_status = 'PENDING',
                       response_ref = NULL,
                       expires_at = :expiresAt,
                       lock_version = lock_version + 1
                 WHERE id = :id AND lock_version = :lockVersion AND deleted_at IS NULL
                """, parameters) == 1;
    }

    private Map<String, Object> parameters(IdempotencyScope scope, String requestHash) {
        Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("tenantId", scope.tenantId());
        parameters.put("memberId", scope.memberId());
        parameters.put("apiRoute", scope.apiRoute());
        parameters.put("idempotencyKey", scope.idempotencyKey());
        parameters.put("requestHash", requestHash);
        return parameters;
    }
}
