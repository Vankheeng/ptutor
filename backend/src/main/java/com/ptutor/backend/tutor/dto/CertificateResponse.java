package com.ptutor.backend.tutor.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.Certificate;
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

    public static CertificateResponse from(Certificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getTutor().getId(),
                certificate.getName(),
                certificate.getIssuingOrganization(),
                certificate.getDescription(),
                certificate.getIssueDate(),
                certificate.getExpiryDate(),
                certificate.getCertificateUrl(),
                certificate.getStatus(),
                certificate.getCreatedAt(),
                certificate.getUpdatedAt());
    }
}
