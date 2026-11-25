package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;

public record AdminStudyingRequestDetailResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String studentEmail,
        String studentAvatarUrl,
        UUID subjectId,
        String subjectName,
        Reference grade,
        Reference district,
        String title,
        String description,
        String note,
        String detailAddress,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String learningGoals,
        LearningMode learningMode,
        String preferredSchedule,
        List<Availability> availabilities,
        RequestStatus status,
        Reviewer reviewer,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Reference(UUID id, String name) {
    }

    public record Availability(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    public record Reviewer(UUID id, String name) {
    }
}
