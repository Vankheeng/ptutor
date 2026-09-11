package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.CreateWithdrawalRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.WithdrawalService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/wallet/withdrawals")
@RequiredArgsConstructor
@Validated
public class WithdrawalController {

    private static final String WITHDRAWALS_PATH = "/api/v1/users/me/wallet/withdrawals";

    private final WithdrawalService withdrawalService;
    private final ApiResponseFactory responseFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateWithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(responseFactory.success(
                        "WITHDRAWAL_REQUESTED",
                        "Withdrawal is pending approval",
                        withdrawalService.create(request, idempotencyKey),
                        WITHDRAWALS_PATH));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalResponse>>> findMine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(responseFactory.success(
                withdrawalService.findMine(page, size),
                WITHDRAWALS_PATH));
    }

    @PatchMapping("/{withdrawalId}/cancel")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> cancel(@PathVariable UUID withdrawalId) {
        return ResponseEntity.ok(responseFactory.success(
                "WITHDRAWAL_CANCELLED",
                "Withdrawal request cancelled and funds returned to the wallet",
                withdrawalService.cancel(withdrawalId),
                WITHDRAWALS_PATH + "/" + withdrawalId + "/cancel"));
    }
}
