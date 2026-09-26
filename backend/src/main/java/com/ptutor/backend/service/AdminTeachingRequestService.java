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
import com.ptutor.backend.dto.request.TeachingRequestApprovalRequest;
import com.ptutor.backend.dto.response.AdminTeachingRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminTeachingRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTeachingRequestService {

    private static final Set<RequestStatus> ADMIN_STATUSES = Set.of(
            RequestStatus.PENDING_REVIEW, RequestStatus.OPEN, RequestStatus.REJECTED);

    private final TeachingRequestRepository teachingRequestRepository;
    private final PaymentRepository paymentRepository;
    private final SubjectRepository subjectRepository;
    private final EmployeeRepository employeeRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminTeachingRequestSummaryResponse> findAll(
            RequestStatus status, String keyword, Pageable pageable) {
        ensureAdminStatus(status);
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        Page<TeachingRequest> requests = teachingRequestRepository.findAllForAdmin(
                status, normalizedKeyword, pageable);
        return PageResponse.from(requests, requests.getContent().stream()
                .map(this::toSummaryResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminTeachingRequestDetailResponse findById(UUID requestId) {
        return toDetailResponse(teachingRequestRepository.findByIdForAdmin(requestId)
                .filter(request -> ADMIN_STATUSES.contains(request.getStatus()))
                .orElseThrow(() -> requestNotFound(requestId)));
    }

    @Transactional
    public AdminTeachingRequestDetailResponse approve(
            UUID reviewerUserId, UUID requestId, TeachingRequestApprovalRequest approval) {
        TeachingRequest request = findPendingForReview(requestId);
        Employee reviewer = findReviewer(reviewerUserId);

        resolveSubject(request, approval);

        request.setStatus(RequestStatus.OPEN);
        request.setRejectionReason(null);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now(clock));
        TeachingRequest saved = teachingRequestRepository.saveAndFlush(request);
        notificationService.createTeachingRequestReviewNotification(
                saved.getTutor().getUser(), saved.getId().toString(), saved.getTitle(), true, null, null);
        return toDetailResponse(saved);
    }

    private void resolveSubject(TeachingRequest request, TeachingRequestApprovalRequest approval) {
        if (request.getSubject() != null) {
            if (approval != null && approval.subjectResolution() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT_RESOLUTION_NOT_ALLOWED",
                        "Subject resolution is only allowed for a custom subject");
            }
            return;
        }

        TeachingRequestApprovalRequest.SubjectResolution resolution = approval == null
                ? null : approval.subjectResolution();
        if (resolution == null || resolution.action() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT_RESOLUTION_REQUIRED",
                    "Subject resolution is required for a custom subject");
        }

        Subject subject = switch (resolution.action()) {
            case CREATE -> createSubject(resolution);
            case USE_EXISTING -> findExistingSubject(resolution.subjectId());
        };
        request.setSubject(subject);
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
    public AdminTeachingRequestDetailResponse reject(
            UUID reviewerUserId, UUID requestId, String rejectionReason) {
        String normalizedReason = normalizeRejectionReason(rejectionReason);
        TeachingRequest request = findPendingForReview(requestId);
        Employee reviewer = findReviewer(reviewerUserId);
        User tutorUser = request.getTutor().getUser();
        Payment payment = findPaidPayment(tutorUser.getId(), requestId);

        walletService.credit(new WalletTransactionCommand(
                tutorUser.getId(),
                payment.getAmount(),
                WalletTransactionPurpose.TEACHING_REQUEST_REFUND,
                ReferenceType.TEACHING_REQUEST,
                requestId,
                "Refund for rejected teaching request",
                "teaching-request-refund-" + requestId));
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.saveAndFlush(payment);

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(normalizedReason);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now(clock));
        TeachingRequest saved = teachingRequestRepository.saveAndFlush(request);
        notificationService.createTeachingRequestReviewNotification(
                tutorUser, saved.getId().toString(), saved.getTitle(), false,
                normalizedReason, payment.getAmount());
        return toDetailResponse(saved);
    }

    private TeachingRequest findPendingForReview(UUID requestId) {
        TeachingRequest request = teachingRequestRepository.findByIdForReview(requestId)
                .orElseThrow(() -> requestNotFound(requestId));
        if (request.getStatus() != RequestStatus.PENDING_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "TEACHING_REQUEST_ALREADY_REVIEWED",
                    "Only PENDING_REVIEW teaching requests can be reviewed");
        }
        return request;
    }

    private Payment findPaidPayment(UUID userId, UUID requestId) {
        List<Payment> payments = paymentRepository.findForRefund(
                userId, PaymentType.TEACHING_REQUEST_FEE, ReferenceType.TEACHING_REQUEST,
                requestId, PaymentStatus.PAID, PageRequest.of(0, 1));
        if (payments.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "TEACHING_REQUEST_PAYMENT_NOT_FOUND",
                    "No paid teaching request fee is available for refund");
        }
        return payments.getFirst();
    }

    private Employee findReviewer(UUID userId) {
        return employeeRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "Only an employee or admin can review teaching requests"));
    }

    private void ensureAdminStatus(RequestStatus status) {
        if (!ADMIN_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEACHING_REQUEST_REVIEW_STATUS",
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

    private AdminTeachingRequestSummaryResponse toSummaryResponse(TeachingRequest request) {
        User user = request.getTutor().getUser();
        return new AdminTeachingRequestSummaryResponse(
                request.getId(), request.getTutor().getId(), fullName(user), user.getEmail(),
                subjectName(request), request.getTitle(), request.getNote(), request.getExpectedPrice(),
                request.getTeachingMode(), request.getStatus(), request.getCreatedAt(), request.getReviewedAt());
    }

    private AdminTeachingRequestDetailResponse toDetailResponse(TeachingRequest request) {
        User user = request.getTutor().getUser();
        AdminTeachingRequestDetailResponse.Reviewer reviewer = request.getReviewedBy() == null ? null
                : new AdminTeachingRequestDetailResponse.Reviewer(
                        request.getReviewedBy().getId(), fullName(request.getReviewedBy().getUser()));
        return new AdminTeachingRequestDetailResponse(
                request.getId(), request.getTutor().getId(), fullName(user), user.getEmail(), user.getAvatarUrl(),
                request.getSubject() == null ? null : request.getSubject().getId(), subjectName(request),
                request.getCustomSubjectName(),
                request.getGradeAssociations().stream()
                        .map(value -> new AdminTeachingRequestDetailResponse.Reference(
                                value.getGrade().getId(), value.getGrade().getName()))
                        .toList(),
                request.getDistrictAssociations().stream()
                        .map(value -> new AdminTeachingRequestDetailResponse.Reference(
                                value.getDistrict().getId(), value.getDistrict().getName()))
                        .toList(),
                request.getTitle(), request.getNote(), request.getQuantity(), request.getDetailAddress(),
                request.getExpectedPrice(), request.getTeachingMode(), request.getPreferredSchedule(),
                request.getDescription(), request.getAvailabilities().stream()
                        .map(value -> new AdminTeachingRequestDetailResponse.Availability(
                                value.getDayOfWeek(), value.getStartTime(), value.getEndTime()))
                        .toList(),
                request.getStatus(), reviewer, request.getReviewedAt(), request.getRejectionReason(),
                request.getCreatedAt(), request.getUpdatedAt());
    }

    private String subjectName(TeachingRequest request) {
        return request.getSubject() == null ? request.getCustomSubjectName() : request.getSubject().getName();
    }

    private String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    private ApiException requestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TEACHING_REQUEST_NOT_FOUND",
                "Teaching request not found: " + requestId);
    }
}
