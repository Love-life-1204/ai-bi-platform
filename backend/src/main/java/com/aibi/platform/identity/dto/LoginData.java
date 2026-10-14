package com.aibi.platform.identity.dto;

import java.time.Instant;

public record LoginData(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserData user) {
}
