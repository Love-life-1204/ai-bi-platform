package com.aibi.platform.identity.entity;

public record AssetEntity(
        long id,
        long tenantId,
        long workspaceId,
        String assetType,
        long assetRefId,
        long ownerMemberId,
        String assetStatus) {
}
