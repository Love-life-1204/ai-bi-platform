package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SetRolePermissionRequest(
        @Positive @JsonResourceId long permissionId,
        @NotBlank @Pattern(regexp = "ALLOW|DENY") String effect) {
}
