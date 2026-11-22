package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.CompleteWithdrawalRequest;
import com.ptutor.backend.dto.request.RejectWithdrawalRequest;
import com.ptutor.backend.dto.response.AdminWithdrawalResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminWithdrawalService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@RequiredArgsConstructor
@Validated
public class AdminWithdrawalController {

    private static final String BASE = "/api/v1/admin/withdrawals";
    private final AdminWithdrawalService service;
    private final ApiResponseFactory responses;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminWithdrawalResponse>>> findAll(
            @RequestParam(required = false) WithdrawalRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responses.success(service.findAll(status, page, size), BASE));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(responses.success(service.findById(id), BASE + "/" + id));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(responses.success(service.approve(id), BASE + "/" + id + "/approve"));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody RejectWithdrawalRequest request) {
        return ResponseEntity.ok(responses.success(service.reject(id, request.reason()), BASE + "/" + id + "/reject"));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<AdminWithdrawalResponse>> complete(
            @PathVariable UUID id, @Valid @RequestBody CompleteWithdrawalRequest request) {
        return ResponseEntity.ok(responses.success(service.complete(id, request.transferReference()),
                BASE + "/" + id + "/complete"));
    }
}
