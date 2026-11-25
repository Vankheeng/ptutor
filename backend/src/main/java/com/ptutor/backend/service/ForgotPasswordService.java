package com.ptutor.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ResetPasswordRequest;
import com.ptutor.backend.dto.request.SendPasswordResetOtpRequest;
import com.ptutor.backend.dto.request.VerifyPasswordResetOtpRequest;
import com.ptutor.backend.dto.response.OtpVerificationResponse;
import com.ptutor.backend.entity.ForgotPassword;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ForgotPasswordRepository;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.security.OtpGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final ForgotPasswordRepository forgotPasswordRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpGenerator otpGenerator;
    private final EmailService emailService;
    private final Clock clock;

    @Transactional
    public void sendOtp(SendPasswordResetOtpRequest request) {
        User user = findUser(request.email());
        LocalDateTime now = now();

        forgotPasswordRepository.invalidateAllUnusedByUserId(user.getId(), now);

        String rawOtp = otpGenerator.generate();
        ForgotPassword passwordReset = ForgotPassword.builder()
                .user(user)
                .otp(passwordEncoder.encode(rawOtp))
                .expiresAt(now.plus(OTP_TTL))
                .build();
        forgotPasswordRepository.saveAndFlush(passwordReset);

        emailService.sendOtp(user.getEmail(), rawOtp);
    }

    @Transactional(readOnly = true)
    public OtpVerificationResponse verifyOtp(VerifyPasswordResetOtpRequest request) {
        User user = findUser(request.email());
        validateOtp(user, request.otp(), now());
        return new OtpVerificationResponse(true);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "New password and confirmation do not match");
        }

        User user = findUser(request.email());
        LocalDateTime now = now();
        ForgotPassword passwordReset = validateOtp(user, request.otp(), now);

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NEW_PASSWORD_MUST_BE_DIFFERENT",
                    "New password must be different from the current password");
        }

        if (forgotPasswordRepository.consumeIfActive(passwordReset.getId(), now) != 1) {
            throw invalidOtp();
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        forgotPasswordRepository.invalidateAllUnusedByUserId(user.getId(), now);
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EMAIL_NOT_FOUND",
                        "No account is registered with this email"));
    }

    private ForgotPassword validateOtp(User user, String rawOtp, LocalDateTime now) {
        ForgotPassword passwordReset = forgotPasswordRepository
                .findFirstByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(ForgotPasswordService::invalidOtp);

        if (!passwordReset.getExpiresAt().isAfter(now)
                || !passwordEncoder.matches(rawOtp, passwordReset.getOtp())) {
            throw invalidOtp();
        }
        return passwordReset;
    }

    private static ApiException invalidOtp() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_OR_EXPIRED_OTP",
                "OTP is invalid, expired, or has already been used");
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
