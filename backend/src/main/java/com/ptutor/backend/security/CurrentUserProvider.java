package com.ptutor.backend.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.dto.enums.UserRole;

@Component
public class CurrentUserProvider {

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw invalidAuthenticatedUser();
        }

        String subject = jwtAuthentication.getToken().getSubject();
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidAuthenticatedUser();
        }
    }

    public UserRole getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw invalidAuthenticatedUser();
        }

        String role = jwtAuthentication.getToken().getClaimAsString("role");
        try {
            return UserRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidAuthenticatedUser();
        }
    }

    private ApiException invalidAuthenticatedUser() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_AUTHENTICATED_USER",
                "Authenticated user identity is invalid");
    }
}
