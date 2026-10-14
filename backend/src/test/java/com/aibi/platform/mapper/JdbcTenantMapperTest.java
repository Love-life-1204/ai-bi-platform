package com.aibi.platform.mapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.common.exception.TenantCodeAlreadyExistsException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.entity.TenantEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcTenantMapperTest {

    @Test
    void shouldTranslateConcurrentUniqueConstraintConflict() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        OptimisticLockExecutor optimisticLockExecutor = mock(OptimisticLockExecutor.class);
        JdbcTenantMapper mapper = new JdbcTenantMapper(jdbcTemplate, optimisticLockExecutor);
        TenantEntity tenant = new TenantEntity(
                1,
                "acme",
                "Acme",
                "ACTIVE",
                "Asia/Shanghai",
                "CNY",
                1,
                Instant.parse("2026-10-10T00:00:00Z"),
                Instant.parse("2026-10-10T00:00:00Z"),
                0);
        when(jdbcTemplate.update(anyString(), anyMap())).thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Long.class))).thenReturn(1L);

        assertThatThrownBy(() -> mapper.insert(tenant))
                .isInstanceOf(TenantCodeAlreadyExistsException.class)
                .hasMessage("租户编码已存在");
    }
}
