package com.aibi.platform.source.dto;

import java.util.List;

public record MetadataDiffData(
        long fromVersion,
        long toVersion,
        List<String> addedObjects,
        List<String> removedObjects,
        List<String> changedObjects,
        List<String> addedColumns,
        List<String> removedColumns,
        List<String> changedColumns) {
}
