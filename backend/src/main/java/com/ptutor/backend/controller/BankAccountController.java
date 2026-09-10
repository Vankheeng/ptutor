package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.UpdateBankAccountRequest;
import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.BankAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/bank-account")
@RequiredArgsConstructor
@Validated
public class BankAccountController {

    private static final String BANK_ACCOUNT_PATH = "/api/v1/users/me/bank-account";

    private final BankAccountService bankAccountService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> getCurrentBankAccount() {
        return ResponseEntity.ok(responseFactory.success(
                bankAccountService.getCurrentBankAccount(), BANK_ACCOUNT_PATH));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> updateCurrentBankAccount(
            @Valid @RequestBody UpdateBankAccountRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                bankAccountService.updateCurrentBankAccount(request), BANK_ACCOUNT_PATH));
    }
}
