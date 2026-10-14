package com.aibi.platform.source.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DatasourceEndpoint(
        @NotBlank @Size(max = 253) String host,
        @Min(1) @Max(65535) int port,
        @NotBlank @Size(max = 128) String database,
        boolean tls) {
}
