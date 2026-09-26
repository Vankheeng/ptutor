package com.ptutor.backend.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String initialPassword,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 20) String phone,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotNull Gender gender,
        @NotBlank @Pattern(regexp = "\\d{12}", message = "Citizen ID must contain exactly 12 digits")
        String citizenId,
        @NotNull UUID provinceId,
        @NotNull UUID districtId,
        @Size(max = 255) String detailAddress,
        @NotNull EmployeeJobFunction jobFunction) {
}
