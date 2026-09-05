package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.CatalogStatus;

public record SubjectResponse(
        UUID id,
        String name,
        String description,
        CatalogStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
