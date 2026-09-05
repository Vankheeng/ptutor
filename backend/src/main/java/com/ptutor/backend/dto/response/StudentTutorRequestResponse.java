package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.LearningMode;

public record StudentTutorRequestResponse(
        UUID id,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String studentEmail,
        UUID gradeId,
        String gradeName,
        UUID teachingRequestId,
        BigDecimal proposedPrice,
        LearningMode learningMode,
        String preferredSchedule,
        String message,
        ApplicationStatus status,
        String nextStep,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
