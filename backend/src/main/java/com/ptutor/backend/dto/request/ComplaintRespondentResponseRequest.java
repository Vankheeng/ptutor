package com.ptutor.backend.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintRespondentResponseRequest(
        @NotBlank(message = "Response is required")
        @Size(max = 5000, message = "Response must not exceed 5000 characters")
        String response,
        @Size(max = 5, message = "At most 5 evidences can be submitted at once")
        List<ComplaintCreateRequest.@Valid Evidence> evidences) {
}
