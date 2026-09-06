package com.ptutor.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.CertificateStatus;

public record AdminCertificateDetailResponse(
        UUID id,
        UUID tutorId,
        String tutorName,
        String tutorEmail,
        String tutorAvatarUrl,
        String name,
        String issuingOrganization,
        String description,
        LocalDate issueDate,
        LocalDate expiryDate,
        String certificateUrl,
        CertificateStatus status,
        String rejectionReason,
        CertificateReviewerResponse reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
