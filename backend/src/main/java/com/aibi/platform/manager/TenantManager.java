package com.aibi.platform.manager;

import com.aibi.platform.common.exception.TenantCodeAlreadyExistsException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.entity.TenantEntity;
import com.aibi.platform.mapper.TenantMapper;
import org.springframework.stereotype.Component;

@Component
public class TenantManager {

    private final TenantMapper tenantMapper;

    public TenantManager(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public TenantEntity create(TenantEntity tenant) {
        if (tenantMapper.existsByTenantCode(tenant.tenantCode())) {
            throw new TenantCodeAlreadyExistsException();
        }
        return tenantMapper.insert(tenant);
    }

    public TenantEntity getRequired(long tenantId) {
        return tenantMapper.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("租户不存在"));
    }

    public java.util.Optional<TenantEntity> find(long tenantId, boolean lock) {
        return lock ? tenantMapper.findByIdForUpdate(tenantId) : tenantMapper.findById(tenantId);
    }

    public TenantEntity updateBasicInfo(TenantEntity tenant, long expectedLockVersion) {
        return tenantMapper.updateBasicInfo(tenant, expectedLockVersion);
    }

    public TenantEntity updateStatus(TenantEntity tenant, long expectedLockVersion) {
        return tenantMapper.updateStatus(tenant, expectedLockVersion);
    }

    public TenantEntity incrementPermissionVersion(long tenantId) {
        return tenantMapper.incrementPermissionVersion(tenantId);
    }
}
