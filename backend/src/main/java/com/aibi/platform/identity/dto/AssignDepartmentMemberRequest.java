package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AssignDepartmentMemberRequest(
        @Positive @JsonResourceId long memberId,
        boolean primary,
        @PositiveOrZero long memberLockVersion) {
}
