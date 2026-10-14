package com.aibi.platform.identity.entity;

import java.time.Instant;

public record InvitationEntity(
        long id,
        long tenantId,
        String email,
        String tokenHash,
        long inviterMemberId,
        Long workspaceId,
        Long roleId,
        Instant expiresAt,
        Instant acceptedAt,
        String invitationStatus,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
