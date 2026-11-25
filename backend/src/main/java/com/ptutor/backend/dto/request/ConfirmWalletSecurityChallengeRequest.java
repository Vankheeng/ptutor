package com.ptutor.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConfirmWalletSecurityChallengeRequest(
        @NotNull UUID challengeId,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
    @Override
    public String toString() {
        return "ConfirmWalletSecurityChallengeRequest[challengeId=" + challengeId + ", otp=***]";
    }
}
