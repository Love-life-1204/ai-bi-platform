package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
public record TaskAuthorizationSnapshot(
        @JsonResourceId long sessionId,
        @JsonResourceId long userId,
        @JsonResourceId long tenantId,
        @JsonResourceId long memberId,
        long tenantPermissionVersion,
        long memberPermissionVersion,
        AccessCheckRequest accessRequest) {
}
