package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;

public record AdminStudyingRequestSummaryResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String studentEmail,
        String subjectName,
        String gradeName,
        String title,
        String note,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LearningMode learningMode,
        RequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt) {
}
