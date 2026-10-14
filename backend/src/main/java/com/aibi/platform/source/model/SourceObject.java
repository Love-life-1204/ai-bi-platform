package com.aibi.platform.source.model;

import com.aibi.platform.common.json.JsonResourceId;
import java.util.List;

public record SourceObject(
        @JsonResourceId Long id,
        String schema,
        String name,
        String type,
        String comment,
        String schemaHash,
        List<SourceColumn> columns) {

    public SourceObject(String schema, String name, String type, String comment,
            String schemaHash, List<SourceColumn> columns) {
        this(null, schema, name, type, comment, schemaHash, columns);
    }
}
