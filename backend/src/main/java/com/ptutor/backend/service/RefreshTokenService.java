package com.ptutor.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.dto.response.AuthTokenResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.InvalidTokenRepository;
import com.ptutor.backend.entity.InvalidToken;
import com.ptutor.backend.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final InvalidTokenRepository invalidTokenRepository;
    private final JwtService jwtService;
    private final RoleResolver roleResolver;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.jwt.refresh-token-ttl:2592000}")
    private long refreshTokenTtlSeconds;

    @Transactional
    public AuthTokenResponse issue(User user, UserRole role) {
        String rawRefreshToken = generateToken();
        LocalDateTime expiresAt = now().plus(refreshTokenTtlSeconds, ChronoUnit.SECONDS);

        InvalidToken refreshToken = InvalidToken.builder()
                .token(hash(rawRefreshToken))
                .user(user)
                .expiresAt(expiresAt)
                .build();
        invalidTokenRepository.save(refreshToken);

        return new AuthTokenResponse(
                jwtService.createAccessToken(user.getId(), user.getEmail(), role),
                rawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                refreshTokenTtlSeconds,
                user.getId(),
                user.getEmail(),
                role);
    }

    @Transactional
    public AuthTokenResponse refresh(String rawRefreshToken) {
        InvalidToken storedToken = invalidTokenRepository.findByToken(hash(rawRefreshToken))
                .orElseThrow(this::invalidRefreshToken);

        if (storedToken.getRevokedAt() != null) {
            revokeAll(storedToken.getUser().getId());
            throw invalidRefreshToken();
        }

        LocalDateTime now = now();
        if (!storedToken.getExpiresAt().isAfter(now)) {
            invalidTokenRepository.revokeIfNotRevoked(storedToken.getId(), now);
            throw invalidRefreshToken();
        }

        if (invalidTokenRepository.revokeIfActive(storedToken.getId(), now) != 1) {
            revokeAll(storedToken.getUser().getId());
            throw invalidRefreshToken();
        }

        User user = storedToken.getUser();
        UserRole role = roleResolver.resolve(user);
        return issue(user, role);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        invalidTokenRepository.findByToken(hash(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                invalidTokenRepository.revokeIfNotRevoked(token.getId(), now());
            }
        });
    }

    private void revokeAll(java.util.UUID userId) {
        LocalDateTime now = now();
        invalidTokenRepository.findByUser_IdAndRevokedAtIsNull(userId).forEach(token -> {
            token.setRevokedAt(now);
            invalidTokenRepository.save(token);
        });
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
    }
}
