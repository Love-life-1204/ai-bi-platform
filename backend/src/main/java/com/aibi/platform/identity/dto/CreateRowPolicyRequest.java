package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRowPolicyRequest(
        @Positive @JsonResourceId long datasetId,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String policyCode,
        @NotBlank @Size(max = 200) String policyName,
        @NotNull JsonNode policyAst,
        @NotBlank @Size(max = 64) String policyGroup) {
}
