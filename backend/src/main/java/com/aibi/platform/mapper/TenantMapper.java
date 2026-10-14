package com.aibi.platform.mapper;

import com.aibi.platform.entity.TenantEntity;
import java.util.Optional;

public interface TenantMapper {

    boolean existsByTenantCode(String tenantCode);

    TenantEntity insert(TenantEntity tenant);

    Optional<TenantEntity> findById(long tenantId);

    default Optional<TenantEntity> findByIdForUpdate(long tenantId) { return findById(tenantId); }

    TenantEntity updateBasicInfo(TenantEntity tenant, long expectedLockVersion);

    TenantEntity updateStatus(TenantEntity tenant, long expectedLockVersion);

    TenantEntity incrementPermissionVersion(long tenantId);
}
