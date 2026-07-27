package com.example.aikb.service;

import com.example.aikb.config.JwtProperties;
import com.example.aikb.dto.auth.AuthLoginRequest;
import com.example.aikb.dto.auth.AuthRegisterRequest;
import com.example.aikb.dto.auth.AuthResponse;
import com.example.aikb.dto.auth.AuthUserResponse;
import com.example.aikb.entity.AppUser;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.UnauthorizedException;
import com.example.aikb.repository.AppUserRepository;
import com.example.aikb.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    public AuthResponse register(AuthRegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (appUserRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }

        AppUser user = appUserRepository.save(new AppUser(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                request.department().trim(),
                DEFAULT_ROLE,
                Instant.now()
        ));

        return toAuthResponse(user);
    }

    public AuthResponse login(AuthLoginRequest request) {
        String username = normalizeUsername(request.username());
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(AppUser user) {
        return new AuthResponse(
                "Bearer",
                jwtService.createToken(user),
                jwtProperties.expiresInSeconds(),
                AuthUserResponse.from(user)
        );
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
