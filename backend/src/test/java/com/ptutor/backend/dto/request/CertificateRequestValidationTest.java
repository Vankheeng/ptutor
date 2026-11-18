package com.ptutor.backend.dto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CertificateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void expiryMustBeLaterThanIssueDate() {
        LocalDate issueDate = LocalDate.of(2026, 11, 18);

        assertEquals(1, validator.validate(request(issueDate, issueDate)).size());
        assertEquals(1, validator.validate(request(issueDate, issueDate.minusDays(1))).size());
        assertTrue(validator.validate(request(issueDate, issueDate.plusDays(1))).isEmpty());
        assertTrue(validator.validate(request(issueDate, null)).isEmpty());
    }

    private CertificateRequest request(LocalDate issueDate, LocalDate expiryDate) {
        return new CertificateRequest("Chứng chỉ", null, null, issueDate, expiryDate, null);
    }
}
