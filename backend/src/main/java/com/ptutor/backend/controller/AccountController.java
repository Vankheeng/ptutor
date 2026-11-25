package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.ChangePasswordRequest;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private static final String CHANGE_PASSWORD_PATH = "/api/v1/users/me/password";

    private final AccountService accountService;
    private final ApiResponseFactory responseFactory;

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.ok(responseFactory.success(
                "PASSWORD_CHANGED",
                "Password changed successfully",
                null,
                CHANGE_PASSWORD_PATH));
    }
}
