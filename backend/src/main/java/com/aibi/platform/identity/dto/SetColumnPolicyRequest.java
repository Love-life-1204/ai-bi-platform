package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record SetColumnPolicyRequest(
        @Positive @JsonResourceId long datasetFieldId,
        @NotBlank @Pattern(regexp = "MEMBER|ROLE|DEPARTMENT") String principalType,
        @Positive @JsonResourceId long principalId,
        @NotBlank @Pattern(regexp = "VISIBLE|MASKED|DENY") String accessMode,
        JsonNode maskConfig) {
}
