package com.example.aikb.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveTextConverterTests {

    private final byte[] key = Arrays.copyOf(
            "test-key-material-for-aes-256".getBytes(StandardCharsets.UTF_8),
            32
    );

    @Test
    void encryptShouldUseRandomIvAndRoundTripChineseText() {
        String plaintext = "简历：手机号 13812345678，项目为 RAG。";

        String first = SensitiveTextConverter.encrypt(plaintext, key);
        String second = SensitiveTextConverter.encrypt(plaintext, key);

        assertThat(first).startsWith("enc:v1:");
        assertThat(second).isNotEqualTo(first);
        assertThat(SensitiveTextConverter.decrypt(first, key)).isEqualTo(plaintext);
        assertThat(SensitiveTextConverter.decrypt(second, key)).isEqualTo(plaintext);
    }

    @Test
    void decryptShouldFailWhenCiphertextWasTamperedWith() {
        String encrypted = SensitiveTextConverter.encrypt("private resume", key);
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThatThrownBy(() -> SensitiveTextConverter.decrypt(tampered, key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }
}
