package com.aibi.platform.source.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TestConnectionRequest(
        @NotNull DatasourceType type,
        @NotNull @Valid DatasourceEndpoint endpoint,
        @NotNull @Valid DatasourceCredential credential,
        @Positive @JsonResourceId Long workspaceId) {

    public TestConnectionRequest(DatasourceType type, DatasourceEndpoint endpoint, DatasourceCredential credential) {
        this(type, endpoint, credential, null);
    }
}
