package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID contractId,
        UUID tutorId,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {
}
