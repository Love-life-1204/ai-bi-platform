package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record AssignMemberRoleRequest(
        @Positive @JsonResourceId long roleId,
        @Positive @JsonResourceId Long workspaceId,
        @Future Instant expiresAt,
        @PositiveOrZero long memberLockVersion) {
}
