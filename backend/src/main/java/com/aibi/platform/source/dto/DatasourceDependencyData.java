package com.aibi.platform.source.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.util.List;

public record DatasourceDependencyData(
        @JsonResourceId long datasourceId,
        List<SyncJobDependency> syncJobs) {

    public record SyncJobDependency(@JsonResourceId long id, String jobCode, String status) {
    }
}
