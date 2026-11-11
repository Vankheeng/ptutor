package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.RefundRequestStatus;

public record AdminRefundRequestSummaryResponse(
        UUID id,
        UUID studentUserId,
        String studentName,
        String studentEmail,
        UUID contractId,
        String subjectName,
        String gradeName,
        String tutorName,
        RefundRequestStatus status,
        long disputedLessonCount,
        boolean awaitingTutorResponse,
        boolean hasOpenComplaint,
        UUID assignedEmployeeId,
        String assignedEmployeeName,
        LocalDateTime createdAt) {
}
