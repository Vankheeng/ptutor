package com.ptutor.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        handler = new GlobalExceptionHandler(responseFactory);
        request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    }

    @Test
    void convertsApiExceptionToStandardErrorResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(
                new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/auth/login");
    }
}
