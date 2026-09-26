package com.ptutor.backend.security;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.UserAccessService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserAccessService userAccessService;
    private final ApiResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = UUID.fromString(jwt.getToken().getSubject());
            User user = userAccessService.refreshAccessState(userId);
            if (user.getStatus() != UserStatus.ACTIVE) {
                writeError(response, request, HttpStatus.FORBIDDEN, user.getStatus() == UserStatus.BLOCKED
                        ? "ACCOUNT_SUSPENDED" : "ACCOUNT_INACTIVE",
                        user.getStatus() == UserStatus.BLOCKED
                                ? "This account is suspended"
                                : "This account is inactive");
                return;
            }
        } catch (IllegalArgumentException exception) {
            writeError(response, request, HttpStatus.UNAUTHORIZED, "INVALID_AUTHENTICATED_USER",
                    "Authenticated user identity is invalid");
            return;
        } catch (ApiException exception) {
            writeError(response, request, exception.getStatus(), exception.getCode(), exception.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                responseFactory.error(code, message, Map.of(), request.getRequestURI()));
    }
}
