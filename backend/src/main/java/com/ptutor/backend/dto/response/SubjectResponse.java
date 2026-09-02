package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;

public record SubjectResponse(
        UUID id,
        String name,
        String description,
        CatalogStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getDescription(),
                subject.getStatus(),
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }
}
