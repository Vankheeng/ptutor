package com.ptutor.backend.common.response;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ApiResponseFactory {

    private final Clock clock;

    public <T> ApiResponse<T> success(T data, String path) {
        return new ApiResponse<>(true, "SUCCESS", "Request processed successfully", data, Map.of(), now(), path);
    }

    public <T> ApiResponse<T> success(String code, String message, T data, String path) {
        return new ApiResponse<>(true, code, message, data, Map.of(), now(), path);
    }

    public ApiResponse<Void> error(String code, String message, Map<String, String> errors, String path) {
        return new ApiResponse<>(false, code, message, null, errors, now(), path);
    }

    private Instant now() {
        return clock.instant();
    }
}
