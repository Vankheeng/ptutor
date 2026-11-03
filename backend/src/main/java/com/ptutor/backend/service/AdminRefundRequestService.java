package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.request.RefundApprovalRequest;
import com.ptutor.backend.dto.response.AdminRefundRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminRefundRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WalletTransactionResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.RefundRequestLesson;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.LessonOutcome;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.RefundRequestRepository;
import com.ptutor.backend.repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Accountant review of refund requests: the queue, the decision on each disputed lesson, the refund to the
 * student's wallet and the tutor's share of lessons the student skipped.
 */
@Service
@RequiredArgsConstructor
public class AdminRefundRequestService {

    private static final List<ComplaintStatus> OPEN_COMPLAINT_STATUSES =
            List.of(ComplaintStatus.PENDING, ComplaintStatus.IN_REVIEW, ComplaintStatus.AWAITING_EVIDENCE);

    private final RefundRequestRepository refundRequestRepository;
    private final RefundRequestLessonRepository refundRequestLessonRepository;
    private final EmployeeRepository employeeRepository;
    private final ComplaintRepository complaintRepository;
    private final LessonRepository lessonRepository;
    private final PaymentRepository paymentRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final RefundAssessor refundAssessor;
    private final RefundPolicy refundPolicy;
    private final RefundRequestViews views;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminRefundRequestSummaryResponse> findAll(
            RefundRequestStatus status, UUID contractId, String keyword, Pageable pageable) {
        Page<RefundRequest> requests = refundRequestRepository.findAllForReview(
                status == null ? RefundRequestStatus.PENDING : status, contractId,
                keyword == null ? "" : keyword.strip(), pageable);
        LocalDateTime now = LocalDateTime.now(clock);
        return PageResponse.from(requests, requests.getContent().stream().map(request -> {
            Contract contract = request.getContract();
            User student = request.getStudent().getUser();
            Employee reviewer = request.getEmployee();
            return new AdminRefundRequestSummaryResponse(
                    request.getId(), student.getId(), RefundRequestViews.fullName(student), student.getEmail(),
                    contract.getId(), contract.getSubject() == null ? null : contract.getSubject().getName(),
                    contract.getGrade() == null ? null : contract.getGrade().getName(),
                    RefundRequestViews.fullName(contract.getTutor().getUser()), request.getStatus(),
                    refundRequestLessonRepository.countByRefundRequest_Id(request.getId()),
                    awaitingTutor(request, now),
                    complaintRepository.existsByContract_IdAndStatusIn(contract.getId(), OPEN_COMPLAINT_STATUSES),
                    reviewer == null ? null : reviewer.getId(),
                    reviewer == null ? null : RefundRequestViews.fullName(reviewer.getUser()),
                    request.getCreatedAt());
        }).toList());
    }

    @Transactional(readOnly = true)
    public AdminRefundRequestDetailResponse findById(UUID refundRequestId) {
        RefundRequest request = refundRequestRepository.findDetailedById(refundRequestId)
                .orElseThrow(() -> RefundRequestService.notFound(refundRequestId));
        return toDetail(request);
    }

