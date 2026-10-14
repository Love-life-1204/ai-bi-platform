package com.aibi.platform.identity.entity;

public record WorkspaceMemberEntity(
        long id,
        long tenantId,
        long workspaceId,
        long memberId,
        String memberStatus) {
}
