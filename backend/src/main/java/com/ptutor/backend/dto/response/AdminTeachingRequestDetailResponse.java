package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record AdminTeachingRequestDetailResponse(
        UUID id,
        UUID tutorId,
        String tutorName,
        String tutorEmail,
        String tutorAvatarUrl,
        UUID subjectId,
        String subjectName,
        String customSubjectName,
        List<Reference> grades,
        List<Reference> districts,
        String title,
        String note,
        Integer quantity,
        String detailAddress,
        BigDecimal expectedPrice,
        TeachingMode teachingMode,
        String preferredSchedule,
        String description,
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
