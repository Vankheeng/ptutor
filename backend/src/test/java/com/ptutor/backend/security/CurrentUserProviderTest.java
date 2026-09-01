package com.ptutor.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ptutor.backend.exception.ApiException;

class CurrentUserProviderTest {

    private final CurrentUserProvider currentUserProvider = new CurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserIdReturnsJwtSubject() {
        UUID userId = UUID.randomUUID();
        setAuthentication(userId.toString());

        assertThat(currentUserProvider.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    void getCurrentUserIdRejectsMissingAuthentication() {
        assertThatThrownBy(currentUserProvider::getCurrentUserId)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_AUTHENTICATED_USER"));
    }

    @Test
    void getCurrentUserIdRejectsMalformedSubject() {
        setAuthentication("not-a-uuid");

        assertThatThrownBy(currentUserProvider::getCurrentUserId)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_AUTHENTICATED_USER"));
    }

    private void setAuthentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        SecurityContextHolder.setContext(context);
    }
}
