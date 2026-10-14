package com.aibi.platform.source.model;

import com.aibi.platform.common.json.JsonResourceId;
public record SourceColumn(
        @JsonResourceId Long id,
        String name,
        String stableKey,
        int ordinalPosition,
        String dataType,
        boolean nullable,
        boolean primaryKey,
        String comment) {

    public SourceColumn(String name, String stableKey, int ordinalPosition,
            String dataType, boolean nullable, boolean primaryKey, String comment) {
        this(null, name, stableKey, ordinalPosition, dataType, nullable, primaryKey, comment);
    }
}
