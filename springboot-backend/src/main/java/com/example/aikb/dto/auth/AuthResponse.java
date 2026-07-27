package com.example.aikb.dto.auth;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        AuthUserResponse user
) {
}
