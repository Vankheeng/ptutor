package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record TutorStudentRequestResponse(
        UUID id,
        UUID tutorId,
        UUID studyingRequestId,
        String studyingRequestTitle,
        UUID subjectId,
        String subjectName,
        UUID gradeId,
        String gradeName,
        BigDecimal proposedPrice,
        TeachingMode teachingMode,
        String preferredSchedule,
        String message,
        ApplicationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
