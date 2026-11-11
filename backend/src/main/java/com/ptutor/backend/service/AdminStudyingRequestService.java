package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.request.StudyingRequestApprovalRequest;
import com.ptutor.backend.dto.request.TeachingRequestApprovalRequest;
import com.ptutor.backend.dto.response.AdminStudyingRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminStudyingRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.SubjectRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStudyingRequestService {

    private static final Set<RequestStatus> ADMIN_STATUSES = Set.of(
            RequestStatus.PENDING_REVIEW, RequestStatus.OPEN, RequestStatus.REJECTED);

    private final StudyingRequestRepository studyingRequestRepository;
    private final PaymentRepository paymentRepository;
    private final SubjectRepository subjectRepository;
    private final EmployeeRepository employeeRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminStudyingRequestSummaryResponse> findAll(
            RequestStatus status, String keyword, Pageable pageable) {
        ensureAdminStatus(status);
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        Page<StudyingRequest> requests = studyingRequestRepository.findAllForAdmin(
                status, normalizedKeyword, pageable);
        return PageResponse.from(requests, requests.getContent().stream()
                .map(this::toSummaryResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminStudyingRequestDetailResponse findById(UUID requestId) {
        return toDetailResponse(studyingRequestRepository.findByIdForAdmin(requestId)
                .filter(request -> ADMIN_STATUSES.contains(request.getStatus()))
                .orElseThrow(() -> requestNotFound(requestId)));
    }

    @Transactional
    public AdminStudyingRequestDetailResponse approve(
            UUID reviewerUserId, UUID requestId, StudyingRequestApprovalRequest approval) {
        StudyingRequest request = findPendingForReview(requestId);
        Employee reviewer = findReviewer(reviewerUserId);

        // A studying request is only reviewed because its subject is outside the catalogue.
        request.setSubject(resolveSubject(approval));

        request.setStatus(RequestStatus.OPEN);
        request.setRejectionReason(null);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now(clock));
        StudyingRequest saved = studyingRequestRepository.saveAndFlush(request);
        notificationService.createStudyingRequestReviewNotification(
                saved.getStudent().getUser(), saved.getId().toString(), saved.getTitle(), true, null, null);
        return toDetailResponse(saved);
    }

    private Subject resolveSubject(StudyingRequestApprovalRequest approval) {
        TeachingRequestApprovalRequest.SubjectResolution resolution = approval == null
                ? null : approval.subjectResolution();
        if (resolution == null || resolution.action() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT_RESOLUTION_REQUIRED",
                    "Subject resolution is required for a custom subject");
        }

        return switch (resolution.action()) {
            case CREATE -> createSubject(resolution);
            case USE_EXISTING -> findExistingSubject(resolution.subjectId());
        };
    }

    private Subject createSubject(TeachingRequestApprovalRequest.SubjectResolution resolution) {
        String name = normalizeSubjectName(resolution.name());
        subjectRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "SUBJECT_ALREADY_EXISTS",
                    "Subject already exists: " + existing.getId());
        });
        return subjectRepository.saveAndFlush(Subject.builder()
                .name(name)
                .description(normalizeSubjectDescription(resolution.description()))
                .status(CatalogStatus.ACTIVE)
                .build());
    }

    private Subject findExistingSubject(UUID subjectId) {
        if (subjectId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT_ID_REQUIRED",
                    "Subject ID is required when using an existing subject");
        }
        return subjectRepository.findById(subjectId)
                .filter(subject -> subject.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBJECT",
                        "Subject not found or inactive: " + subjectId));
    }

    private String normalizeSubjectName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBJECT_NAME",
                    "Subject name is required and must not exceed 100 characters");
        }
        return value.strip();
    }

    private String normalizeSubjectDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBJECT_DESCRIPTION",
                    "Subject description must not exceed 500 characters");
        }
        return normalized;
    }

    @Transactional
    public AdminStudyingRequestDetailResponse reject(
            UUID reviewerUserId, UUID requestId, String rejectionReason) {
        String normalizedReason = normalizeRejectionReason(rejectionReason);
        StudyingRequest request = findPendingForReview(requestId);
        Employee reviewer = findReviewer(reviewerUserId);
        User studentUser = request.getStudent().getUser();
        Payment payment = findPaidPayment(studentUser.getId(), requestId);

        walletService.credit(new WalletTransactionCommand(
                studentUser.getId(),
                payment.getAmount(),
                WalletTransactionPurpose.STUDYING_REQUEST_REFUND,
                ReferenceType.STUDYING_REQUEST,
                requestId,
                "Refund for rejected studying request",
                "studying-request-refund-" + requestId));
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.saveAndFlush(payment);

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(normalizedReason);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now(clock));
        StudyingRequest saved = studyingRequestRepository.saveAndFlush(request);
        notificationService.createStudyingRequestReviewNotification(
                studentUser, saved.getId().toString(), saved.getTitle(), false,
                normalizedReason, payment.getAmount());
        return toDetailResponse(saved);
    }

    private StudyingRequest findPendingForReview(UUID requestId) {
        StudyingRequest request = studyingRequestRepository.findByIdForReview(requestId)
                .orElseThrow(() -> requestNotFound(requestId));
        if (request.getStatus() != RequestStatus.PENDING_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDYING_REQUEST_ALREADY_REVIEWED",
                    "Only PENDING_REVIEW studying requests can be reviewed");
        }
        return request;
    }

    private Payment findPaidPayment(UUID userId, UUID requestId) {
        List<Payment> payments = paymentRepository.findForRefund(
                userId, PaymentType.STUDYING_REQUEST_FEE, ReferenceType.STUDYING_REQUEST,
                requestId, PaymentStatus.PAID, PageRequest.of(0, 1));
        if (payments.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDYING_REQUEST_PAYMENT_NOT_FOUND",
                    "No paid studying request fee is available for refund");
        }
        return payments.getFirst();
    }

    private Employee findReviewer(UUID userId) {
        return employeeRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "Only an employee or admin can review studying requests"));
    }

    private void ensureAdminStatus(RequestStatus status) {
        if (!ADMIN_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDYING_REQUEST_REVIEW_STATUS",
                    "Status must be PENDING_REVIEW, OPEN or REJECTED");
        }
    }

    private String normalizeRejectionReason(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON",
                    "Rejection reason is required and must not exceed 500 characters");
        }
        return value.strip();
    }

    private AdminStudyingRequestSummaryResponse toSummaryResponse(StudyingRequest request) {
        User user = request.getStudent().getUser();
        return new AdminStudyingRequestSummaryResponse(
                request.getId(), request.getStudent().getId(), fullName(user), user.getEmail(),
                subjectName(request), request.getGrade().getName(), request.getTitle(), request.getNote(),
                request.getMinPrice(), request.getMaxPrice(), request.getLearningMode(), request.getStatus(),
                request.getCreatedAt(), request.getReviewedAt());
    }

    private AdminStudyingRequestDetailResponse toDetailResponse(StudyingRequest request) {
        User user = request.getStudent().getUser();
        AdminStudyingRequestDetailResponse.Reviewer reviewer = request.getReviewedBy() == null ? null
                : new AdminStudyingRequestDetailResponse.Reviewer(
                        request.getReviewedBy().getId(), fullName(request.getReviewedBy().getUser()));
        return new AdminStudyingRequestDetailResponse(
                request.getId(), request.getStudent().getId(), fullName(user), user.getEmail(), user.getAvatarUrl(),
                request.getSubject() == null ? null : request.getSubject().getId(), subjectName(request),
                new AdminStudyingRequestDetailResponse.Reference(
                        request.getGrade().getId(), request.getGrade().getName()),
                request.getDistrict() == null ? null : new AdminStudyingRequestDetailResponse.Reference(
                        request.getDistrict().getId(), request.getDistrict().getName()),
                request.getTitle(), request.getDescription(), request.getNote(), request.getDetailAddress(),
                request.getMinPrice(), request.getMaxPrice(), request.getLearningGoals(),
                request.getLearningMode(), request.getPreferredSchedule(),
                request.getAvailabilities().stream()
                        .map(value -> new AdminStudyingRequestDetailResponse.Availability(
                                value.getDayOfWeek(), value.getStartTime(), value.getEndTime()))
                        .toList(),
                request.getStatus(), reviewer, request.getReviewedAt(), request.getRejectionReason(),
                request.getCreatedAt(), request.getUpdatedAt());
    }

    private String subjectName(StudyingRequest request) {
        return request.getSubject() == null ? null : request.getSubject().getName();
    }

    private String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    private ApiException requestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDYING_REQUEST_NOT_FOUND",
                "Studying request not found: " + requestId);
    }
}
