package com.ptutor.backend.complaint.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.complaint.dto.ComplaintEvidenceRequest;
import com.ptutor.backend.complaint.dto.ComplaintEvidenceResponse;
import com.ptutor.backend.complaint.dto.ComplaintResolutionRequest;
import com.ptutor.backend.complaint.dto.ComplaintResponse;
import com.ptutor.backend.complaint.dto.CreateComplaintRequest;
import com.ptutor.backend.complaint.service.ComplaintService;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Validated
public class ComplaintController {

    private final ComplaintService complaintService;
    private final ApiResponseFactory responseFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateComplaintRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                complaintService.create(userId(jwt), request), "/api/v1/complaints"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> findMine(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(responseFactory.success(
                complaintService.findMine(userId(jwt)), "/api/v1/complaints"));
    }

    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> findMineById(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID complaintId) {
        return ResponseEntity.ok(responseFactory.success(complaintService.findMineById(
                userId(jwt), complaintId), "/api/v1/complaints/" + complaintId));
    }

    @PostMapping("/{complaintId}/evidences")
    public ResponseEntity<ApiResponse<ComplaintEvidenceResponse>> addEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID complaintId,
            @Valid @RequestBody ComplaintEvidenceRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(complaintService.addEvidence(
                userId(jwt), complaintId, request), "/api/v1/complaints/" + complaintId + "/evidences"));
    }

    @DeleteMapping("/{complaintId}/evidences/{evidenceId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID complaintId,
            @PathVariable UUID evidenceId) {
        complaintService.deleteEvidence(userId(jwt), complaintId, evidenceId);
        return ResponseEntity.ok(responseFactory.success("EVIDENCE_DELETED", "Evidence deleted successfully",
                null, "/api/v1/complaints/" + complaintId + "/evidences/" + evidenceId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
