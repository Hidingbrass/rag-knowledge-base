package com.example.aikb.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 对简历、JD、聊天正文等敏感 TEXT 字段执行 AES-256-GCM 应用层加密。
 *
 * 未配置 SENSITIVE_DATA_ENCRYPTION_KEY 时保持本地开发兼容；生产环境配置后，
 * 新写入值以 enc:v1: 开头。既有明文仍可读取，便于无停机渐进迁移。
 */
@Converter
public class SensitiveTextConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        byte[] key = configuredKey();
        return key == null ? attribute : encrypt(attribute, key);
    }

    @Override
    public String convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null || !databaseValue.startsWith(PREFIX)) {
            return databaseValue;
        }
        byte[] key = configuredKey();
        if (key == null) {
            throw new IllegalStateException(
                    "检测到加密数据，但未配置 SENSITIVE_DATA_ENCRYPTION_KEY"
            );
        }
        return decrypt(databaseValue, key);
    }

    static String encrypt(String plaintext, byte[] key) {
        validateKey(key);
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv)
            );
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(iv, iv.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("敏感数据加密失败", exception);
        }
    }

    static String decrypt(String encrypted, byte[] key) {
        validateKey(key);
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("加密数据长度非法");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv)
            );
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("敏感数据解密失败", exception);
        }
    }

    private static byte[] configuredKey() {
        String encoded = System.getenv("SENSITIVE_DATA_ENCRYPTION_KEY");
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(encoded.trim());
            validateKey(key);
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "SENSITIVE_DATA_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节密钥",
                    exception
            );
        }
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 密钥必须为 32 字节");
        }
    }
}
