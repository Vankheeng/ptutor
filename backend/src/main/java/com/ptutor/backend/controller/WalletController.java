package com.ptutor.backend.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WalletBalanceResponse;
import com.ptutor.backend.dto.response.WalletTransactionResponse;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.entity.enums.WalletTransactionStatus;
import com.ptutor.backend.entity.enums.WalletTransactionType;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.WalletService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/wallet")
@RequiredArgsConstructor
@Validated
public class WalletController {

    private static final String WALLET_PATH = "/api/v1/users/me/wallet";

    private final WalletService walletService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getCurrentBalance() {
        return ResponseEntity.ok(responseFactory.success(walletService.getCurrentBalance(), WALLET_PATH));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getTransactions(
            @RequestParam(required = false) WalletTransactionType transactionType,
            @RequestParam(required = false) WalletTransactionPurpose purpose,
            @RequestParam(required = false) WalletTransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responseFactory.success(
                walletService.getMyTransactions(
                        transactionType, purpose, status, createdFrom, createdTo, page, size),
                WALLET_PATH + "/transactions"));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> getTransaction(
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(responseFactory.success(
                walletService.getMyTransaction(transactionId),
                WALLET_PATH + "/transactions/" + transactionId));
    }
}
