package com.ptutor.backend.tutor.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.tutor.dto.CertificateRequest;
import com.ptutor.backend.tutor.dto.CertificateResponse;
import com.ptutor.backend.tutor.repository.CertificateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final TutorRepository tutorRepository;

    @Transactional
    public CertificateResponse create(UUID userId, CertificateRequest request) {
        Tutor tutor = findTutorByUserId(userId);
        Certificate certificate = Certificate.builder()
                .tutor(tutor)
                .name(request.name().strip())
                .issuingOrganization(normalize(request.issuingOrganization()))
                .description(normalize(request.description()))
                .issueDate(request.issueDate())
                .expiryDate(request.expiryDate())
                .certificateUrl(normalize(request.certificateUrl()))
                .status(CertificateStatus.PENDING)
                .build();
        return CertificateResponse.from(certificateRepository.saveAndFlush(certificate));
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> findMine(UUID userId, CertificateStatus status) {
        Tutor tutor = findTutorByUserId(userId);
        List<Certificate> certificates = status == null
                ? certificateRepository.findAllByTutor_IdOrderByCreatedAtDesc(tutor.getId())
                : certificateRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(tutor.getId(), status);
        return certificates.stream()
                .map(CertificateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> findVerifiedByTutorId(UUID tutorId) {
        findTutorById(tutorId);
        return certificateRepository
                .findAllByTutor_IdAndStatusOrderByCreatedAtDesc(tutorId, CertificateStatus.VERIFIED).stream()
                .map(CertificateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CertificateResponse findMineById(UUID userId, UUID certificateId) {
        Tutor tutor = findTutorByUserId(userId);
        Certificate certificate = certificateRepository.findByIdAndTutor_Id(certificateId, tutor.getId())
                .orElseThrow(() -> certificateNotFound(certificateId));
        return CertificateResponse.from(certificate);
    }

    @Transactional
    public CertificateResponse update(UUID userId, UUID certificateId, CertificateRequest request) {
        Tutor tutor = findTutorByUserId(userId);
        Certificate certificate = certificateRepository.findByIdAndTutor_Id(certificateId, tutor.getId())
                .orElseThrow(() -> certificateNotFound(certificateId));
        if (certificate.getStatus() == CertificateStatus.VERIFIED) {
            throw new ApiException(HttpStatus.CONFLICT, "VERIFIED_CERTIFICATE_IMMUTABLE",
                    "A verified certificate cannot be edited; create a new certificate instead");
        }

        certificate.setName(request.name().strip());
        certificate.setIssuingOrganization(normalize(request.issuingOrganization()));
        certificate.setDescription(normalize(request.description()));
        certificate.setIssueDate(request.issueDate());
        certificate.setExpiryDate(request.expiryDate());
        certificate.setCertificateUrl(normalize(request.certificateUrl()));
        certificate.setStatus(CertificateStatus.PENDING);
        return CertificateResponse.from(certificateRepository.saveAndFlush(certificate));
    }

    @Transactional
    public void delete(UUID userId, UUID certificateId) {
        Tutor tutor = findTutorByUserId(userId);
        Certificate certificate = certificateRepository.findByIdAndTutor_Id(certificateId, tutor.getId())
                .orElseThrow(() -> certificateNotFound(certificateId));
        certificateRepository.delete(certificate);
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage certificates"));
    }

    private Tutor findTutorById(UUID tutorId) {
        return tutorRepository.findById(tutorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TUTOR_NOT_FOUND",
                        "Tutor not found: " + tutorId));
    }

    private ApiException certificateNotFound(UUID certificateId) {
        return new ApiException(HttpStatus.NOT_FOUND, "CERTIFICATE_NOT_FOUND",
                "Certificate not found: " + certificateId);
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
