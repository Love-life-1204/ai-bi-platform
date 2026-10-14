package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AuthorizeWorkspaceMemberRequest(
        @Positive @JsonResourceId long memberId,
        @PositiveOrZero long memberLockVersion) {
}
