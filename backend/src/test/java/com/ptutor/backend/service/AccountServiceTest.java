package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ptutor.backend.dto.request.ChangePasswordRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CurrentUserProvider currentUserProvider;

    private AccountService accountService;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, passwordEncoder, currentUserProvider);
        userId = UUID.randomUUID();
        user = User.builder().password("old-hash").build();
        user.setId(userId);
    }

    @Test
    void changePasswordHashesAndSavesNewPassword() {
        ChangePasswordRequest request = request("Current123", "NewPassword456", "NewPassword456");
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current123", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword456", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("new-hash");

        accountService.changePassword(request);

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsConfirmationMismatch() {
        assertThatThrownBy(() -> accountService.changePassword(
                request("Current123", "NewPassword456", "Different456")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PASSWORD_CONFIRMATION_MISMATCH"));
        verify(currentUserProvider, never()).getCurrentUserId();
    }

    @Test
    void changePasswordRejectsMissingUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.changePassword(
                request("Current123", "NewPassword456", "NewPassword456")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        stubUser();
        when(passwordEncoder.matches("WrongPassword", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(
                request("WrongPassword", "NewPassword456", "NewPassword456")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CURRENT_PASSWORD_INCORRECT"));
    }

    @Test
    void changePasswordRejectsSamePassword() {
        stubUser();
        when(passwordEncoder.matches("Current123", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("SamePassword123", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> accountService.changePassword(
                request("Current123", "SamePassword123", "SamePassword123")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("NEW_PASSWORD_MUST_BE_DIFFERENT"));
        verify(userRepository, never()).save(user);
    }

    @Test
    void changePasswordRequestMasksPasswordsInLogs() {
        ChangePasswordRequest request = request("Current123", "NewPassword456", "NewPassword456");

        assertThat(request.toString())
                .doesNotContain("Current123")
                .doesNotContain("NewPassword456")
                .contains("***");
    }

    private void stubUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private ChangePasswordRequest request(String current, String password, String confirmation) {
        return new ChangePasswordRequest(current, password, confirmation);
    }
}
