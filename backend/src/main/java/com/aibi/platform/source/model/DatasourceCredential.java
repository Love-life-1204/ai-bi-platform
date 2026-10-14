package com.aibi.platform.source.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DatasourceCredential(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 1024) String password) {
}
