package com.example.aikb.security;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(
        UUID userId,
        String username,
        String displayName,
        String department,
        String role,
        Instant expiresAt
) {

    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(userId, username, displayName, department, role);
    }
}
