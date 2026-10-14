package com.aibi.platform.identity.entity;

import java.time.Instant;

public record PolicyGrantEntity(
        long id,
        long tenantId,
        long policyId,
        String principalType,
        long principalId,
        Instant expiresAt) {

    public boolean isEffectiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
