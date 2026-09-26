package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record AdminTeachingRequestSummaryResponse(
        UUID id,
        UUID tutorId,
        String tutorName,
        String tutorEmail,
        String subjectName,
        String title,
        String note,
        BigDecimal expectedPrice,
        TeachingMode teachingMode,
        RequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt) {
}
