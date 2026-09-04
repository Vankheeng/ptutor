package com.ptutor.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

public record TeachingRequestResponse(
        UUID id,
        UUID tutorId,
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
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TeachingRequestResponse from(
            TeachingRequest request,
            List<Reference> grades,
            List<Reference> districts,
            List<Availability> availabilities) {
        return new TeachingRequestResponse(
                request.getId(),
                request.getTutor().getId(),
                request.getSubject() == null ? null : request.getSubject().getId(),
                request.getSubject() == null ? null : request.getSubject().getName(),
                request.getCustomSubjectName(),
                grades,
                districts,
                request.getTitle(),
                request.getNote(),
                request.getQuantity(),
                request.getDetailAddress(),
                request.getExpectedPrice(),
                request.getTeachingMode(),
                request.getPreferredSchedule(),
                request.getDescription(),
                availabilities,
                request.getStatus(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                request.getReviewedAt(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    public record Reference(UUID id, String name) {
    }

    public record Availability(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
