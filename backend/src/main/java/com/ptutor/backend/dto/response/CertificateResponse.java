package com.ptutor.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.CertificateStatus;

public record CertificateResponse(
        UUID id,
        UUID tutorId,
        String name,
        String issuingOrganization,
        String description,
        LocalDate issueDate,
        LocalDate expiryDate,
        String certificateUrl,
        CertificateStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
