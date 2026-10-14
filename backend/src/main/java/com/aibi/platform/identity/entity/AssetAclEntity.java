package com.aibi.platform.identity.entity;

import java.time.Instant;

public record AssetAclEntity(
        long id,
        long tenantId,
        long assetId,
        String principalType,
        long principalId,
        String actionCode,
        String effect,
        Instant expiresAt) {

    public boolean isEffectiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
