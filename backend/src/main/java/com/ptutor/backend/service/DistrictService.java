package com.ptutor.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.DistrictResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictRepository districtRepository;
    private final ProvinceRepository provinceRepository;

    @Transactional(readOnly = true)
    public List<DistrictResponse> findDistricts(UUID provinceId) {
        if (provinceId == null) {
            return districtRepository.findAllOrdered().stream()
                    .map(DistrictResponse::from)
                    .toList();
        }

        provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_PROVINCE", "Province not found"));

        return districtRepository.findAllByProvinceIdOrdered(provinceId).stream()
                .map(DistrictResponse::from)
                .toList();
    }
}
