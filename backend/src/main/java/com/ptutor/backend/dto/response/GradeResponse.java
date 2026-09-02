package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.enums.CatalogStatus;

public record GradeResponse(
        UUID id,
        String name,
        Integer level,
        CatalogStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static GradeResponse from(Grade grade) {
        return new GradeResponse(
                grade.getId(),
                grade.getName(),
                grade.getLevel(),
                grade.getStatus(),
                grade.getCreatedAt(),
                grade.getUpdatedAt());
    }
}
