package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.CertificateRejectionRequest;
import com.ptutor.backend.dto.response.AdminCertificateDetailResponse;
import com.ptutor.backend.dto.response.AdminCertificateSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminCertificateService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/certificates")
@RequiredArgsConstructor
@Validated
public class AdminCertificateController {

    private static final String BASE_PATH = "/api/v1/admin/certificates";

    private final AdminCertificateService adminCertificateService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminCertificateSummaryResponse>>> findAll(
            @RequestParam(defaultValue = "PENDING") CertificateStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                adminCertificateService.findAll(status, keyword, pageable), BASE_PATH));
    }

    @GetMapping("/{certificateId}")
    public ResponseEntity<ApiResponse<AdminCertificateDetailResponse>> findById(
            @PathVariable UUID certificateId) {
        return ResponseEntity.ok(responseFactory.success(
                adminCertificateService.findById(certificateId), BASE_PATH + "/" + certificateId));
    }

    @PatchMapping("/{certificateId}/approve")
    public ResponseEntity<ApiResponse<AdminCertificateDetailResponse>> approve(
            @PathVariable UUID certificateId) {
        return ResponseEntity.ok(responseFactory.success(
                adminCertificateService.approve(currentUserProvider.getCurrentUserId(), certificateId),
                BASE_PATH + "/" + certificateId + "/approve"));
    }

    @PatchMapping("/{certificateId}/reject")
    public ResponseEntity<ApiResponse<AdminCertificateDetailResponse>> reject(
            @PathVariable UUID certificateId,
            @Valid @RequestBody CertificateRejectionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                adminCertificateService.reject(currentUserProvider.getCurrentUserId(), certificateId,
                        request.rejectionReason()),
                BASE_PATH + "/" + certificateId + "/reject"));
    }
}
