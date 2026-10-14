package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;

public record TenantContextData(
        String tokenType,
        String tenantAccessToken,
        Instant expiresAt,
        @JsonResourceId long tenantId,
        @JsonResourceId long memberId,
        long tenantPermissionVersion,
        long memberPermissionVersion) {
}
