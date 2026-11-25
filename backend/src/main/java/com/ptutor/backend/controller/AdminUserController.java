package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.dto.request.ReactivateAccountRequest;
import com.ptutor.backend.dto.request.SuspendAccountRequest;
import com.ptutor.backend.dto.response.AdminUserAccountResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AccountSuspensionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private static final String BASE_PATH = "/api/v1/admin/users";

    private final AccountSuspensionService accountSuspensionService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserAccountResponse>>> findAll(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                accountSuspensionService.findAll(role, status, keyword, pageable), BASE_PATH));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserAccountResponse>> findById(@PathVariable UUID userId) {
        return ResponseEntity.ok(responseFactory.success(
                accountSuspensionService.findById(userId), BASE_PATH + "/" + userId));
    }

    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<AdminUserAccountResponse>> suspend(
            @PathVariable UUID userId,
            @Valid @RequestBody SuspendAccountRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                accountSuspensionService.suspend(currentUserProvider.getCurrentUserId(), userId, request),
                BASE_PATH + "/" + userId + "/suspend"));
    }

    @PatchMapping("/{userId}/reactivate")
    public ResponseEntity<ApiResponse<AdminUserAccountResponse>> reactivate(
            @PathVariable UUID userId,
            @Valid @RequestBody ReactivateAccountRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                accountSuspensionService.reactivate(
                        currentUserProvider.getCurrentUserId(), userId, request.reason()),
                BASE_PATH + "/" + userId + "/reactivate"));
    }
}
