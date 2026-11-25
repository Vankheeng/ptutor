package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployeeAccessRequest(
        @NotBlank @Size(min = 10, max = 1000) String reason) {
}
