package com.ptutor.backend.tutor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        List<TeachingRequestReferenceResponse> grades,
        List<TeachingRequestReferenceResponse> districts,
        String title,
        String note,
        Integer quantity,
        String detailAddress,
        BigDecimal expectedPrice,
        TeachingMode teachingMode,
        String preferredSchedule,
        String description,
        List<TeachingRequestAvailabilityResponse> availabilities,
        RequestStatus status,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TeachingRequestResponse from(
            TeachingRequest request,
            List<TeachingRequestReferenceResponse> grades,
            List<TeachingRequestReferenceResponse> districts,
            List<TeachingRequestAvailabilityResponse> availabilities) {
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
}
