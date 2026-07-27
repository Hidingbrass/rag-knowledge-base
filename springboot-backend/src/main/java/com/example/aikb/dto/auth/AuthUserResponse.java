package com.example.aikb.dto.auth;

import com.example.aikb.entity.AppUser;
import com.example.aikb.security.AuthenticatedUser;

import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String displayName,
        String department,
        String role
) {

    public static AuthUserResponse from(AppUser user) {
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.department(),
                user.role()
        );
    }

    public static AuthUserResponse from(AuthenticatedUser user) {
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.department(),
                user.role()
        );
    }
}
