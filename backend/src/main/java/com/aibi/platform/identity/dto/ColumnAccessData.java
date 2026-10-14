package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.fasterxml.jackson.databind.JsonNode;

public record ColumnAccessData(@JsonResourceId long datasetFieldId, String accessMode, JsonNode maskConfig) {
}
