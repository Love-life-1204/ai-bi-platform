package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;

public record InvitationData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        String email,
        @JsonResourceId Long workspaceId,
        @JsonResourceId Long roleId,
        String invitationStatus,
        Instant expiresAt,
        String inviteToken) {
}
