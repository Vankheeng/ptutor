package com.ptutor.backend.dto.response;

import java.util.UUID;

import com.ptutor.backend.dto.enums.UserRole;

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
