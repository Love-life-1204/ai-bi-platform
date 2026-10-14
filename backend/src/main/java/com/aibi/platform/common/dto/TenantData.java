package com.aibi.platform.common.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;

public record TenantData(
        @JsonResourceId long id,
        String tenantCode,
        String tenantName,
        String tenantStatus,
        String timezone,
        String defaultCurrency,
        long permissionVersion,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
