package com.aibi.platform.identity.entity;

import java.time.Instant;

public record MemberEntity(
        long id,
        long tenantId,
        long userId,
        String memberName,
        String memberStatus,
        boolean owner,
        long permissionVersion,
        Instant joinedAt,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
