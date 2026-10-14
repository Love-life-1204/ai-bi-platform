package com.aibi.platform.common.transaction;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class OptimisticLockExecutor {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OptimisticLockExecutor(
            @Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 租户业务表必须同时限定 tenant_id、业务主键和 lock_version；租户根表限定 id 和 lock_version。
     * SET 子句必须递增 lock_version。
     */
    public void updateExactlyOne(String sql, Map<String, ?> parameters) {
        int affectedRows = jdbcTemplate.update(sql, parameters);
        verifyAffectedRows(affectedRows);
    }

    static void verifyAffectedRows(int affectedRows) {
        if (affectedRows == 0) {
            throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        if (affectedRows != 1) {
            throw new IllegalStateException("乐观锁更新影响了非预期行数: " + affectedRows);
        }
    }
}
