package com.ptutor.backend.complaint.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.complaint.dto.ComplaintResolutionRequest;
import com.ptutor.backend.complaint.dto.ComplaintResponse;
import com.ptutor.backend.complaint.service.ComplaintService;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/complaints")
@RequiredArgsConstructor
@Validated
public class ComplaintManagementController {

    private final ComplaintService complaintService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> findAll(
            @RequestParam(required = false) ComplaintStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.findForManagement(status), "/api/v1/admin/complaints"));
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> findById(@PathVariable UUID complaintId) {
        return ResponseEntity.ok(responseFactory.success(complaintService.findForManagementById(
                complaintId), "/api/v1/admin/complaints/" + complaintId));
    }

    @PatchMapping("/{complaintId}/status")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintResolutionRequest request) {
        return ResponseEntity.ok(responseFactory.success(complaintService.updateStatus(
                userId(jwt), complaintId, request), "/api/v1/admin/complaints/" + complaintId + "/status"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
