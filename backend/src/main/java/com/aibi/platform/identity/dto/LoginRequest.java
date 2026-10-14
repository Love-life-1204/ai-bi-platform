package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password,
        @Size(max = 256) String deviceFingerprint,
        @Size(min = 6, max = 6) String mfaCode) {
}
