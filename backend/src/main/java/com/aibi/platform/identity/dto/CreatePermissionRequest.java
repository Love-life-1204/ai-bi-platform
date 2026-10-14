package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$") String permissionCode,
        @NotBlank @Size(max = 200) String permissionName,
        @NotBlank @Size(max = 32) String resourceType,
        @NotBlank @Size(max = 64) String actionCode) {
}
