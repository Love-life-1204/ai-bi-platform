package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
public record WorkspaceData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        String workspaceCode,
        String workspaceName,
        String workspaceStatus,
        @JsonResourceId long ownerMemberId,
        long lockVersion) {
}
