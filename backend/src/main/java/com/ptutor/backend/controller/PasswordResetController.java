package com.ptutor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.ResetPasswordRequest;
import com.ptutor.backend.dto.request.SendPasswordResetOtpRequest;
import com.ptutor.backend.dto.request.VerifyPasswordResetOtpRequest;
import com.ptutor.backend.dto.response.OtpVerificationResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.ForgotPasswordService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
@Validated
public class PasswordResetController {

    private static final String OTP_PATH = "/api/v1/auth/password-reset/otp";
    private static final String VERIFY_PATH = "/api/v1/auth/password-reset/verify";
    private static final String RESET_PATH = "/api/v1/auth/password-reset/reset";

    private final ForgotPasswordService forgotPasswordService;
    private final ApiResponseFactory responseFactory;

    @PostMapping("/otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @Valid @RequestBody SendPasswordResetOtpRequest request) {
        forgotPasswordService.sendOtp(request);
        return ResponseEntity.ok(responseFactory.success(
                "OTP_SENT",
                "Password reset OTP sent successfully",
                null,
                OTP_PATH));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> verifyOtp(
            @Valid @RequestBody VerifyPasswordResetOtpRequest request) {
        OtpVerificationResponse response = forgotPasswordService.verifyOtp(request);
        return ResponseEntity.ok(responseFactory.success(
                "OTP_VERIFIED",
                "OTP verified successfully",
                response,
                VERIFY_PATH));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        forgotPasswordService.resetPassword(request);
        return ResponseEntity.ok(responseFactory.success(
                "PASSWORD_RESET",
                "Password reset successfully",
                null,
                RESET_PATH));
    }
}
