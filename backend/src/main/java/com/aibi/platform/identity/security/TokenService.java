package com.aibi.platform.identity.security;

import com.aibi.platform.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] signingKey = randomBytes(32);

    public IssuedAccessToken issueAccessToken(long sessionId, long userId, Instant now) {
        Instant expiresAt = now.plus(ACCESS_TTL);
        String payload = "v1." + sessionId + "." + userId + "." + expiresAt.getEpochSecond()
                + "." + randomToken(12);
        String signature = encode(hmac(payload));
        return new IssuedAccessToken(payload + "." + signature, expiresAt);
    }

    public AccessClaims verifyAccessToken(String token, Instant now) {
        if (token == null) {
            throw new UnauthorizedException();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 6 || !"v1".equals(parts[0])) {
            throw new UnauthorizedException();
        }
        String payload = String.join(".", parts[0], parts[1], parts[2], parts[3], parts[4]);
        if (!MessageDigest.isEqual(hmac(payload), decode(parts[5]))) {
            throw new UnauthorizedException();
        }
        try {
            long sessionId = Long.parseLong(parts[1]);
            long userId = Long.parseLong(parts[2]);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[3]));
            if (!expiresAt.isAfter(now)) {
                throw new UnauthorizedException();
            }
            return new AccessClaims(sessionId, userId, expiresAt);
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException();
        }
    }

    public IssuedTenantToken issueTenantToken(AccessClaims access, long tenantId, long memberId,
            long tenantPermissionVersion, long memberPermissionVersion, Instant now) {
        Instant expiresAt = access.expiresAt().isBefore(now.plus(ACCESS_TTL))
                ? access.expiresAt() : now.plus(ACCESS_TTL);
        String payload = "tc1." + access.sessionId() + "." + access.userId() + "." + tenantId
                + "." + memberId + "." + tenantPermissionVersion + "." + memberPermissionVersion
                + "." + expiresAt.getEpochSecond() + "." + randomToken(12);
        return new IssuedTenantToken(payload + "." + encode(hmac(payload)), expiresAt);
    }

    public TenantClaims verifyTenantToken(String token, Instant now) {
        if (token == null) {
            throw new UnauthorizedException();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 10 || !"tc1".equals(parts[0])) {
            throw new UnauthorizedException();
        }
        String payload = String.join(".", java.util.Arrays.copyOf(parts, 9));
        if (!MessageDigest.isEqual(hmac(payload), decode(parts[9]))) {
            throw new UnauthorizedException();
        }
        try {
            TenantClaims claims = new TenantClaims(
                    Long.parseLong(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3]),
                    Long.parseLong(parts[4]), Long.parseLong(parts[5]), Long.parseLong(parts[6]),
                    Instant.ofEpochSecond(Long.parseLong(parts[7])));
            if (!claims.expiresAt().isAfter(now)) {
                throw new UnauthorizedException();
            }
            return claims;
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException();
        }
    }

    public String newRefreshToken() {
        return newOpaqueToken();
    }

    public String newOpaqueToken() {
        return randomToken(32);
    }

    public Instant refreshExpiresAt(Instant now) {
        return now.plus(REFRESH_TTL);
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("运行环境缺少 HMAC-SHA256", exception);
        }
    }

    private String randomToken(int size) {
        return encode(randomBytes(size));
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException();
        }
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }

    public record AccessClaims(long sessionId, long userId, Instant expiresAt) {
    }

    public record IssuedTenantToken(String value, Instant expiresAt) {
    }

    public record TenantClaims(
            long sessionId,
            long userId,
            long tenantId,
            long memberId,
            long tenantPermissionVersion,
            long memberPermissionVersion,
            Instant expiresAt) {
    }
}
