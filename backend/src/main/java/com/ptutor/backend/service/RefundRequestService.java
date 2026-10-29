package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
import com.ptutor.backend.dto.request.RefundRequestCreateRequest;
import com.ptutor.backend.dto.request.TutorRefundResponseRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.RefundRequestResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Evidence;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.RefundRequestLesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.LessonOutcome;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.RefundRequestRepository;
import com.ptutor.backend.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Refund requests from the student's and the tutor's side of a contract. A request disputes lessons the tutor
 * marked as taught that, according to the student, did not take place.
 */
@Service
@RequiredArgsConstructor
public class RefundRequestService {

    static final Set<RefundRequestStatus> OPEN_STATUSES =
            EnumSet.of(RefundRequestStatus.PENDING, RefundRequestStatus.IN_REVIEW);
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RefundRequestRepository refundRequestRepository;
    private final RefundRequestLessonRepository refundRequestLessonRepository;
    private final ContractRepository contractRepository;
    private final StudentRepository studentRepository;
    private final LessonRepository lessonRepository;
    private final EvidenceRepository evidenceRepository;
    private final EmployeeRepository employeeRepository;
    private final RefundAssessor refundAssessor;
    private final RefundPolicy refundPolicy;
    private final RefundRequestViews views;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public RefundRequestResponse create(UUID userId, RefundRequestCreateRequest source) {
        Student student = studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED",
                        "Only a student can request a refund"));
        Contract contract = contractRepository.findByIdAndParticipantUserId(source.contractId(), userId)
                .filter(value -> value.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                        "Contract not found: " + source.contractId()));
        if (contract.getStatus() == ContractStatus.PENDING || refundAssessor.paidTuition(contract.getId()).signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_NOT_REFUNDABLE",
                    "Only a signed contract with paid tuition can be refunded");
        }
        if (refundRequestRepository.existsByContract_IdAndStatusIn(contract.getId(), OPEN_STATUSES)) {
            throw alreadyOpen();
        }
        Map<UUID, LessonOutcome> claims = new LinkedHashMap<>();
        for (RefundRequestCreateRequest.DisputedLesson item : source.lessons()) {
            if (!RefundPolicy.isAbsence(item.claimedOutcome())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLAIMED_OUTCOME",
                        "A disputed lesson must be claimed as TUTOR_ABSENT or STUDENT_ABSENT");
            }
            if (claims.put(item.lessonId(), item.claimedOutcome()) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_DISPUTED_LESSON",
                        "Each lesson can be disputed only once: " + item.lessonId());
            }
        }
        List<Lesson> disputed = lockDisputableLessons(contract, claims.keySet());

        RefundRequest request;
        try {
            request = refundRequestRepository.saveAndFlush(RefundRequest.builder()
                    .contract(contract)
                    .student(student)
                    .description(source.description().strip())
                    .status(RefundRequestStatus.PENDING)
                    .tutorResponseDeadline(LocalDateTime.now(clock).plus(refundPolicy.tutorResponseWindow()))
                    .build());
        } catch (DataIntegrityViolationException exception) {
            // Another request for the same contract was created concurrently (unique open-request index).
            throw alreadyOpen();
        }
        RefundRequest saved = request;
        refundRequestLessonRepository.saveAll(disputed.stream()
                .map(lesson -> RefundRequestLesson.builder()
                        .refundRequest(saved)
                        .lesson(lesson)
                        .claimedOutcome(claims.get(lesson.getId()))
                        .build())
                .toList());
        saveEvidences(saved, student.getUser(), source.evidences());

        employeeRepository.findActiveUserIdsForJobFunction(EmployeeJobFunction.ACCOUNTANT).forEach(recipient ->
                publish(recipient, saved, NotificationEventType.REFUND_REQUEST_REVIEW_REQUIRED, Map.of()));
        publish(contract.getTutor().getUser().getId(), saved, NotificationEventType.REFUND_REQUEST_SUBMITTED,
                Map.of("deadline", displayTime(saved.getTutorResponseDeadline())));
        return views.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponse> findMine(UUID userId, RefundRequestStatus status, Pageable pageable) {
        Page<RefundRequest> requests = refundRequestRepository.findAllForStudent(userId, status, pageable);
        return PageResponse.from(requests, requests.getContent().stream().map(views::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse findMineById(UUID userId, UUID refundRequestId) {
        return views.toResponse(findDetailed(refundRequestId, request -> isStudent(request, userId)));
    }

    @Transactional
    public RefundRequestResponse cancel(UUID userId, UUID refundRequestId) {
        RefundRequest request = refundRequestRepository.findByIdForUpdate(refundRequestId)
                .filter(value -> isStudent(value, userId))
                .orElseThrow(() -> notFound(refundRequestId));
        if (request.getStatus() != RefundRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REFUND_REQUEST_STATUS_TRANSITION",
                    "Only a PENDING refund request can be cancelled");
        }
        request.setStatus(RefundRequestStatus.CANCELLED);
        return views.toResponse(refundRequestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponse> findForTutor(UUID userId, RefundRequestStatus status, Pageable pageable) {
        Page<RefundRequest> requests = refundRequestRepository.findAllForTutor(userId, status, pageable);
        return PageResponse.from(requests, requests.getContent().stream().map(views::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse findForTutorById(UUID userId, UUID refundRequestId) {
        return views.toResponse(findDetailed(refundRequestId, request -> isTutor(request, userId)));
    }

    @Transactional
    public RefundRequestResponse respond(UUID userId, UUID refundRequestId, TutorRefundResponseRequest source) {
        RefundRequest request = refundRequestRepository.findByIdForUpdate(refundRequestId)
                .filter(value -> isTutor(value, userId))
                .orElseThrow(() -> notFound(refundRequestId));
        if (request.getTutorRespondedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "TUTOR_RESPONSE_ALREADY_SUBMITTED",
                    "A response has already been submitted for this refund request");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!OPEN_STATUSES.contains(request.getStatus()) || now.isAfter(request.getTutorResponseDeadline())) {
            throw new ApiException(HttpStatus.CONFLICT, "TUTOR_RESPONSE_NOT_ALLOWED",
                    "The refund request no longer accepts a tutor response");
        }

        List<RefundRequestLesson> disputed = views.disputedLessons(request);
        Map<UUID, LessonOutcome> positions = new HashMap<>();
        for (TutorRefundResponseRequest.LessonPosition position : source.lessonPositions()) {
            positions.put(position.lessonId(), position.position());
        }
        Set<UUID> disputedIds = disputed.stream().map(item -> item.getLesson().getId()).collect(Collectors.toSet());
        if (positions.size() != source.lessonPositions().size() || !positions.keySet().equals(disputedIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_POSITION_REQUIRED",
                    "Give exactly one position for every disputed lesson");
        }
        disputed.forEach(item -> item.setTutorPosition(positions.get(item.getLesson().getId())));
        refundRequestLessonRepository.saveAll(disputed);

        request.setTutorResponse(source.message().strip());
        request.setTutorRespondedAt(now);
        RefundRequest saved = refundRequestRepository.saveAndFlush(request);
        saveEvidences(saved, saved.getContract().getTutor().getUser(), source.evidences());
        if (saved.getEmployee() != null && saved.getEmployee().getUser() != null) {
            publish(saved.getEmployee().getUser().getId(), saved,
                    NotificationEventType.REFUND_REQUEST_TUTOR_RESPONDED, Map.of());
        }
        return views.toResponse(saved);
    }

    private List<Lesson> lockDisputableLessons(Contract contract, Set<UUID> ids) {
        Map<UUID, Lesson> lessons = lessonRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Lesson::getId, Function.identity()));
        for (UUID id : ids) {
            Lesson lesson = lessons.get(id);
            if (lesson == null || lesson.getContract() == null
                    || !contract.getId().equals(lesson.getContract().getId())
                    || lesson.getStatus() != LessonStatus.PENDING_CONFIRMATION) {
                throw new ApiException(HttpStatus.CONFLICT, "LESSON_NOT_DISPUTABLE",
                        "Only lessons of this contract awaiting confirmation can be disputed: " + id);
            }
        }
        return ids.stream().map(lessons::get).toList();
    }

    private void saveEvidences(RefundRequest request, User uploadedBy, List<ComplaintCreateRequest.Evidence> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        evidenceRepository.saveAll(source.stream()
                .map(value -> Evidence.builder()
                        .refundRequest(request)
                        .fileUrl(value.fileUrl().strip())
                        .fileType(value.fileType().strip())
                        .uploadedBy(uploadedBy)
                        .build())
                .toList());
        evidenceRepository.flush();
    }

    private RefundRequest findDetailed(UUID refundRequestId, Predicate<RefundRequest> canSee) {
        return refundRequestRepository.findDetailedById(refundRequestId)
                .filter(canSee)
                .orElseThrow(() -> notFound(refundRequestId));
    }

    private boolean isStudent(RefundRequest request, UUID userId) {
        return userId.equals(request.getStudent().getUser().getId());
    }

    private boolean isTutor(RefundRequest request, UUID userId) {
        return userId.equals(request.getContract().getTutor().getUser().getId());
    }

    private void publish(UUID recipientUserId, RefundRequest request, NotificationEventType eventType,
            Map<String, String> data) {
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                recipientUserId, eventType, NotificationReferenceType.REFUND_REQUEST, request.getId(), data));
    }

    // Business timestamps follow the injected clock's zone; notifications show Vietnam time.
    private String displayTime(LocalDateTime value) {
        return value.atZone(clock.getZone()).withZoneSameInstant(DISPLAY_ZONE).format(DEADLINE_FORMAT);
    }

    static ApiException notFound(UUID refundRequestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "REFUND_REQUEST_NOT_FOUND",
                "Refund request not found: " + refundRequestId);
    }

    private static ApiException alreadyOpen() {
        return new ApiException(HttpStatus.CONFLICT, "REFUND_REQUEST_ALREADY_OPEN",
                "This contract already has a refund request waiting for a decision");
    }

    static String amountText(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
