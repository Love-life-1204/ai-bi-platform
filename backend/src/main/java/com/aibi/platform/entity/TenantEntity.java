package com.aibi.platform.entity;

import java.time.Instant;

public record TenantEntity(
        long id,
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
