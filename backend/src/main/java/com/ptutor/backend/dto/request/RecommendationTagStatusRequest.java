package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.RecommendationTagStatus;

import jakarta.validation.constraints.NotNull;

public record RecommendationTagStatusRequest(
        @NotNull(message = "Status is required") RecommendationTagStatus status) {
}
