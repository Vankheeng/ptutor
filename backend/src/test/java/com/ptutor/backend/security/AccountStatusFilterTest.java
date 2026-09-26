package com.ptutor.backend.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.UserAccessService;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AccountStatusFilterTest {

    @Mock UserAccessService userAccessService;
    @Mock ObjectMapper objectMapper;
    @Mock FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksExistingAccessTokenWhenAccountIsSuspended() throws Exception {
        UUID userId = authenticate();
        User user = User.builder().status(UserStatus.BLOCKED).build();
        user.setId(userId);
        when(userAccessService.refreshAccessState(userId)).thenReturn(user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(new MockHttpServletRequest("GET", "/api/v1/students/me"), response, filterChain);

        verify(filterChain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsActiveAccount() throws Exception {
        UUID userId = authenticate();
        User user = User.builder().status(UserStatus.ACTIVE).build();
        user.setId(userId);
        when(userAccessService.refreshAccessState(userId)).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        return userId;
    }

    private AccountStatusFilter filter() {
        return new AccountStatusFilter(
                userAccessService,
                new ApiResponseFactory(Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC)),
                objectMapper);
    }
}
