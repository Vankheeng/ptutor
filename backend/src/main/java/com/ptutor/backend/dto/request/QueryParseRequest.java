package com.ptutor.backend.dto.request;

import com.ptutor.backend.entity.enums.RecommendationDirection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QueryParseRequest(
        @NotBlank(message = "Text is required")
        @Size(max = 500, message = "Text must not exceed 500 characters") String text,
        @NotNull(message = "Direction is required") RecommendationDirection direction) {
}
