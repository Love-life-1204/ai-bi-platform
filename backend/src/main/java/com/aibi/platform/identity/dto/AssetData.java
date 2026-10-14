package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
public record AssetData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        @JsonResourceId long workspaceId,
        String assetType,
        @JsonResourceId long assetRefId,
        @JsonResourceId long ownerMemberId,
        String assetStatus) {
}
