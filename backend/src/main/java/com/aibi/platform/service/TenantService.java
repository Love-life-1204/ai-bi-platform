package com.aibi.platform.service;

import com.aibi.platform.common.dto.CreateTenantRequest;
import com.aibi.platform.common.dto.ChangeTenantStatusRequest;
import com.aibi.platform.common.dto.TenantData;
import com.aibi.platform.common.dto.UpdateTenantRequest;

public interface TenantService {

    TenantData createTenant(String authorization, CreateTenantRequest request);

    TenantData createTenant(String authorization, CreateTenantRequest request, String idempotencyKey);

    TenantData getTenant(String authorization, long tenantId);

    TenantData updateTenant(String authorization, long tenantId, UpdateTenantRequest request);

    TenantData freezeTenant(String authorization, long tenantId, ChangeTenantStatusRequest request);

    TenantData unfreezeTenant(String authorization, long tenantId, ChangeTenantStatusRequest request);
}
