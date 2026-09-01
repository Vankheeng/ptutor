package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ChangePasswordRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "New password and confirmation do not match");
        }

        UUID currentUserId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_INCORRECT",
                    "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NEW_PASSWORD_MUST_BE_DIFFERENT",
                    "New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
