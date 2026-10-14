package com.aibi.platform.identity.service;

import com.aibi.platform.commercial.dto.CommercialDtos.MfaEnrollment;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.UnauthorizedException;
import com.aibi.platform.identity.mapper.UserMapper;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MfaService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SessionService sessions;
    private final UserMapper users;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Factor> factors = new ConcurrentHashMap<>();
    private final NamedParameterJdbcTemplate jdbc;
    private final SecretKey encryptionKey;

    public MfaService(SessionService sessions, UserMapper users,
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> jdbcProvider,
            @Value("${app.datasource.enabled:false}") boolean databaseEnabled,
            @Value("${app.connector.master-key:}") String configuredKey) {
        this.sessions = sessions;
        this.users = users;
        this.jdbc = databaseEnabled ? jdbcProvider.getIfAvailable() : null;
        if (databaseEnabled && jdbc == null) {
            throw new IllegalStateException("MFA 持久化已启用但 MySQL 不可用");
        }
        this.encryptionKey = loadKey(configuredKey, databaseEnabled);
    }

    public MfaEnrollment enroll(String authorization) {
        long userId = sessions.authenticate(authorization).userId();
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        String encoded = base32(secret);
        save(userId, new Factor(secret, false, -1));
        String email = users.findById(userId).orElseThrow(UnauthorizedException::new).email();
        String uri = "otpauth://totp/AI-BI:" + encode(email)
                + "?secret=" + encoded + "&issuer=AI-BI&algorithm=SHA1&digits=6&period=30";
        return new MfaEnrollment(encoded, uri);
    }

    public void confirm(String authorization, String code) {
        long userId = sessions.authenticate(authorization).userId();
        Factor factor = find(userId);
        long step = verifyStep(factor == null ? null : factor.secret(), code, Instant.now());
        if (factor == null || step < 0) {
            throw new UnauthorizedException();
        }
        save(userId, new Factor(factor.secret(), true, step));
    }

    public void disable(String authorization, String code) {
        long userId = sessions.authenticate(authorization).userId();
        requireLoginCode(userId, code);
        delete(userId);
    }

    public synchronized void requireLoginCode(long userId, String code) {
        Factor factor = find(userId);
        if (factor == null || !factor.enabled()) {
            return;
        }
        long step = verifyStep(factor.secret(), code, Instant.now());
        if (step < 0 || step <= factor.lastUsedStep()) {
            throw new UnauthorizedException();
        }
        if (jdbc != null) {
            int updated = jdbc.update("UPDATE sys_mfa_factor SET last_used_step = :step,"
                    + " updated_at = CURRENT_TIMESTAMP(3) WHERE user_id = :userId"
                    + " AND factor_type = 'TOTP' AND factor_status = 'ACTIVE'"
                    + " AND (last_used_step IS NULL OR last_used_step < :step)",
                    Map.of("step", step, "userId", userId));
            if (updated != 1) {
                throw new UnauthorizedException();
            }
            return;
        }
        save(userId, new Factor(factor.secret(), true, step));
    }

    public boolean enabled(long userId) {
        Factor factor = find(userId);
        return factor != null && factor.enabled();
    }

    private Factor find(long userId) {
        if (jdbc == null) {
            return factors.get(userId);
        }
        String sql = "SELECT secret_ciphertext, factor_status, last_used_step"
                + " FROM sys_mfa_factor WHERE user_id = :userId AND factor_type = 'TOTP'";
        var rows = jdbc.query(sql, Map.of("userId", userId), (resultSet, rowNum) -> new Factor(
                decrypt(userId, resultSet.getBytes("secret_ciphertext")),
                "ACTIVE".equals(resultSet.getString("factor_status")),
                resultSet.getObject("last_used_step") == null
                        ? -1 : resultSet.getLong("last_used_step")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void save(long userId, Factor factor) {
        if (jdbc == null) {
            factors.put(userId, factor);
            return;
        }
        String sql = """
                INSERT INTO sys_mfa_factor
                    (id, user_id, factor_type, secret_ciphertext, factor_status,
                     confirmed_at, last_used_step)
                VALUES (:id, :userId, 'TOTP', :secret, :status, :confirmedAt, :lastUsedStep)
                ON DUPLICATE KEY UPDATE secret_ciphertext = VALUES(secret_ciphertext),
                    factor_status = VALUES(factor_status), confirmed_at = VALUES(confirmed_at),
                    last_used_step = VALUES(last_used_step), updated_at = CURRENT_TIMESTAMP(3)
                """;
        jdbc.update(sql, new MapSqlParameterSource().addValue("id", random.nextLong(1,
                        Long.MAX_VALUE)).addValue("userId", userId)
                .addValue("secret", encrypt(userId, factor.secret()))
                .addValue("status", factor.enabled() ? "ACTIVE" : "PENDING")
                .addValue("confirmedAt", factor.enabled()
                        ? LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC) : null)
                .addValue("lastUsedStep", factor.lastUsedStep() < 0
                        ? null : factor.lastUsedStep()));
    }

    private void delete(long userId) {
        if (jdbc == null) {
            factors.remove(userId);
        } else {
            jdbc.update("DELETE FROM sys_mfa_factor WHERE user_id = :userId"
                    + " AND factor_type = 'TOTP'", Map.of("userId", userId));
        }
    }

    private long verifyStep(byte[] secret, String code, Instant now) {
        if (secret == null || code == null || !code.matches("\\d{6}")) {
            return -1;
        }
        long counter = now.getEpochSecond() / 30;
        for (long drift = -1; drift <= 1; drift++) {
            if (totp(secret, counter + drift).equals(code)) {
                return counter + drift;
            }
        }
        return -1;
    }

    String currentCodeForTest(long userId, Instant now) {
        Factor factor = find(userId);
        if (factor == null) {
            throw new ConflictException("MFA 因子不存在");
        }
        return totp(factor.secret(), now.getEpochSecond() / 30);
    }

    private String totp(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JVM 不支持 TOTP", exception);
        }
    }

    private SecretKey loadKey(String configuredKey, boolean required) {
        try {
            if (configuredKey != null && !configuredKey.isBlank()) {
                byte[] bytes = Base64.getDecoder().decode(configuredKey);
                if (bytes.length != 32) {
                    throw new IllegalStateException("MFA 加密主密钥必须为 Base64 编码的 256 位密钥");
                }
                return new SecretKeySpec(bytes, "AES");
            }
            if (required) {
                throw new IllegalStateException("生产持久化模式必须配置 MFA 加密主密钥");
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法初始化 MFA 加密密钥", exception);
        }
    }

    private byte[] encrypt(long userId, byte[] clearText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH, iv));
            cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(userId).array());
            byte[] cipherText = cipher.doFinal(clearText);
            return ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MFA 因子加密失败", exception);
        }
    }

    private byte[] decrypt(long userId, byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH, iv));
            cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(userId).array());
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MFA 因子解密失败", exception);
        }
    }

    private String base32(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte item : bytes) {
            buffer = (buffer << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                value.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            value.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return value.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Factor(byte[] secret, boolean enabled, long lastUsedStep) {
    }
}
