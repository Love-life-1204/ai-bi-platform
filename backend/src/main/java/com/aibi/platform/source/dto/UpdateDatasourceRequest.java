package com.aibi.platform.source.dto;

import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.model.DatasourceEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateDatasourceRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Valid DatasourceEndpoint endpoint,
        @Valid DatasourceCredential credential,
        @PositiveOrZero long lockVersion) {
}
