package com.example.aikb.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        String displayName,
        String department,
        String role
) {
}
