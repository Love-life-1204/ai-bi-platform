package com.aibi.platform.source.model;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;
import java.util.List;

public record MetadataSnapshot(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        @JsonResourceId long datasourceId,
        long version,
        String schemaHash,
        List<SourceObject> objects,
        Instant collectedAt) {
}
