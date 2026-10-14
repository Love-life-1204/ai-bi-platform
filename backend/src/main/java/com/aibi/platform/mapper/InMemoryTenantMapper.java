package com.aibi.platform.mapper;

import com.aibi.platform.common.exception.TenantCodeAlreadyExistsException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.entity.TenantEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryTenantMapper implements TenantMapper {

    private final ConcurrentMap<String, TenantEntity> tenantsByCode = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, TenantEntity> tenantsById = new ConcurrentHashMap<>();

    @Override
    public boolean existsByTenantCode(String tenantCode) {
        return tenantsByCode.containsKey(tenantCode);
    }

    @Override
    public TenantEntity insert(TenantEntity tenant) {
        TenantEntity existing = tenantsByCode.putIfAbsent(tenant.tenantCode(), tenant);
        if (existing != null) {
            throw new TenantCodeAlreadyExistsException();
        }
        if (tenantsById.putIfAbsent(tenant.id(), tenant) != null) {
            tenantsByCode.remove(tenant.tenantCode(), tenant);
            throw new IllegalStateException("租户主键冲突");
        }
        return tenant;
    }

    @Override
    public Optional<TenantEntity> findById(long tenantId) {
        return Optional.ofNullable(tenantsById.get(tenantId));
    }

    @Override
    public TenantEntity updateBasicInfo(TenantEntity tenant, long expectedLockVersion) {
        return update(tenant, expectedLockVersion);
    }

    @Override
    public TenantEntity updateStatus(TenantEntity tenant, long expectedLockVersion) {
        return update(tenant, expectedLockVersion);
    }

    @Override
    public TenantEntity incrementPermissionVersion(long tenantId) {
        TenantEntity updated = tenantsById.compute(tenantId, (id, current) -> {
            if (current == null) {
                throw new NotFoundException("租户不存在");
            }
            return new TenantEntity(
                    current.id(), current.tenantCode(), current.tenantName(), current.tenantStatus(),
                    current.timezone(), current.defaultCurrency(), current.permissionVersion() + 1,
                    current.createdAt(), Instant.now(), current.lockVersion());
        });
        tenantsByCode.replace(updated.tenantCode(), updated);
        return updated;
    }

    private TenantEntity update(TenantEntity tenant, long expectedLockVersion) {
        TenantEntity updated = tenantsById.compute(tenant.id(), (id, current) -> {
            if (current == null) {
                throw new NotFoundException("租户不存在");
            }
            if (current.lockVersion() != expectedLockVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return tenant;
        });
        tenantsByCode.replace(updated.tenantCode(), updated);
        return updated;
    }
}
