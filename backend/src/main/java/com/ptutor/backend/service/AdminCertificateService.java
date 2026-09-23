package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.AdminCertificateDetailResponse;
import com.ptutor.backend.dto.response.AdminCertificateSummaryResponse;
import com.ptutor.backend.dto.response.CertificateReviewerResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AdminCertificateService {

    private final CertificateRepository certificateRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public AdminCertificateService(
            CertificateRepository certificateRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.certificateRepository = certificateRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Compatibility constructor for focused unit tests from before domain events. */
    public AdminCertificateService(
            CertificateRepository certificateRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService,
            Clock clock) {
        this(certificateRepository, employeeRepository, notificationService, null, clock);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCertificateSummaryResponse> findAll(
            CertificateStatus status, String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        Page<Certificate> certificates = certificateRepository.findAllForReview(
                status, normalizedKeyword, pageable);
        return PageResponse.from(certificates, certificates.getContent().stream()
                .map(this::toSummaryResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminCertificateDetailResponse findById(UUID certificateId) {
        return toDetailResponse(certificateRepository.findByIdForAdmin(certificateId)
                .orElseThrow(() -> certificateNotFound(certificateId)));
    }

    @Transactional
    public AdminCertificateDetailResponse approve(UUID reviewerUserId, UUID certificateId) {
        Certificate certificate = findPendingForUpdate(certificateId);
        if (certificate.getExpiryDate() != null
                && certificate.getExpiryDate().isBefore(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "CERTIFICATE_EXPIRED",
                    "An expired certificate cannot be approved");
        }

        Employee reviewer = findReviewer(reviewerUserId);
        certificate.setStatus(CertificateStatus.VERIFIED);
        certificate.setRejectionReason(null);
        certificate.setReviewedBy(reviewer);
        certificate.setReviewedAt(LocalDateTime.now(clock));
        Certificate saved = certificateRepository.saveAndFlush(certificate);
        publishCertificateNotification(saved, true, null);
        return toDetailResponse(saved);
    }

    @Transactional
    public AdminCertificateDetailResponse reject(
            UUID reviewerUserId, UUID certificateId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank() || rejectionReason.strip().length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON",
                    "Rejection reason is required and must not exceed 500 characters");
        }
        Certificate certificate = findPendingForUpdate(certificateId);
        Employee reviewer = findReviewer(reviewerUserId);
        String normalizedReason = rejectionReason.strip();

        certificate.setStatus(CertificateStatus.REJECTED);
        certificate.setRejectionReason(normalizedReason);
        certificate.setReviewedBy(reviewer);
        certificate.setReviewedAt(LocalDateTime.now(clock));
        Certificate saved = certificateRepository.saveAndFlush(certificate);
        publishCertificateNotification(saved, false, normalizedReason);
        return toDetailResponse(saved);
    }

    private Certificate findPendingForUpdate(UUID certificateId) {
        Certificate certificate = certificateRepository.findByIdForUpdate(certificateId)
                .orElseThrow(() -> certificateNotFound(certificateId));
        if (certificate.getStatus() != CertificateStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CERTIFICATE_ALREADY_REVIEWED",
                    "Only PENDING certificates can be reviewed");
        }
        return certificate;
    }

    private Employee findReviewer(UUID userId) {
        return employeeRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "Only an employee or admin can review certificates"));
    }

    private AdminCertificateSummaryResponse toSummaryResponse(Certificate certificate) {
        User user = certificate.getTutor().getUser();
        return new AdminCertificateSummaryResponse(
                certificate.getId(), certificate.getTutor().getId(), fullName(user), user.getEmail(),
                certificate.getName(), certificate.getIssuingOrganization(), certificate.getIssueDate(),
                certificate.getExpiryDate(), certificate.getStatus(), certificate.getCreatedAt());
    }

    private AdminCertificateDetailResponse toDetailResponse(Certificate certificate) {
        User tutorUser = certificate.getTutor().getUser();
        CertificateReviewerResponse reviewer = null;
        if (certificate.getReviewedBy() != null) {
            Employee reviewedBy = certificate.getReviewedBy();
            reviewer = new CertificateReviewerResponse(reviewedBy.getId(), fullName(reviewedBy.getUser()));
        }
        return new AdminCertificateDetailResponse(
                certificate.getId(), certificate.getTutor().getId(), fullName(tutorUser), tutorUser.getEmail(),
                tutorUser.getAvatarUrl(), certificate.getName(), certificate.getIssuingOrganization(),
                certificate.getDescription(), certificate.getIssueDate(), certificate.getExpiryDate(),
                certificate.getCertificateUrl(), certificate.getStatus(), certificate.getRejectionReason(), reviewer,
                certificate.getReviewedAt(), certificate.getCreatedAt(), certificate.getUpdatedAt());
    }

    private String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    private ApiException certificateNotFound(UUID certificateId) {
        return new ApiException(HttpStatus.NOT_FOUND, "CERTIFICATE_NOT_FOUND",
                "Certificate not found: " + certificateId);
    }

    private void publishCertificateNotification(Certificate certificate, boolean approved, String reason) {
        if (eventPublisher == null) {
            notificationService.createCertificateReviewNotification(
                    certificate.getTutor().getUser(), certificate.getId().toString(), certificate.getName(),
                    approved, reason);
            return;
        }
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                certificate.getTutor().getUser().getId(),
                approved ? NotificationEventType.CERTIFICATE_APPROVED : NotificationEventType.CERTIFICATE_REJECTED,
                NotificationReferenceType.CERTIFICATE,
                certificate.getId(),
                java.util.Map.of(
                        "name", certificate.getName() == null ? "" : certificate.getName(),
                        "reason", reason == null ? "" : reason)));
    }
}
