package com.ptutor.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.ProvinceResponse;
import com.ptutor.backend.repository.ProvinceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProvinceService {

    private final ProvinceRepository provinceRepository;

    @Transactional(readOnly = true)
    public List<ProvinceResponse> findAll() {
        return provinceRepository.findAllByOrderByNameAsc().stream()
                .map(province -> new ProvinceResponse(
                        province.getId(),
                        province.getName(),
                        province.getCreatedAt(),
                        province.getUpdatedAt()))
                .toList();
    }
}
