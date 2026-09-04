package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.dto.request.CertificateRequest;
import com.ptutor.backend.dto.response.CertificateResponse;
import com.ptutor.backend.service.CertificateService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tutors")
@RequiredArgsConstructor
@Validated
public class CertificateController {

    private final CertificateService certificateService;
    private final ApiResponseFactory responseFactory;

    @PostMapping("/me/certificates")
    public ResponseEntity<ApiResponse<CertificateResponse>> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                certificateService.create(userId(jwt), request), "/api/v1/tutors/me/certificates"));
    }

    @GetMapping("/me/certificates")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) CertificateStatus status) {
        return ResponseEntity.ok(responseFactory.success(
                certificateService.findMine(userId(jwt), status), "/api/v1/tutors/me/certificates"));
    }

    @GetMapping("/{tutorId}/certificates")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> findVerifiedByTutorId(
            @PathVariable UUID tutorId) {
        return ResponseEntity.ok(responseFactory.success(
                certificateService.findVerifiedByTutorId(tutorId), "/api/v1/tutors/" + tutorId + "/certificates"));
    }

    @GetMapping("/me/certificates/{certificateId}")
    public ResponseEntity<ApiResponse<CertificateResponse>> findMineById(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId) {
        return ResponseEntity.ok(responseFactory.success(certificateService.findMineById(
                userId(jwt), certificateId), "/api/v1/tutors/me/certificates/" + certificateId));
    }

    @PutMapping("/me/certificates/{certificateId}")
    public ResponseEntity<ApiResponse<CertificateResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID certificateId,
            @Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.ok(responseFactory.success(certificateService.update(
                userId(jwt), certificateId, request), "/api/v1/tutors/me/certificates/" + certificateId));
    }

    @DeleteMapping("/me/certificates/{certificateId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId) {
        certificateService.delete(userId(jwt), certificateId);
        return ResponseEntity.ok(responseFactory.success("CERTIFICATE_DELETED", "Certificate deleted successfully",
                null, "/api/v1/tutors/me/certificates/" + certificateId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
