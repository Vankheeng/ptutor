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
import com.ptutor.backend.dto.request.ConfirmWalletSecurityChallengeRequest;
import com.ptutor.backend.dto.request.RequestWithdrawalOtpRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.dto.response.WalletSecurityChallengeResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.WithdrawalService;
import com.ptutor.backend.service.WalletSecurityService;

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
    private final WalletSecurityService walletSecurityService;
    private final ApiResponseFactory responseFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateWithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(responseFactory.success(
                        "WITHDRAWAL_REQUESTED",
                        "Yêu cầu rút tiền đang chờ duyệt",
                        withdrawalService.create(request, idempotencyKey),
                        WITHDRAWALS_PATH));
    }

    @PostMapping("/otp")
    public ResponseEntity<ApiResponse<WalletSecurityChallengeResponse>> requestOtp(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RequestWithdrawalOtpRequest request) {
        return ResponseEntity.ok(responseFactory.success("WITHDRAWAL_OTP_SENT",
                "Mã xác minh rút tiền đã được gửi", walletSecurityService.requestWithdrawalOtp(request, idempotencyKey),
                WITHDRAWALS_PATH + "/otp"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> confirm(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ConfirmWalletSecurityChallengeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.success("WITHDRAWAL_REQUESTED",
                "Yêu cầu rút tiền đang chờ duyệt",
                walletSecurityService.confirmWithdrawal(request, idempotencyKey), WITHDRAWALS_PATH + "/confirm"));
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
                "Đã hủy yêu cầu rút tiền và hoàn lại tiền vào ví",
                withdrawalService.cancel(withdrawalId),
                WITHDRAWALS_PATH + "/" + withdrawalId + "/cancel"));
    }
}
