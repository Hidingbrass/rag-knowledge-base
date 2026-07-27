package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.auth.AuthLoginRequest;
import com.example.aikb.dto.auth.AuthRegisterRequest;
import com.example.aikb.dto.auth.AuthResponse;
import com.example.aikb.dto.auth.AuthUserResponse;
import com.example.aikb.security.AuthenticatedUser;
import com.example.aikb.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(AuthUserResponse.from(currentUser));
    }
}
