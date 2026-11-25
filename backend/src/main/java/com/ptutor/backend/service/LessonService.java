package com.ptutor.backend.service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.LessonRequest;
import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.dto.response.LessonResponse.RefundSummary;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.LessonMapper;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LessonService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<RefundRequestStatus> OPEN_REFUND_STATUSES =
            List.of(RefundRequestStatus.PENDING, RefundRequestStatus.IN_REVIEW);
    private static final List<ComplaintStatus> BLOCKING_COMPLAINT_STATUSES =
            List.of(ComplaintStatus.PENDING, ComplaintStatus.IN_REVIEW, ComplaintStatus.AWAITING_EVIDENCE);

    private final LessonRepository lessonRepository;
    private final ContractRepository contractRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final ComplaintRepository complaintRepository;
    private final RefundRequestLessonRepository refundRequestLessonRepository;
    private final LessonMapper lessonMapper;
    private final Clock clock;
    private final ContractPaymentInstallmentService installmentService;
    private final LessonPayoutService payoutService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private ContractLessonProvisioningService lessonProvisioningService;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private WalletService walletService;

    @Transactional
    public LessonResponse create(UUID userId, UUID contractId, LessonRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        Contract contract = findOwnedContract(contractId, tutor.getId());
        validateActiveContract(contract);
        validateSchedule(contract, source);
        ensureNoScheduleConflict(contract, source.date(), source.startTime(), source.endTime(), null);
        if (!installmentService.canCreateLesson(contract)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_LESSON_LIMIT_REACHED",
                    "The contract already has its maximum number of lessons");
        }
        Lesson lesson = Lesson.builder()
                .contract(contract)
                .title(normalize(source.title()))
                .date(source.date())
                .startTime(source.startTime())
                .endTime(source.endTime())
                .teachingMode(contract.getTeachingMode())
                .meetingLink(normalizeMeetingLink(source.meetingLink()))
                .note(normalize(source.note()))
                .status(LessonStatus.SCHEDULED)
                .build();
        Lesson saved = lessonRepository.saveAndFlush(lesson);
        installmentService.assignNextInstallment(contract, saved);
        return toResponse(saved);
    }

    @Transactional
    public List<LessonResponse> findByContractForStudent(UUID userId, UUID contractId) {
        Student student = findStudentByUserId(userId);
        Contract contract = contractRepository.findById(contractId)
                .filter(candidate -> candidate.getStudent() != null && student.getId().equals(candidate.getStudent().getId()))
                .orElseThrow(() -> contractNotFound(contractId));
        if (lessonProvisioningService != null) {
            lessonProvisioningService.provision(contract);
        }
        return toResponses(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contractId), contract);
    }

    @Transactional
    public PageResponse<LessonResponse> findByContract(
            UUID userId, UUID contractId, LessonStatus status, Pageable pageable) {
        Tutor tutor = findTutorByUserId(userId);
        Contract contract = findOwnedContract(contractId, tutor.getId());
        if (lessonProvisioningService != null) {
            lessonProvisioningService.provision(contract);
        }
        Page<Lesson> lessons = status == null
                ? lessonRepository.findAllByContract_Id(contractId, pageable)
                : lessonRepository.findAllByContract_IdAndStatus(contractId, status, pageable);
        return PageResponse.from(lessons, toResponses(lessons.getContent(), contract));
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> findForTutor(
            UUID userId, LessonStatus status, ContractStatus contractStatus, Pageable pageable) {
        Tutor tutor = findTutorByUserId(userId);
        Page<Lesson> lessons = lessonRepository.findAllForTutorUser(tutor.getUser().getId(), status, contractStatus, pageable);
        return PageResponse.from(lessons, lessons.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> findForStudent(UUID userId, Pageable pageable) {
        Student student = findStudentByUserId(userId);
        Page<Lesson> lessons = lessonRepository.findAllForStudentUser(
                student.getUser().getId(), null, null, pageable);
        return PageResponse.from(lessons, lessons.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public LessonResponse findById(UUID userId, UUID lessonId) {
        Tutor tutor = findTutorByUserId(userId);
        return toResponse(findOwnedLesson(lessonId, tutor.getId()));
    }

    @Transactional
    public LessonResponse update(UUID userId, UUID lessonId, LessonRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        Lesson lesson = findOwnedLesson(lessonId, tutor.getId());
        Contract contract = lesson.getContract();
        validateActiveContract(contract);
        ensureScheduled(lesson, "updated");
        validateSchedule(contract, source);
        ensureNoScheduleConflict(contract, source.date(), source.startTime(), source.endTime(), lesson.getId());

        boolean scheduleChanged = !source.date().equals(lesson.getDate())
                || !source.startTime().equals(lesson.getStartTime())
                || !source.endTime().equals(lesson.getEndTime());

        lesson.setTitle(normalize(source.title()));
        lesson.setDate(source.date());
        lesson.setStartTime(source.startTime());
        lesson.setEndTime(source.endTime());
        lesson.setMeetingLink(normalizeMeetingLink(source.meetingLink()));
        lesson.setNote(normalize(source.note()));
        Lesson saved = lessonRepository.saveAndFlush(lesson);
        installmentService.updatePendingLessonInstallmentDueDate(saved);
        if (scheduleChanged) {
            publishStudentRescheduledNotification(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public LessonResponse updateStatus(UUID userId, UUID lessonId, LessonRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        Lesson lesson = findOwnedLesson(lessonId, tutor.getId());
        LessonStatus targetStatus = require(source.status(), "LESSON_STATUS_REQUIRED", "Status is required");

        if (targetStatus == LessonStatus.PENDING_CONFIRMATION) {
            ensureScheduled(lesson, "marked as taught");
            LocalDateTime lessonEnd = LocalDateTime.of(lesson.getDate(), lesson.getEndTime());
            if (lessonEnd.isAfter(LocalDateTime.now(clock))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_NOT_FINISHED",
                        "A lesson can only be marked as taught after it ends");
            }
            installmentService.requirePaidForLesson(lesson);
        } else if (targetStatus == LessonStatus.CANCELLED) {
            if (lesson.getStatus() != LessonStatus.SCHEDULED
                    && lesson.getStatus() != LessonStatus.PENDING_CONFIRMATION) {
                throw invalidStatusTransition("Only SCHEDULED or PENDING_CONFIRMATION lessons can be cancelled");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_STATUS",
                    "Tutor can only mark a lesson as taught or cancel it");
        }

        lesson.setStatus(targetStatus);
        if (targetStatus == LessonStatus.PENDING_CONFIRMATION) {
            lesson.setMarkedTaughtAt(LocalDateTime.now(clock));
        }
        Lesson saved = lessonRepository.saveAndFlush(lesson);
        java.math.BigDecimal refundAmount = targetStatus == LessonStatus.CANCELLED
                ? refundPaidLesson(saved)
                : null;
        if (targetStatus == LessonStatus.CANCELLED) {
            installmentService.releasePendingLessonInstallment(saved);
        }
        publishStudentNotification(saved,
                targetStatus == LessonStatus.CANCELLED
                        ? NotificationEventType.LESSON_CANCELLED
                        : NotificationEventType.LESSON_MARKED_TAUGHT,
                refundAmount);
        return toResponse(saved);
    }

    @Transactional
    public LessonResponse confirmByStudent(UUID userId, UUID lessonId) {
        Student student = findStudentByUserId(userId);
        Lesson lesson = findStudentLesson(lessonId, student.getId());
        ensurePendingConfirmation(lesson, "confirmed");
        LocalDateTime lessonEnd = LocalDateTime.of(lesson.getDate(), lesson.getEndTime());
        if (lessonEnd.isAfter(LocalDateTime.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_NOT_FINISHED",
                    "A lesson can only be confirmed after it ends");
        }
        ensureNoBlockingComplaint(lesson.getContract());
        if (refundRequestLessonRepository.existsByLesson_IdAndRefundRequest_StatusIn(
                lesson.getId(), OPEN_REFUND_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "LESSON_COMPLETION_ON_HOLD",
                    "Lesson completion is on hold while it is disputed in a refund request");
        }

        lesson.setStatus(LessonStatus.CONFIRMED);
        payoutService.release(lesson);
        return toResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Transactional
    public int completeExpiredPendingLessons(int graceDays) {
        // The grace period starts when the tutor marks the lesson as taught, not when it ends,
        // so a late report still leaves the student the full window to confirm or dispute it.
        LocalDateTime deadline = LocalDateTime.now(clock).minusDays(graceDays);
        List<Lesson> candidates = lessonRepository.findAllMarkedTaughtBefore(
                LessonStatus.PENDING_CONFIRMATION, deadline);
        // Lessons disputed in an open refund request wait for the reviewer's decision.
        java.util.Set<UUID> heldLessonIds = candidates.isEmpty() ? java.util.Set.of()
                : java.util.Set.copyOf(refundRequestLessonRepository.findHeldLessonIds(
                        candidates.stream().map(Lesson::getId).toList(), OPEN_REFUND_STATUSES));
        List<Lesson> completedLessons = candidates.stream()
                .filter(lesson -> !heldLessonIds.contains(lesson.getId()))
                .filter(lesson -> !hasBlockingComplaint(lesson.getContract()))
                .toList();
        if (completedLessons.isEmpty()) {
            return 0;
        }
        completedLessons.forEach(lesson -> {
            lesson.setStatus(LessonStatus.COMPLETED);
            payoutService.release(lesson);
        });
        lessonRepository.saveAll(completedLessons);
        return completedLessons.size();
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage lessons"));
    }

    private void ensureNoScheduleConflict(
            Contract contract, LocalDate date, LocalTime startTime, LocalTime endTime, UUID excludedLessonId) {
        List<Lesson> conflicts = lessonRepository.findScheduleConflicts(
                contract.getStudent().getUser().getId(), contract.getTutor().getUser().getId(),
                date, startTime, endTime, excludedLessonId);
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }

        Lesson conflict = conflicts.get(0);
        boolean studentConflict = conflict.getContract() != null
                && conflict.getContract().getStudent() != null
                && conflict.getContract().getStudent().getUser() != null
                && conflict.getContract().getStudent().getUser().getId().equals(contract.getStudent().getUser().getId());
        boolean tutorConflict = conflict.getContract() != null
                && conflict.getContract().getTutor() != null
                && conflict.getContract().getTutor().getUser() != null
                && conflict.getContract().getTutor().getUser().getId().equals(contract.getTutor().getUser().getId());
        String conflictSchedule = formatLessonSchedule(conflict);
        // Reveal the title only for a lesson that belongs exclusively to the
        // tutor making this request. Shared student+tutor schedule data, and
        // the student's schedule, must remain generic.
        String message = tutorConflict && !studentConflict
                ? "Gia sư đang có buổi học \"" + safeLessonTitle(conflict) + "\" trùng lịch ("
                        + conflictSchedule + "). Vui lòng chọn khung giờ khác."
                : "Học viên đang có một buổi học khác trùng lịch (" + conflictSchedule
                        + "). Vui lòng chọn khung giờ khác cho học viên.";
        throw new ApiException(HttpStatus.CONFLICT, "LESSON_SCHEDULE_CONFLICT", message);
    }

    private String safeLessonTitle(Lesson lesson) {
        return lesson.getTitle() == null || lesson.getTitle().isBlank() ? "Buổi học" : lesson.getTitle();
    }

    private String formatLessonSchedule(Lesson lesson) {
        return lesson.getDate().format(DISPLAY_DATE) + " " + lesson.getStartTime() + " - " + lesson.getEndTime();
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED",
                        "Only a student can confirm lessons"));
    }

    private Contract findOwnedContract(UUID contractId, UUID tutorId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> contractNotFound(contractId));
        if (contract.getTutor() == null || !tutorId.equals(contract.getTutor().getId())) {
            throw contractNotFound(contractId);
        }
        return contract;
    }

    private Lesson findOwnedLesson(UUID lessonId, UUID tutorId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> lessonNotFound(lessonId));
        Contract contract = lesson.getContract();
        if (contract == null || contract.getTutor() == null || !tutorId.equals(contract.getTutor().getId())) {
            throw lessonNotFound(lessonId);
        }
        return lesson;
    }

    private Lesson findStudentLesson(UUID lessonId, UUID studentId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> lessonNotFound(lessonId));
        Contract contract = lesson.getContract();
        if (contract == null || contract.getStudent() == null || !studentId.equals(contract.getStudent().getId())) {
            throw lessonNotFound(lessonId);
        }
        return lesson;
    }

    private void validateActiveContract(Contract contract) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_NOT_ACTIVE",
                    "Lessons can only be managed for an active contract");
        }
        if (contract.getTeachingMode() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_MODE_REQUIRED",
                    "Contract teaching mode is required");
        }
    }

    private void validateSchedule(Contract contract, LessonRequest source) {
        LocalDate date = require(source.date(), "LESSON_DATE_REQUIRED", "Date is required");
        LocalTime startTime = require(source.startTime(), "LESSON_START_TIME_REQUIRED", "Start time is required");
        LocalTime endTime = require(source.endTime(), "LESSON_END_TIME_REQUIRED", "End time is required");
        if (!startTime.isBefore(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_TIME",
                    "Start time must be before end time");
        }
        if ((contract.getStartDate() != null && date.isBefore(contract.getStartDate()))
                || (contract.getEndDate() != null && date.isAfter(contract.getEndDate()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_OUTSIDE_CONTRACT_PERIOD",
                    "Lesson date must be within the contract period");
        }
    }

    private void ensureScheduled(Lesson lesson, String action) {
        if (lesson.getStatus() != LessonStatus.SCHEDULED) {
            throw invalidStatusTransition("Only SCHEDULED lessons can be " + action);
        }
    }

    private void ensurePendingConfirmation(Lesson lesson, String action) {
        if (lesson.getStatus() != LessonStatus.PENDING_CONFIRMATION) {
            throw invalidStatusTransition("Only PENDING_CONFIRMATION lessons can be " + action);
        }
    }

    private void ensureNoBlockingComplaint(Contract contract) {
        if (hasBlockingComplaint(contract)) {
            throw new ApiException(HttpStatus.CONFLICT, "LESSON_COMPLETION_ON_HOLD",
                    "Lesson completion is on hold while a complaint is being processed");
        }
    }

    private boolean hasBlockingComplaint(Contract contract) {
        return complaintRepository.existsByContract_IdAndStatusIn(contract.getId(), BLOCKING_COMPLAINT_STATUSES);
    }

    private ApiException contractNotFound(UUID contractId) {
        return new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found: " + contractId);
    }

    private ApiException lessonNotFound(UUID lessonId) {
        return new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found: " + lessonId);
    }

    private ApiException invalidStatusTransition(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_LESSON_STATUS_TRANSITION", message);
    }

    private void publishStudentNotification(Lesson lesson, NotificationEventType eventType) {
        publishStudentNotification(lesson, eventType, null);
    }

    private void publishStudentNotification(
            Lesson lesson, NotificationEventType eventType, java.math.BigDecimal refundAmount) {
        Contract contract = lesson.getContract();
        if (contract == null || contract.getStudent() == null || contract.getStudent().getUser() == null) {
            return;
        }
        String name = lesson.getTitle() == null ? "Lesson" : lesson.getTitle();
        java.util.Map<String, String> data = refundAmount == null
                ? java.util.Map.of("name", name)
                : java.util.Map.of("name", name, "amount", refundAmount.stripTrailingZeros().toPlainString());
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                contract.getStudent().getUser().getId(),
                eventType,
                NotificationReferenceType.LESSON,
                lesson.getId(),
                data));
    }

    private void publishStudentRescheduledNotification(Lesson lesson) {
        Contract contract = lesson.getContract();
        if (contract == null || contract.getStudent() == null || contract.getStudent().getUser() == null) {
            return;
        }
        String name = lesson.getTitle() == null ? "Buổi học" : lesson.getTitle();
        String newSchedule = lesson.getDate() + " lúc " + lesson.getStartTime() + " - " + lesson.getEndTime();
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                contract.getStudent().getUser().getId(),
                NotificationEventType.LESSON_RESCHEDULED,
                NotificationReferenceType.LESSON,
                lesson.getId(),
                Map.of("name", name, "newSchedule", newSchedule)));
    }

    private java.math.BigDecimal refundPaidLesson(Lesson lesson) {
        if (paymentRepository == null || walletService == null) {
            return null;
        }
        return installmentService.findByLesson(lesson)
                .filter(installment -> installment.getStatus() == PaymentInstallmentStatus.PAID)
                .flatMap(installment -> paymentRepository.findFirstByPaymentInstallment_IdAndStatusOrderByPaidAtDesc(
                        installment.getId(), PaymentStatus.PAID))
                .map(payment -> {
                    java.math.BigDecimal amount = payment.getAmount();
                    walletService.credit(new WalletTransactionCommand(
                            lesson.getContract().getStudent().getUser().getId(),
                            amount,
                            WalletTransactionPurpose.CONTRACT_REFUND,
                            ReferenceType.LESSON,
                            lesson.getId(),
                            "Hoàn tiền buổi học đã hủy",
                            "lesson-cancel-refund-" + lesson.getId()));
                    payment.setStatus(PaymentStatus.REFUNDED);
                    payment.setRefundedAmount(amount);
                    paymentRepository.saveAndFlush(payment);
                    return amount;
                })
                .orElse(null);
    }

    private LessonResponse toResponse(Lesson lesson) {
        java.time.LocalDateTime paidAt = findPaidAt(lesson);
        return toResponse(lesson, paidAt);
    }

    private List<LessonResponse> toResponses(List<Lesson> lessons, Contract contract) {
        if (lessons.isEmpty()) return List.of();
        Map<UUID, java.time.LocalDateTime> paidAtByInstallment = new HashMap<>();
        List<com.ptutor.backend.entity.ContractPaymentInstallment> installments =
                installmentService.ensureForActiveContract(contract);
        if (paymentRepository != null) {
            paymentRepository.findAllPaidForContract(
                    contract.getId(), List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED))
                    .stream()
                    .filter(payment -> payment.getPaidAt() != null)
                    .forEach(payment -> paidAtByInstallment.merge(
                            payment.getPaymentInstallment().getId(), payment.getPaidAt(),
                            (current, candidate) -> current == null
                                    || (candidate != null && current.isBefore(candidate))
                                    ? candidate
                                    : current));
        }
        return lessons.stream()
                .map(lesson -> {
                    java.time.LocalDateTime paidAt = installmentService.findForLesson(lesson, installments)
                            .map(item -> paidAtByInstallment.get(item.getId()))
                            .orElse(null);
                    return toResponse(lesson, paidAt);
                })
                .toList();
    }

    private java.time.LocalDateTime findPaidAt(Lesson lesson) {
        if (paymentRepository == null) return null;
        return installmentService.findForLesson(lesson)
                .flatMap(installment -> paymentRepository
                        .findFirstByPaymentInstallment_IdAndStatusInOrderByPaidAtDesc(
                                installment.getId(), List.of(PaymentStatus.PAID, PaymentStatus.REFUNDED)))
                .map(Payment::getPaidAt)
                .orElse(null);
    }

    private LessonResponse toResponse(Lesson lesson, java.time.LocalDateTime paidAt) {
        RefundSummary refund = refundRequestLessonRepository.findFirstByLesson_IdOrderByCreatedAtDesc(lesson.getId())
                .map(dispute -> new RefundSummary(
                        dispute.getRefundRequest().getId(),
                        dispute.getRefundRequest().getStatus(),
                        dispute.getRefundRequest().getRefundAmount(),
                        dispute.getRefundRequest().getDescription(),
                        dispute.getRefundRequest().getCreatedAt()))
                .orElse(null);
        return lessonMapper.toResponse(lesson, paidAt, refund);
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }

    private String normalizeMeetingLink(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isBlank()) return null;
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank()) {
                return normalized;
            }
        } catch (IllegalArgumentException ignored) {
            // Converted to the domain validation error below.
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_MEETING_LINK",
                "Meeting link must be a valid HTTP or HTTPS URL");
    }

    private <T> T require(T value, String code, String message) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value;
    }
}
