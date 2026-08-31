package com.ptutor.backend.response;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp,
        String path) {
}
