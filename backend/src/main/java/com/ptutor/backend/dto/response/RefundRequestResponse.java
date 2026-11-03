package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LessonOutcome;
import com.ptutor.backend.entity.enums.RefundRequestStatus;

public record RefundRequestResponse(
        UUID id,
        UUID contractId,
        String subjectName,
        String gradeName,
        UUID studentUserId,
        String studentName,
        UUID tutorUserId,
        String tutorName,
        RefundRequestStatus status,
        String description,
        List<DisputedLesson> disputedLessons,
        List<Evidence> evidences,
        String tutorResponse,
        LocalDateTime tutorRespondedAt,
        LocalDateTime tutorResponseDeadline,
        BigDecimal refundAmount,
        BigDecimal tutorShareAmount,
        String rejectionReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record DisputedLesson(
            UUID lessonId,
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            LocalDateTime markedTaughtAt,
            LessonOutcome claimedOutcome,
            LessonOutcome tutorPosition,
            LessonOutcome decision) {
    }

    public record Evidence(UUID id, String fileUrl, String fileType, UUID uploadedByUserId) {
    }
}
