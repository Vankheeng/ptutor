package com.ptutor.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VnPayPaymentRequest(
        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "Bank code must be alphanumeric")
        @Size(max = 20, message = "Bank code must not exceed 20 characters")
        String bankCode,

        @Pattern(regexp = "^(vn|en)?$", message = "Locale must be vn or en")
        String locale) {
}
