package com.aibi.platform.ai.manager;

import com.aibi.platform.ai.config.AiProperties;
import com.aibi.platform.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class AiDelegationManager {

    private static final String ALGORITHM = "HmacSHA256";
    private static final long CLOCK_SKEW_SECONDS = 30;
    private final byte[] secret;
    private final Map<String, Instant> consumedNonces = new ConcurrentHashMap<>();

    public AiDelegationManager(AiProperties properties) {
        this.secret = properties.getSharedSecret().getBytes(StandardCharsets.UTF_8);
    }

    public SignedHeaders sign(String method, String path, byte[] body) {
        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        return new SignedHeaders(timestamp, nonce, signature(method, path, body, timestamp, nonce));
    }

    public void verify(String method, String path, byte[] body, HttpHeaders headers) {
        try {
            long timestamp = Long.parseLong(required(headers, "X-AI-Timestamp"));
            String nonce = required(headers, "X-AI-Nonce");
            String supplied = required(headers, "X-AI-Signature");
            Instant now = Instant.now();
            if (Math.abs(now.getEpochSecond() - timestamp) > CLOCK_SKEW_SECONDS) {
                throw new UnauthorizedException();
            }
            consumedNonces.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
            if (consumedNonces.putIfAbsent(nonce, now.plusSeconds(120)) != null) {
                throw new UnauthorizedException();
            }
            String expected = signature(method, path, body, timestamp, nonce);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) {
                consumedNonces.remove(nonce);
                throw new UnauthorizedException();
            }
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException();
        }
    }

    private String signature(String method, String path, byte[] body, long timestamp, String nonce) {
        String canonical = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + nonce
                + "\n" + sha256(body);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("运行环境缺少 HMAC-SHA256", exception);
        }
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private String required(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException();
        }
        return value;
    }

    public record SignedHeaders(long timestamp, String nonce, String signature) {
    }
}

