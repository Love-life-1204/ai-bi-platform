package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record DataPolicyEvaluationData(
        @JsonResourceId long datasetId,
        Map<String, List<JsonNode>> rowPolicyGroups,
        List<ColumnAccessData> columns) {
}
