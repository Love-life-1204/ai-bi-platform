package com.aibi.platform.identity.entity;

import java.time.Instant;

public record UserEntity(
        long id,
        String email,
        String displayName,
        String passwordHash,
        String userStatus,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
