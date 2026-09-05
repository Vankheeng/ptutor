package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record TutorStudentRequestResponse(
        UUID id,
        UUID tutorId,
        String tutorFirstName,
        String tutorLastName,
        String tutorEmail,
        UUID gradeId,
        String gradeName,
        UUID studyingRequestId,
        BigDecimal proposedPrice,
        TeachingMode teachingMode,
        String preferredSchedule,
        String message,
        ApplicationStatus status,
        String nextStep,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