    @Transactional
    public AdminRefundRequestDetailResponse startReview(UUID reviewerUserId, UUID refundRequestId) {
        RefundRequest request = findForUpdate(refundRequestId);
        if (request.getStatus() != RefundRequestStatus.PENDING || request.getEmployee() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_REQUEST_ALREADY_ASSIGNED",
                    "Only an unassigned PENDING refund request can be taken into review");
        }
        request.setEmployee(findReviewer(reviewerUserId));
        request.setStatus(RefundRequestStatus.IN_REVIEW);
        return toDetail(refundRequestRepository.saveAndFlush(request));
    }

    @Transactional
    public AdminRefundRequestDetailResponse approve(
            UUID reviewerUserId, UUID refundRequestId, RefundApprovalRequest source) {
        RefundRequest request = findAssignedInReview(reviewerUserId, refundRequestId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (awaitingTutor(request, now)) {
            throw new ApiException(HttpStatus.CONFLICT, "TUTOR_RESPONSE_WINDOW_OPEN",
                    "Wait for the tutor's response or for the response deadline to pass");
        }
        Contract contract = request.getContract();
        if (complaintRepository.existsByContract_IdAndStatusIn(contract.getId(), OPEN_COMPLAINT_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_HAS_OPEN_COMPLAINT",
                    "The contract has a complaint that has not been concluded yet");
        }

        List<RefundRequestLesson> disputed = views.disputedLessons(request);
        // A disputed lesson the tutor cancelled in the meantime no longer needs a decision.
        Set<UUID> pendingIds = disputed.stream()
                .map(RefundRequestLesson::getLesson)
                .filter(lesson -> lesson.getStatus() == LessonStatus.PENDING_CONFIRMATION)
                .map(Lesson::getId)
                .collect(Collectors.toSet());
        Map<UUID, LessonOutcome> decisions = new HashMap<>();
        for (RefundApprovalRequest.LessonDecision decision : source.lessonDecisions()) {
            decisions.put(decision.lessonId(), decision.decision());
        }
        if (decisions.size() != source.lessonDecisions().size() || !decisions.keySet().equals(pendingIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_DECISION_REQUIRED",
                    "Give exactly one decision for every disputed lesson awaiting confirmation");
        }

        RefundAssessor.Assessment assessment = refundAssessor.assess(contract, List.copyOf(decisions.values()));
        RefundCalculator.Result result = assessment.result();
        BigDecimal refund = result.refundAmount();
        BigDecimal tutorShare = result.tutorShareAmount();
        if (refund.signum() <= 0 && tutorShare.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_REFUND",
                    "No lesson was found not taught; reject the request instead");
        }
        if (refund.compareTo(source.expectedAmount()) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_AMOUNT_CHANGED",
                    "The refund amount is now " + RefundRequestService.amountText(refund)
                            + "; review the request again");
        }

        if (refund.signum() > 0) {
            WalletTransactionResponse credit = walletService.credit(new WalletTransactionCommand(
                    contract.getStudent().getUser().getId(), refund, WalletTransactionPurpose.CONTRACT_REFUND,
                    ReferenceType.REFUND_REQUEST, request.getId(), "Refund for lessons that did not take place",
                    "contract-refund-" + request.getId()));
            request.setWalletTransaction(walletTransactionRepository.getReferenceById(credit.transactionId()));
            allocateToPayments(assessment.tuitionPayments(), refund);
        }
        if (tutorShare.signum() > 0) {
            WalletTransactionResponse share = walletService.credit(new WalletTransactionCommand(
                    contract.getTutor().getUser().getId(), tutorShare, WalletTransactionPurpose.TUTOR_EARNING,
                    ReferenceType.REFUND_REQUEST, request.getId(), "Share of lessons the student skipped",
                    "refund-tutor-share-" + request.getId()));
            request.setTutorWalletTransaction(walletTransactionRepository.getReferenceById(share.transactionId()));
        }
        settleDisputedLessons(disputed, decisions);

        request.setUnitPrice(result.unitPrice());
        request.setRefundAmount(refund);
        request.setTutorShareAmount(tutorShare);
        request.setNote(normalizeOptional(source.note()));
        request.setStatus(RefundRequestStatus.REFUNDED);
        request.setReviewedAt(now);
        RefundRequest saved = refundRequestRepository.saveAndFlush(request);

        publishToParticipants(saved, NotificationEventType.REFUND_REQUEST_APPROVED,
                Map.of("amount", RefundRequestService.amountText(refund)));
        return toDetail(saved);
    }

    @Transactional
    public AdminRefundRequestDetailResponse reject(UUID reviewerUserId, UUID refundRequestId, String reason) {
        RefundRequest request = findAssignedInReview(reviewerUserId, refundRequestId);
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON",
                    "Rejection reason is required and must not exceed 500 characters");
        }
        request.setStatus(RefundRequestStatus.REJECTED);
        request.setRejectionReason(normalized);
        request.setReviewedAt(LocalDateTime.now(clock));
        RefundRequest saved = refundRequestRepository.saveAndFlush(request);
        publishToParticipants(saved, NotificationEventType.REFUND_REQUEST_REJECTED, Map.of("reason", normalized));
        return toDetail(saved);
    }

    @Transactional
    public AdminRefundRequestDetailResponse reassign(UUID adminUserId, UUID refundRequestId, UUID targetEmployeeId) {
        RefundRequest request = findForUpdate(refundRequestId);
        if (findReviewer(adminUserId).getRole() != EmployeeRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REFUND_REQUEST_REASSIGN_FORBIDDEN",
                    "Only an admin can reassign refund requests");
        }
        if (request.getStatus() != RefundRequestStatus.IN_REVIEW || request.getEmployee() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REFUND_REQUEST_STATUS_TRANSITION",
                    "Only an assigned IN_REVIEW refund request can be reassigned");
        }
        Employee target = employeeRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND",
                        "Employee not found: " + targetEmployeeId));
        if (target.getUser() == null || target.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_NOT_ACTIVE", "The target employee account must be active");
        }
        if (target.getRole() != EmployeeRole.ADMIN && target.getJobFunction() != EmployeeJobFunction.ACCOUNTANT) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_EMPLOYEE_FUNCTION",
                    "The target employee must be an accountant");
        }
        if (request.getEmployee().getId().equals(target.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_REQUEST_ALREADY_ASSIGNED_TO_EMPLOYEE",
                    "The refund request is already assigned to this employee");
        }
        request.setEmployee(target);
        return toDetail(refundRequestRepository.saveAndFlush(request));
    }

    // Newest payment first, so older installments stay fully paid as long as possible.
    private void allocateToPayments(List<Payment> payments, BigDecimal amount) {
        BigDecimal remaining = amount;
        List<Payment> ordered = new ArrayList<>(payments);
        ordered.sort(Comparator.comparing(
                (Payment payment) -> payment.getPaidAt() == null ? payment.getCreatedAt() : payment.getPaidAt(),
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        for (Payment payment : ordered) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal refunded = payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
            BigDecimal capacity = payment.getAmount().subtract(refunded);
            if (capacity.signum() <= 0) {
                continue;
            }
            BigDecimal portion = capacity.min(remaining);
            payment.setRefundedAmount(refunded.add(portion));
            if (payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            }
            remaining = remaining.subtract(portion);
        }
        paymentRepository.saveAll(ordered);
    }

    // Taught lessons are completed so the tutor is paid for them; absences are cancelled so the tutor is not.
    private void settleDisputedLessons(List<RefundRequestLesson> disputed, Map<UUID, LessonOutcome> decisions) {
        if (decisions.isEmpty()) {
            return;
        }
        Map<UUID, Lesson> lessons = lessonRepository.findAllByIdForUpdate(decisions.keySet()).stream()
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));
        for (RefundRequestLesson item : disputed) {
            UUID lessonId = item.getLesson().getId();
            LessonOutcome decision = decisions.get(lessonId);
            Lesson lesson = lessons.get(lessonId);
            if (decision == null || lesson == null) {
                continue;
            }
            item.setDecision(decision);
            lesson.setStatus(decision == LessonOutcome.TAUGHT ? LessonStatus.COMPLETED : LessonStatus.CANCELLED);
        }
        refundRequestLessonRepository.saveAll(disputed);
        lessonRepository.saveAll(lessons.values());
    }

    private AdminRefundRequestDetailResponse toDetail(RefundRequest request) {
        Contract contract = request.getContract();
        List<RefundRequestLesson> disputed = views.disputedLessons(request);
        RefundAssessor.Assessment assessment = refundAssessor.assess(contract, previewOutcomes(disputed));
        User student = contract.getStudent().getUser();
        User tutor = contract.getTutor().getUser();
        Employee reviewer = request.getEmployee();
        return new AdminRefundRequestDetailResponse(
                request.getId(), contract.getId(), request.getStatus(), request.getDescription(),
                views.toDisputedLessons(disputed), views.evidences(request),
                request.getTutorResponse(), request.getTutorRespondedAt(), request.getTutorResponseDeadline(),
                request.getRefundAmount(), request.getTutorShareAmount(), request.getRejectionReason(),
                request.getReviewedAt(), request.getCreatedAt(), request.getUpdatedAt(),
                new AdminRefundRequestDetailResponse.Party(student.getId(), RefundRequestViews.fullName(student),
                        student.getEmail()),
                new AdminRefundRequestDetailResponse.Party(tutor.getId(), RefundRequestViews.fullName(tutor),
                        tutor.getEmail()),
                reviewer == null ? null : new AdminRefundRequestDetailResponse.Reviewer(
                        reviewer.getId(), reviewer.getUser().getId(), RefundRequestViews.fullName(reviewer.getUser())),
                new AdminRefundRequestDetailResponse.ContractSummary(
                        contract.getId(), contract.getSubject() == null ? null : contract.getSubject().getName(),
                        contract.getGrade() == null ? null : contract.getGrade().getName(), contract.getTeachingMode(),
                        contract.getPrice(), contract.getPaymentPeriod(), contract.getTotalLession(),
                        contract.getStartDate(), contract.getEndDate(), contract.getStatus()),
                assessment.tuitionPayments().stream().map(payment ->
                        new AdminRefundRequestDetailResponse.PaymentSummary(payment.getId(),
                                payment.getPaymentInstallment() == null ? null : payment.getPaymentInstallment().getId(),
                                payment.getPaymentMethod(), payment.getStatus(), payment.getAmount(),
                                payment.getRefundedAmount(), payment.getPaidAt())).toList(),
                complaintRepository.findAllByContract_IdOrderByCreatedAtDesc(contract.getId()).stream()
                        .filter(complaint -> OPEN_COMPLAINT_STATUSES.contains(complaint.getStatus()))
                        .map(complaint -> new AdminRefundRequestDetailResponse.ComplaintSummary(
                                complaint.getId(), complaint.getTitle(), complaint.getStatus(),
                                complaint.getFaultParty()))
                        .toList(),
                calculation(request, disputed, assessment.result()), request.getNote());
    }

    /**
     * Decided lessons count as decided; undecided ones as the student claimed, so the reviewer sees what the
     * request would cost if upheld.
     */
    private static List<LessonOutcome> previewOutcomes(List<RefundRequestLesson> disputed) {
        return disputed.stream()
                .map(item -> item.getDecision() != null ? item.getDecision() : item.getClaimedOutcome())
                .toList();
    }

    private AdminRefundRequestDetailResponse.Calculation calculation(
            RefundRequest request, List<RefundRequestLesson> disputed, RefundCalculator.Result result) {
        boolean refunded = request.getStatus() == RefundRequestStatus.REFUNDED;
        List<String> blockers = new ArrayList<>();
        if (RefundRequestService.OPEN_STATUSES.contains(request.getStatus())) {
            if (awaitingTutor(request, LocalDateTime.now(clock))) {
                blockers.add("TUTOR_RESPONSE_WINDOW_OPEN");
            }
            if (complaintRepository.existsByContract_IdAndStatusIn(
                    request.getContract().getId(), OPEN_COMPLAINT_STATUSES)) {
                blockers.add("CONTRACT_HAS_OPEN_COMPLAINT");
            }
            if (result.refundableAmount().signum() <= 0) {
                blockers.add("NOTHING_TO_REFUND");
            }
        }
        int undecided = (int) disputed.stream()
                .filter(item -> item.getDecision() == null)
                .filter(item -> item.getLesson().getStatus() == LessonStatus.PENDING_CONFIRMATION)
                .count();
        return new AdminRefundRequestDetailResponse.Calculation(
                refunded && request.getUnitPrice() != null ? request.getUnitPrice() : result.unitPrice(),
                refundPolicy.tutorAbsentRate(), refundPolicy.studentAbsentRate(), result.paidAmount(),
                result.previouslyRefundedAmount(), result.refundableAmount(),
                refunded ? request.getRefundAmount() : result.refundAmount(),
                refunded ? request.getTutorShareAmount() : result.tutorShareAmount(),
                refunded ? 0 : undecided, blockers);
    }

    private boolean awaitingTutor(RefundRequest request, LocalDateTime now) {
        return RefundRequestService.OPEN_STATUSES.contains(request.getStatus())
                && request.getTutorRespondedAt() == null
                && !now.isAfter(request.getTutorResponseDeadline());
    }

    private RefundRequest findAssignedInReview(UUID reviewerUserId, UUID refundRequestId) {
        RefundRequest request = findForUpdate(refundRequestId);
        if (request.getStatus() != RefundRequestStatus.IN_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REFUND_REQUEST_STATUS_TRANSITION",
                    "Only an IN_REVIEW refund request can be decided");
        }
        if (request.getEmployee() == null || request.getEmployee().getUser() == null
                || !Objects.equals(reviewerUserId, request.getEmployee().getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REFUND_REQUEST_REVIEW_FORBIDDEN",
                    "Only the assigned employee can decide this refund request");
        }
        return request;
    }

    private RefundRequest findForUpdate(UUID refundRequestId) {
        return refundRequestRepository.findByIdForUpdate(refundRequestId)
                .orElseThrow(() -> RefundRequestService.notFound(refundRequestId));
    }

    private Employee findReviewer(UUID userId) {
        return employeeRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "An employee profile is required to review refund requests"));
    }

    private void publishToParticipants(RefundRequest request, NotificationEventType eventType, Map<String, String> data) {
        Contract contract = request.getContract();
        for (UUID recipient : List.of(contract.getStudent().getUser().getId(), contract.getTutor().getUser().getId())) {
            eventPublisher.publishEvent(NotificationDomainEvent.of(
                    recipient, eventType, NotificationReferenceType.REFUND_REQUEST, request.getId(), data));
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
