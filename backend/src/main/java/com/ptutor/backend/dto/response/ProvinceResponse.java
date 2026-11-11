package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProvinceResponse(
        UUID id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
