package com.aibi.platform.identity.entity;

import java.time.Instant;

public record MemberRoleEntity(
        long id,
        long tenantId,
        long memberId,
        long roleId,
        Long workspaceId,
        String scopeKey,
        Instant expiresAt) {

    public boolean isEffectiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
