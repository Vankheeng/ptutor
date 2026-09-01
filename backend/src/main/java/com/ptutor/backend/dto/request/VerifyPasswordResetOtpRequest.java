package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyPasswordResetOtpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must contain exactly 6 digits") String otp) {

    @Override
    public String toString() {
        return "VerifyPasswordResetOtpRequest[email=" + email + ", otp=***]";
    }
}
