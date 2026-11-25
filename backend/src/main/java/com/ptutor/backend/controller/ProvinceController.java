package com.ptutor.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.ProvinceResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.ProvinceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/provinces")
@RequiredArgsConstructor
public class ProvinceController {

    private static final String PROVINCES_PATH = "/api/v1/provinces";

    private final ProvinceService provinceService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> findAll() {
        return ResponseEntity.ok(responseFactory.success(provinceService.findAll(), PROVINCES_PATH));
    }
}
