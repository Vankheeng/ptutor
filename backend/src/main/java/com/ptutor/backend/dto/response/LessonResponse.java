package com.ptutor.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.math.BigDecimal;

import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record LessonResponse(
        UUID id,
        UUID contractId,
        String title,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        TeachingMode teachingMode,
        String meetingLink,
        LessonStatus status,
        String note,
        LocalDateTime markedTaughtAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime paidAt,
        RefundSummary refund) {

    public LessonResponse(
            UUID id,
            UUID contractId,
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            TeachingMode teachingMode,
            LessonStatus status,
            String note,
            LocalDateTime markedTaughtAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(id, contractId, title, date, startTime, endTime, teachingMode, null, status, note,
                markedTaughtAt, createdAt, updatedAt, null, null);
    }

    public LessonResponse(
            UUID id,
            UUID contractId,
            String title,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            TeachingMode teachingMode,
            String meetingLink,
            LessonStatus status,
            String note,
            LocalDateTime markedTaughtAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(id, contractId, title, date, startTime, endTime, teachingMode, meetingLink, status, note,
                markedTaughtAt, createdAt, updatedAt, null, null);
    }

    public record RefundSummary(
            UUID id,
            RefundRequestStatus status,
            BigDecimal refundAmount,
            String description,
            LocalDateTime createdAt) {
    }
}
