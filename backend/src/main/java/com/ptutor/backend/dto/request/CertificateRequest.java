package com.ptutor.backend.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CertificateRequest(
        @NotBlank(message = "Certificate name is required")
        @Size(max = 255, message = "Certificate name must not exceed 255 characters")
        String name,

        @Size(max = 255, message = "Issuing organization must not exceed 255 characters")
        String issuingOrganization,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        LocalDate issueDate,
        LocalDate expiryDate,

        @Size(max = 500, message = "Certificate URL must not exceed 500 characters")
        String certificateUrl) {

    @AssertTrue(message = "Expiry date must be on or after issue date")
    public boolean isDateRangeValid() {
        return issueDate == null || expiryDate == null || !expiryDate.isBefore(issueDate);
    }
}
