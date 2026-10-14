package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;

public record MemberRoleData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        @JsonResourceId long memberId,
        @JsonResourceId long roleId,
        String roleCode,
        @JsonResourceId Long workspaceId,
        String scopeKey,
        Instant expiresAt,
        boolean effective) {
}
