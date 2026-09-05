package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record DistrictResponse(
        UUID id,
        String name,
        UUID provinceId,
        String provinceName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
