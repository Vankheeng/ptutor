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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.ContractTermsRequest;
import com.ptutor.backend.dto.request.ContractUpdateRequest;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ContractService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private static final String BASE_PATH = "/api/v1/users/me/contracts";

    private final ContractService contractService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ContractResponse>>> findMine(
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(responseFactory.success(
                contractService.findMine(currentUserProvider.getCurrentUserId(), status, pageable),
                BASE_PATH));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ApiResponse<ContractResponse>> findMineById(@PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                contractService.findMineById(currentUserProvider.getCurrentUserId(), contractId),
                BASE_PATH + "/" + contractId));
    }

    @PatchMapping("/{contractId}")
    public ResponseEntity<ApiResponse<ContractResponse>> update(
            @PathVariable UUID contractId,
            @Valid @RequestBody ContractUpdateRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                "CONTRACT_UPDATED",
                "Contract updated successfully",
                contractService.update(currentUserProvider.getCurrentUserId(), contractId, request),
                BASE_PATH + "/" + contractId));
    }

    @PatchMapping("/{contractId}/sign")
    public ResponseEntity<ApiResponse<ContractResponse>> sign(@PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                "CONTRACT_SIGNED",
                "Contract signed and activated successfully",
                contractService.sign(currentUserProvider.getCurrentUserId(), contractId),
                BASE_PATH + "/" + contractId + "/sign"));
    }

    @PatchMapping("/{contractId}/reject")
    public ResponseEntity<ApiResponse<ContractResponse>> reject(@PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                "CONTRACT_REJECTED",
                "Contract rejected successfully",
                contractService.reject(currentUserProvider.getCurrentUserId(), contractId),
                BASE_PATH + "/" + contractId + "/reject"));
    }

    @PatchMapping("/{contractId}/cancel")
    public ResponseEntity<ApiResponse<ContractResponse>> cancel(@PathVariable UUID contractId) {
        return ResponseEntity.ok(responseFactory.success(
                "CONTRACT_CANCELLED",
                "Contract cancelled successfully",
                contractService.cancel(currentUserProvider.getCurrentUserId(), contractId),
                BASE_PATH + "/" + contractId + "/cancel"));
    }

    @PostMapping("/{contractId}/renewals")
    public ResponseEntity<ApiResponse<ContractResponse>> renew(
            @PathVariable UUID contractId,
            @Valid @RequestBody ContractTermsRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                "CONTRACT_RENEWAL_PROPOSED",
                "Contract renewal proposed and awaiting counterparty signature",
                contractService.renew(currentUserProvider.getCurrentUserId(), contractId, request),
                BASE_PATH + "/" + contractId + "/renewals"));
    }
}
