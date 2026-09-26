package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.dto.response.AuthTokenResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.InvalidTokenRepository;
import com.ptutor.backend.entity.InvalidToken;
import com.ptutor.backend.entity.User;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock InvalidTokenRepository invalidTokenRepository;
    @Mock JwtService jwtService;
    @Mock RoleResolver roleResolver;
    @Mock UserAccessService userAccessService;

    private RefreshTokenService refreshTokenService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                invalidTokenRepository, jwtService, roleResolver, clock, userAccessService);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenTtlSeconds", 3600L);
        user = User.builder().email("student@example.com").build();
        user.setId(UUID.randomUUID());
        user.setStatus(com.ptutor.backend.entity.enums.UserStatus.ACTIVE);
    }

    @Test
    void refreshRotatesTokenAndRevokesOldToken() {
        String rawToken = "old-refresh-token";
        InvalidToken stored = InvalidToken.builder()
                .token(RefreshTokenService.hash(rawToken))
                .user(user)
                .expiresAt(LocalDateTime.of(2026, 1, 1, 1, 0))
                .build();
        stored.setId(UUID.randomUUID());
        when(invalidTokenRepository.findByToken(RefreshTokenService.hash(rawToken)))
                .thenReturn(Optional.of(stored));
        when(roleResolver.resolve(user)).thenReturn(UserRole.STUDENT);
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtService.createAccessToken(user.getId(), user.getEmail(), UserRole.STUDENT)).thenReturn("access-token");
        when(invalidTokenRepository.revokeIfActive(stored.getId(), LocalDateTime.of(2026, 1, 1, 0, 0)))
                .thenReturn(1);
        when(userAccessService.refreshAccessState(user.getId())).thenReturn(user);

        AuthTokenResponse response = refreshTokenService.refresh(rawToken);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotEqualTo(rawToken);
        verify(invalidTokenRepository).revokeIfActive(stored.getId(), LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(invalidTokenRepository).save(any(InvalidToken.class));
    }

    @Test
    void replayedRefreshTokenRevokesAllActiveTokens() {
        String rawToken = "replayed-token";
        InvalidToken revoked = InvalidToken.builder()
                .token(RefreshTokenService.hash(rawToken))
                .user(user)
                .revokedAt(LocalDateTime.of(2025, 12, 31, 23, 0))
                .expiresAt(LocalDateTime.of(2026, 1, 1, 1, 0))
                .build();
        when(invalidTokenRepository.findByToken(RefreshTokenService.hash(rawToken)))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.refresh(rawToken))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid");
        verify(invalidTokenRepository).revokeAllByUserId(
                user.getId(), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void logoutIsIdempotentForUnknownToken() {
        when(invalidTokenRepository.findByToken(any())).thenReturn(Optional.empty());

        refreshTokenService.logout("unknown-token");

        verify(invalidTokenRepository).findByToken(RefreshTokenService.hash("unknown-token"));
    }

    @Test
    void concurrentRefreshFailureReturnsInvalidTokenAndRevokesActiveTokens() {
        String rawToken = "concurrent-refresh-token";
        InvalidToken stored = InvalidToken.builder()
                .token(RefreshTokenService.hash(rawToken))
                .user(user)
                .expiresAt(LocalDateTime.of(2026, 1, 1, 1, 0))
                .build();
        stored.setId(UUID.randomUUID());
        when(invalidTokenRepository.findByToken(RefreshTokenService.hash(rawToken)))
                .thenReturn(Optional.of(stored));
        when(invalidTokenRepository.revokeIfActive(stored.getId(), LocalDateTime.of(2026, 1, 1, 0, 0)))
                .thenReturn(0);

        assertThatThrownBy(() -> refreshTokenService.refresh(rawToken))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid");
        verify(invalidTokenRepository).revokeIfActive(stored.getId(), LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(invalidTokenRepository).revokeAllByUserId(
                user.getId(), LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
