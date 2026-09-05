package com.ptutor.backend.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintCreateRequest(
        @NotNull(message = "Contract ID is required")
        UUID contractId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Content is required")
        @Size(max = 10000, message = "Content must not exceed 10000 characters")
        String content,

        List<@Valid Evidence> evidences) {

    public record Evidence(
            @NotBlank(message = "Evidence file URL is required")
            @Size(max = 2048, message = "Evidence file URL must not exceed 2048 characters")
            String fileUrl,

            @NotBlank(message = "Evidence file type is required")
            @Size(max = 100, message = "Evidence file type must not exceed 100 characters")
            String fileType) {
    }
}
