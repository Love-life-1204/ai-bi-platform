package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AccessCheckRequest(
        @Positive @JsonResourceId Long workspaceId,
        @NotBlank @Size(max = 128) String permissionCode,
        @Positive @JsonResourceId Long assetId,
        @Size(max = 64) String actionCode) {
}
