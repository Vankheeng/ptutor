package com.ptutor.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PasswordResetRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void otpMustContainExactlySixDigits() {
        VerifyPasswordResetOtpRequest request =
                new VerifyPasswordResetOtpRequest("student@example.com", "12345A");

        Set<ConstraintViolation<VerifyPasswordResetOtpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(violation -> violation.getPropertyPath().toString().equals("otp"));
    }

    @Test
    void sensitiveRequestValuesAreMaskedInLogs() {
        VerifyPasswordResetOtpRequest verify =
                new VerifyPasswordResetOtpRequest("student@example.com", "123456");
        ResetPasswordRequest reset = new ResetPasswordRequest(
                "student@example.com", "123456", "NewPassword456", "NewPassword456");

        assertThat(verify.toString()).doesNotContain("123456").contains("***");
        assertThat(reset.toString())
                .doesNotContain("123456", "NewPassword456")
                .contains("***");
    }
}
