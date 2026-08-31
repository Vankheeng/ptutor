package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.LoginRequest;
import com.ptutor.backend.dto.request.RefreshTokenRequest;
import com.ptutor.backend.dto.request.RegisterRequest;
import com.ptutor.backend.dto.response.AuthTokenResponse;
import com.ptutor.backend.dto.response.RegisterResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AuthService;
import com.ptutor.backend.service.RefreshTokenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final ApiResponseFactory responseFactory;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201)
                .body(responseFactory.success(authService.register(request), "/api/v1/auth/register"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(responseFactory.success(authService.login(request), "/api/v1/auth/login"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                refreshTokenService.refresh(request.refreshToken()), "/api/v1/auth/refresh"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.logout(request.refreshToken());
        return ResponseEntity.ok(responseFactory.success(
                "LOGOUT_SUCCESS", "Logout completed successfully", null, "/api/v1/auth/logout"));
    }
}
