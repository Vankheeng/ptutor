package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The code of a tag never changes: logs and saved searches refer to it. */
public record RecommendationTagUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must not exceed 120 characters") String name,
        @Size(max = 500, message = "Description must not exceed 500 characters") String description,
        /** Null removes the parent. */
        String parentCode) {
}
