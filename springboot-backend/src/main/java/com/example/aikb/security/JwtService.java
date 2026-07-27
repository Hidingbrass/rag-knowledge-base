package com.example.aikb.security;

import com.example.aikb.config.JwtProperties;
import com.example.aikb.entity.AppUser;
import com.example.aikb.exception.UnauthorizedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JwtProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String createToken(AppUser user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(properties.expiresInSeconds());

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = Map.of(
                "sub", user.id().toString(),
                "username", user.username(),
                "displayName", user.displayName(),
                "department", user.department(),
                "role", user.role(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        );

        String unsignedToken = base64Url(toJson(header)) + "." + base64Url(toJson(payload));
        return unsignedToken + "." + sign(unsignedToken);
    }

    public JwtClaims parseAndValidate(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("JWT 格式不合法");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new UnauthorizedException("JWT 签名无效");
        }

        JsonNode payload = readPayload(parts[1]);
        Instant expiresAt = Instant.ofEpochSecond(requiredLong(payload, "exp"));
        if (!expiresAt.isAfter(Instant.now(clock))) {
            throw new UnauthorizedException("JWT 已过期");
        }

        return new JwtClaims(
                UUID.fromString(requiredText(payload, "sub")),
                requiredText(payload, "username"),
                requiredText(payload, "displayName"),
                requiredText(payload, "department"),
                requiredText(payload, "role"),
                expiresAt
        );
    }

    private JsonNode readPayload(String encodedPayload) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedPayload);
            return objectMapper.readTree(payloadBytes);
        } catch (Exception exception) {
            throw new UnauthorizedException("JWT 内容解析失败", exception);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new UnauthorizedException("JWT 缺少必要字段: " + fieldName);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.canConvertToLong()) {
            throw new UnauthorizedException("JWT 缺少必要字段: " + fieldName);
        }
        return value.asLong();
    }

    private byte[] toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JWT JSON 序列化失败", exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 签名失败", exception);
        }
    }
}
