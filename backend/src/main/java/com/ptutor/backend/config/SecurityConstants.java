package com.ptutor.backend.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityConstants {

    /**
     * Endpoints that can be called without an access-token JWT.
     *
     * Refresh and logout still authenticate the caller by validating the
     * refresh token in the request body at the service layer.
     */
    public static final String[] API_PUBLIC = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    public static final String[] API_DOCUMENTATION = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/swagger-ui/**",
            "/openapi.yaml"
    };

    public static final String[] STUDENT_SELF_SERVICE_API = {
            "/api/v1/students/me",
            "/api/v1/students/me/**"
    };
}
