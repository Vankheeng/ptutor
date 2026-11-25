package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.DistrictResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.DistrictService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/districts")
@RequiredArgsConstructor
public class DistrictController {

    private static final String DISTRICTS_PATH = "/api/v1/districts";

    private final DistrictService districtService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> findAll(
            @RequestParam(required = false) UUID provinceId) {
        return ResponseEntity.ok(responseFactory.success(
                districtService.findDistricts(provinceId), DISTRICTS_PATH));
    }
}
