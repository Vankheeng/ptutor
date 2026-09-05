package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.LearningMode;

public record StudentTutorRequestMineResponse(
        UUID id,
        UUID teachingRequestId,
        String teachingRequestTitle,
        UUID subjectId,
        String subjectName,
        String customSubjectName,
        UUID tutorId,
        String tutorFirstName,
        String tutorLastName,
        String tutorEmail,
        UUID gradeId,
        String gradeName,
        BigDecimal proposedPrice,
        LearningMode learningMode,
        String preferredSchedule,
        String message,
        ApplicationStatus status,
        String nextStep,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
