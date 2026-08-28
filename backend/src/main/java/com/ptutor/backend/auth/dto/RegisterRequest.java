package com.ptutor.backend.auth.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Pattern(regexp = "STUDENT|TUTOR", message = "Role must be STUDENT or TUTOR") String role,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 20) String gender,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotNull UUID provinceId,
        @NotNull UUID districtId) {
}
