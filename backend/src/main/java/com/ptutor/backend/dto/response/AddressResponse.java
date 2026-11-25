package com.ptutor.backend.dto.response;

import java.util.UUID;

public record AddressResponse(
        String detailAddress,
        UUID districtId,
        String districtName,
        UUID provinceId,
        String provinceName) {
}
