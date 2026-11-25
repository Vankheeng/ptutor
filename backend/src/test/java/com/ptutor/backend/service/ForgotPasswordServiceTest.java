package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ptutor.backend.dto.request.ResetPasswordRequest;
import com.ptutor.backend.dto.request.SendPasswordResetOtpRequest;
import com.ptutor.backend.dto.request.VerifyPasswordResetOtpRequest;
import com.ptutor.backend.entity.ForgotPassword;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ForgotPasswordRepository;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.security.OtpGenerator;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-01T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    private static final String EMAIL = "student@example.com";
    private static final String OTP = "123456";

    @Mock UserRepository userRepository;
    @Mock ForgotPasswordRepository forgotPasswordRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OtpGenerator otpGenerator;
    @Mock EmailService emailService;

    private ForgotPasswordService service;
    private User user;
    private ForgotPassword passwordReset;

    @BeforeEach
    void setUp() {
        service = new ForgotPasswordService(
                userRepository,
                forgotPasswordRepository,
                passwordEncoder,
                otpGenerator,
                emailService,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        user = User.builder().email(EMAIL).password("old-hash").build();
        user.setId(UUID.randomUUID());
        passwordReset = ForgotPassword.builder()
                .user(user)
                .otp("otp-hash")
                .expiresAt(NOW.plusMinutes(1))
                .build();
        passwordReset.setId(UUID.randomUUID());
    }

    @Test
    void sendOtpInvalidatesOldTokensStoresOnlyHashAndEmailsRawOtp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(otpGenerator.generate()).thenReturn(OTP);
        when(passwordEncoder.encode(OTP)).thenReturn("bcrypt-otp-hash");

        service.sendOtp(new SendPasswordResetOtpRequest("  STUDENT@EXAMPLE.COM "));

        verify(forgotPasswordRepository).invalidateAllUnusedByUserId(user.getId(), NOW);
        ArgumentCaptor<ForgotPassword> captor = ArgumentCaptor.forClass(ForgotPassword.class);
        verify(forgotPasswordRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOtp()).isEqualTo("bcrypt-otp-hash").isNotEqualTo(OTP);
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        verify(emailService).sendOtp(EMAIL, OTP);
    }

    @Test
    void sendOtpRejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertCode(() -> service.sendOtp(new SendPasswordResetOtpRequest(EMAIL)),
                HttpStatus.NOT_FOUND, "EMAIL_NOT_FOUND");
        verify(forgotPasswordRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendOtpPropagatesEmailDeliveryFailureForTransactionRollback() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(otpGenerator.generate()).thenReturn(OTP);
        when(passwordEncoder.encode(OTP)).thenReturn("otp-hash");
        org.mockito.Mockito.doThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED", "Unable to send email"))
                .when(emailService).sendOtp(EMAIL, OTP);

        assertCode(() -> service.sendOtp(new SendPasswordResetOtpRequest(EMAIL)),
                HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED");
    }

    @Test
    void verifyValidOtpDoesNotConsumeIt() {
        stubValidOtp();

        assertThat(service.verifyOtp(new VerifyPasswordResetOtpRequest(EMAIL, OTP)).valid()).isTrue();

        verify(forgotPasswordRepository, never()).consumeIfActive(any(), any());
    }

    @Test
    void verifyRejectsWrongOtp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(forgotPasswordRepository.findFirstByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(passwordReset));
        when(passwordEncoder.matches("999999", "otp-hash")).thenReturn(false);

        assertCode(() -> service.verifyOtp(new VerifyPasswordResetOtpRequest(EMAIL, "999999")),
                HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_OTP");
    }

    @Test
    void verifyRejectsExpiredOtpWithoutCheckingHash() {
        passwordReset.setExpiresAt(NOW);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(forgotPasswordRepository.findFirstByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(passwordReset));

        assertCode(() -> service.verifyOtp(new VerifyPasswordResetOtpRequest(EMAIL, OTP)),
                HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_OTP");
        verify(passwordEncoder, never()).matches(OTP, "otp-hash");
    }

    @Test
    void verifyRejectsUsedOrMissingOtp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(forgotPasswordRepository.findFirstByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.empty());

        assertCode(() -> service.verifyOtp(new VerifyPasswordResetOtpRequest(EMAIL, OTP)),
                HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_OTP");
    }

    @Test
    void resetConsumesOtpAndHashesNewPassword() {
        stubValidOtp();
        when(passwordEncoder.matches("NewPassword456", "old-hash")).thenReturn(false);
        when(forgotPasswordRepository.consumeIfActive(passwordReset.getId(), NOW)).thenReturn(1);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("new-hash");

        service.resetPassword(resetRequest(OTP, "NewPassword456", "NewPassword456"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(forgotPasswordRepository).invalidateAllUnusedByUserId(user.getId(), NOW);
    }

    @Test
    void resetRejectsConfirmationMismatchBeforeDatabaseAccess() {
        assertCode(() -> service.resetPassword(resetRequest(OTP, "NewPassword456", "Different456")),
                HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH");
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void resetRejectsPasswordMatchingCurrentPassword() {
        stubValidOtp();
        when(passwordEncoder.matches("SamePassword123", "old-hash")).thenReturn(true);

        assertCode(() -> service.resetPassword(resetRequest(OTP, "SamePassword123", "SamePassword123")),
                HttpStatus.BAD_REQUEST, "NEW_PASSWORD_MUST_BE_DIFFERENT");
        verify(forgotPasswordRepository, never()).consumeIfActive(any(), any());
    }

    @Test
    void atomicConsumeFailureRejectsConcurrentResetOrReplay() {
        stubValidOtp();
        when(passwordEncoder.matches("NewPassword456", "old-hash")).thenReturn(false);
        when(forgotPasswordRepository.consumeIfActive(passwordReset.getId(), NOW)).thenReturn(0);

        assertCode(() -> service.resetPassword(resetRequest(OTP, "NewPassword456", "NewPassword456")),
                HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_OTP");
        verify(userRepository, never()).save(any());
    }

    private void stubValidOtp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(forgotPasswordRepository.findFirstByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(passwordReset));
        when(passwordEncoder.matches(OTP, "otp-hash")).thenReturn(true);
    }

    private ResetPasswordRequest resetRequest(String otp, String password, String confirmation) {
        return new ResetPasswordRequest(EMAIL, otp, password, confirmation);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            HttpStatus status, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(status);
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }
}
