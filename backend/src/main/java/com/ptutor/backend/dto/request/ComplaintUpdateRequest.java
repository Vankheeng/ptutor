package com.ptutor.backend.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintUpdateRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Content is required")
        @Size(max = 10000, message = "Content must not exceed 10000 characters")
        String content,

        List<ComplaintCreateRequest.@Valid Evidence> evidences) {
}
