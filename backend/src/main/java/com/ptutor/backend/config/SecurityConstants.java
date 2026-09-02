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
            "/api/v1/auth/logout",
            "/api/v1/auth/password-reset/otp",
            "/api/v1/auth/password-reset/verify",
            "/api/v1/auth/password-reset/reset"
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

    public static final String[] TUTOR_SELF_SERVICE_API = {
            "/api/v1/tutors/me",
            "/api/v1/tutors/me/**"
    };

    public static final String[] TUTOR_CERTIFICATE_READ_API = {
            "/api/v1/tutors/*/certificates"
    };

    public static final String[] TUTOR_PROFILE_READ_API = {
            "/api/v1/tutors/*"
    };

    public static final String[] GRADE_READ_API = {
            "/api/v1/grades",
            "/api/v1/grades/**"
    };
    
    public static final String[] TEACHING_REQUEST_READ_API = {
            "/api/v1/teaching-requests",
            "/api/v1/teaching-requests/**"
    };

    public static final String[] SUBJECT_READ_API = {
            "/api/v1/subjects",
            "/api/v1/subjects/**"
    };

    public static final String[] DISTRICT_READ_API = {
            "/api/v1/districts",
            "/api/v1/districts/**"
    };

}
