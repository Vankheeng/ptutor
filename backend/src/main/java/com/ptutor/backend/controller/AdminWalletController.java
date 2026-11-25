package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.AdminHeldTuitionResponse;
import com.ptutor.backend.dto.response.AdminContractTransactionResponse;
import com.ptutor.backend.dto.response.AdminWalletResponse;
import com.ptutor.backend.dto.response.AdminWalletSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminWalletService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/wallets")
@RequiredArgsConstructor
@Validated
public class AdminWalletController {
    private static final String BASE = "/api/v1/admin/wallets";
    private final AdminWalletService service;
    private final ApiResponseFactory responses;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminWalletSummaryResponse>> summary() {
        return ResponseEntity.ok(responses.success(service.summary(), BASE + "/summary"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminWalletResponse>>> wallets(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responses.success(service.wallets(search, page, size), BASE));
    }

    @GetMapping("/tuition")
    public ResponseEntity<ApiResponse<PageResponse<AdminHeldTuitionResponse>>> tuition(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responses.success(service.tuition(search, page, size), BASE + "/tuition"));
    }

    @GetMapping("/contracts/{contractId}/transactions")
    public ResponseEntity<ApiResponse<PageResponse<AdminContractTransactionResponse>>> contractTransactions(
            @PathVariable UUID contractId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responses.success(service.contractTransactions(contractId, page, size),
                BASE + "/contracts/" + contractId + "/transactions"));
    }
}
