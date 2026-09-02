package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.District;

public record DistrictResponse(
        UUID id,
        String name,
        UUID provinceId,
        String provinceName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DistrictResponse from(District district) {
        return new DistrictResponse(
                district.getId(),
                district.getName(),
                district.getProvince().getId(),
                district.getProvince().getName(),
                district.getCreatedAt(),
                district.getUpdatedAt());
    }
}
