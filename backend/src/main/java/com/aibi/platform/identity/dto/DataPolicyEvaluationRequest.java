package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record DataPolicyEvaluationRequest(
        @Positive @JsonResourceId long workspaceId,
        @Positive @JsonResourceId long datasetId,
        @NotEmpty @JsonResourceIds List<@Positive Long> datasetFieldIds) {
}
