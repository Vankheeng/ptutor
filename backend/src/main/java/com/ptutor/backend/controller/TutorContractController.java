package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.TutorContractCreateRequest;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ContractService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests/{studentRequestId}/contracts")
@RequiredArgsConstructor
@Validated
public class TutorContractController {

    private final ContractService contractService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<ContractResponse>> create(
            @PathVariable UUID teachingRequestId,
            @PathVariable UUID studentRequestId,
            @Valid @RequestBody TutorContractCreateRequest request) {
        String path = "/api/v1/tutors/me/teaching-requests/" + teachingRequestId
                + "/student-requests/" + studentRequestId + "/contracts";
        return ResponseEntity.status(201).body(responseFactory.success(
                "CONTRACT_CREATED",
                "Contract created and awaiting counterparty signature",
                contractService.createFromStudentTutorRequest(
                        currentUserProvider.getCurrentUserId(), teachingRequestId, studentRequestId, request),
                path));
    }
}
