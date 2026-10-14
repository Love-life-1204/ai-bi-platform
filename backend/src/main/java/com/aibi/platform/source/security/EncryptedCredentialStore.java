package com.aibi.platform.source.security;

import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.source.model.DatasourceCredential;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地加密凭据存储。数据库实体只持有 opaque reference；生产可用同一接口替换为 KMS/Vault。
 */
@Component
public class EncryptedCredentialStore implements CredentialStore {

    private static final String PREFIX = "secret://datasource/";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;
    private final Map<String, byte[]> encryptedSecrets = new ConcurrentHashMap<>();

    public EncryptedCredentialStore(ObjectMapper objectMapper,
            @Value("${app.connector.master-key:}") String configuredKey) {
        this.objectMapper = objectMapper;
        this.key = loadKey(configuredKey);
    }

    @Override
    public String put(DatasourceCredential credential) {
        String reference = PREFIX + UUID.randomUUID();
        encryptedSecrets.put(reference, encrypt(serialize(credential)));
        return reference;
    }

    @Override
    public DatasourceCredential read(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new ApiException(ApiErrorCode.DATASOURCE_AUTH_FAILED, "凭据引用无效");
        }
        byte[] encrypted = encryptedSecrets.get(reference);
        if (encrypted == null) {
            throw new ApiException(ApiErrorCode.DATASOURCE_AUTH_FAILED, "凭据不存在或已失效");
        }
        try {
            return objectMapper.readValue(decrypt(encrypted), DatasourceCredential.class);
        } catch (IOException exception) {
            throw new IllegalStateException("凭据内容无法解析", exception);
        }
    }

    @Override
    public void delete(String reference) {
        if (reference != null) {
            encryptedSecrets.remove(reference);
        }
    }

    private SecretKey loadKey(String configuredKey) {
        try {
            if (configuredKey != null && !configuredKey.isBlank()) {
                byte[] bytes = Base64.getDecoder().decode(configuredKey);
                if (bytes.length != 32) {
                    throw new IllegalStateException("app.connector.master-key 必须是 Base64 编码的 256 位密钥");
                }
                return new SecretKeySpec(bytes, "AES");
            }
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法初始化凭据加密密钥", exception);
        }
    }

    private byte[] serialize(DatasourceCredential credential) {
        try {
            return objectMapper.writeValueAsBytes(credential);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("凭据无法序列化", exception);
        }
    }

    private byte[] encrypt(byte[] clearText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(clearText);
            return ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("凭据加密失败", exception);
        }
    }

    private byte[] decrypt(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("凭据解密失败", exception);
        }
    }
}
