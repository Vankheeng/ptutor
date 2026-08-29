package com.ptutor.backend.auth.dto;

import java.util.UUID;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        UUID userId,
        String email,
        UserRole role) {
}
