package com.aibi.platform.identity.entity;

import java.time.Instant;

public record SessionEntity(
        long id,
        long userId,
        String refreshTokenHash,
        Instant expiresAt,
        Instant revokedAt,
        String deviceFingerprint,
        Long rotatedFromId,
        Instant createdAt,
        long lockVersion) {

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
