package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(
        @Positive @JsonResourceId long workspaceId,
        @NotBlank @Size(max = 32) String assetType,
        @Positive @JsonResourceId long assetRefId) {
}
