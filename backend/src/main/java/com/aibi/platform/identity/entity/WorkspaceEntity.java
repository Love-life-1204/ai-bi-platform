package com.aibi.platform.identity.entity;

import java.time.Instant;

public record WorkspaceEntity(
        long id,
        long tenantId,
        String workspaceCode,
        String workspaceName,
        String workspaceStatus,
        long ownerMemberId,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
