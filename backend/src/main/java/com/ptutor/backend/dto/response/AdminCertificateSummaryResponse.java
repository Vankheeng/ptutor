package com.ptutor.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.CertificateStatus;

public record AdminCertificateSummaryResponse(
        UUID id,
        UUID tutorId,
        String tutorName,
        String tutorEmail,
        String name,
        String issuingOrganization,
        LocalDate issueDate,
        LocalDate expiryDate,
        CertificateStatus status,
        LocalDateTime createdAt) {
}
