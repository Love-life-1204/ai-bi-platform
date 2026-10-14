package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String roleCode,
        @NotBlank @Size(max = 200) String roleName) {
}
