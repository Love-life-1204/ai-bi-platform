package com.aibi.platform.entity;

import java.time.Instant;

public record IdempotencyRequestEntity(
        long id,
        String requestHash,
        String requestStatus,
        String responseJson,
        Instant expiresAt,
        long lockVersion) {
}
