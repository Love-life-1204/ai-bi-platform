package com.aibi.platform.source.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateDatasourceRequest(
        @Positive @JsonResourceId long workspaceId,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "只能包含小写字母、数字和下划线，且以字母开头")
        String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull DatasourceType type,
        @NotNull @Valid DatasourceEndpoint endpoint,
        @NotNull @Valid DatasourceCredential credential) {
}
