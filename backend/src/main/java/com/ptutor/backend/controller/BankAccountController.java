package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.UpdateBankAccountRequest;
import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.dto.request.ConfirmWalletSecurityChallengeRequest;
import com.ptutor.backend.dto.response.WalletSecurityChallengeResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.WalletSecurityService;
import com.ptutor.backend.service.BankAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/bank-account")
@RequiredArgsConstructor
@Validated
public class BankAccountController {

    private static final String BANK_ACCOUNT_PATH = "/api/v1/users/me/bank-account";

    private final WalletSecurityService walletSecurityService;
    private final BankAccountService bankAccountService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> getCurrentBankAccount() {
        return ResponseEntity.ok(responseFactory.success(
                bankAccountService.getCurrentBankAccount(), BANK_ACCOUNT_PATH));
    }

    @PostMapping("/otp")
    public ResponseEntity<ApiResponse<WalletSecurityChallengeResponse>> requestUpdateOtp(
            @Valid @RequestBody UpdateBankAccountRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "BANK_ACCOUNT_OTP_SENT", "Mã xác minh tài khoản ngân hàng đã được gửi",
                walletSecurityService.requestBankAccountOtp(request), BANK_ACCOUNT_PATH + "/otp"));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<BankAccountResponse>> confirmUpdate(
            @Valid @RequestBody ConfirmWalletSecurityChallengeRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "BANK_ACCOUNT_UPDATED", "Đã cập nhật tài khoản ngân hàng", walletSecurityService.confirmBankAccount(request),
                BANK_ACCOUNT_PATH + "/confirm"));
    }
}
