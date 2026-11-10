package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.request.RefundApprovalRequest;
import com.ptutor.backend.dto.response.WalletTransactionResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.RefundRequestLesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.WalletTransaction;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.LessonOutcome;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.RefundRequestRepository;
import com.ptutor.backend.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class AdminRefundRequestServiceTest {

    @Mock RefundRequestRepository refundRequestRepository;
    @Mock RefundRequestLessonRepository refundRequestLessonRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock LessonRepository lessonRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock WalletService walletService;
    @Mock ApplicationEventPublisher eventPublisher;

    private AdminRefundRequestService service;
    private UUID reviewerUserId;
    private Employee reviewer;
    private Contract contract;
    private Payment payment;
    private Lesson tutorMissed;
    private Lesson studentSkipped;
    private RefundRequest request;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC);
        RefundPolicy policy = new RefundPolicy(100, 80, 48);
        RefundAssessor assessor = new RefundAssessor(installmentRepository, paymentRepository, refundRequestRepository, policy);
        RefundRequestViews views = new RefundRequestViews(refundRequestLessonRepository, evidenceRepository);
        service = new AdminRefundRequestService(
                refundRequestRepository, refundRequestLessonRepository, employeeRepository, complaintRepository,
                lessonRepository, paymentRepository, walletTransactionRepository, walletService, assessor, policy,
                views, eventPublisher, clock);

        reviewerUserId = UUID.randomUUID();
        reviewer = Employee.builder().user(user(reviewerUserId, "Lan")).role(EmployeeRole.EMPLOYEE).build();
        reviewer.setId(UUID.randomUUID());
        Student student = Student.builder().user(user(UUID.randomUUID(), "Ha")).build();
        student.setId(UUID.randomUUID());
        Tutor tutor = Tutor.builder().user(user(UUID.randomUUID(), "Khoa")).build();
        tutor.setId(UUID.randomUUID());
        contract = Contract.builder().student(student).tutor(tutor).price(new BigDecimal("2000000"))
                .paymentPeriod(PaymentPeriod.MONTHLY).totalLession(16).status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 11, 30)).build();
        contract.setId(UUID.randomUUID());

        // 2 × 2,000,000 for 16 lessons: 250,000 per lesson; installment 1 paid.
        ContractPaymentInstallment first = installment(1, PaymentInstallmentStatus.PAID);
        ContractPaymentInstallment second = installment(2, PaymentInstallmentStatus.PENDING);
        payment = Payment.builder().paymentType(PaymentType.TUITION_PAYMENT).status(PaymentStatus.PAID)
                .amount(new BigDecimal("2000000")).paymentInstallment(first)
                .paidAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
        payment.setId(UUID.randomUUID());

        tutorMissed = lesson();
        studentSkipped = lesson();
        request = RefundRequest.builder().contract(contract).student(student).employee(reviewer)
                .description("Two lessons did not happen").status(RefundRequestStatus.IN_REVIEW)
                .tutorResponseDeadline(LocalDateTime.of(2026, 10, 26, 10, 20))
                .tutorRespondedAt(LocalDateTime.of(2026, 10, 24, 22, 47)).build();
        request.setId(UUID.randomUUID());
        List<RefundRequestLesson> disputed = List.of(
                row(tutorMissed, LessonOutcome.TUTOR_ABSENT), row(studentSkipped, LessonOutcome.TUTOR_ABSENT));

        lenient().when(refundRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        lenient().when(refundRequestLessonRepository
                .findAllByRefundRequest_IdOrderByLesson_DateAscLesson_StartTimeAsc(request.getId()))
                .thenReturn(disputed);
        lenient().when(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()))
                .thenReturn(List.of(first, second));
        lenient().when(paymentRepository.findAllForContract(contract.getId(), ReferenceType.CONTRACT))
                .thenReturn(List.of(payment));
        lenient().when(refundRequestRepository.sumRefundedAmountForContract(contract.getId()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(refundRequestRepository.saveAndFlush(any(RefundRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvesPerLessonRefundAndPaysTutorShareOfSkippedLesson() {
        UUID studentTransaction = UUID.randomUUID();
        UUID tutorTransaction = UUID.randomUUID();
        when(lessonRepository.findAllByIdForUpdate(any())).thenReturn(List.of(tutorMissed, studentSkipped));
        when(walletService.credit(any()))
                .thenReturn(walletResponse(studentTransaction), walletResponse(tutorTransaction));
        when(walletTransactionRepository.getReferenceById(any())).thenReturn(new WalletTransaction());

        var response = service.approve(reviewerUserId, request.getId(), approval(new BigDecimal("450000")));

        ArgumentCaptor<WalletTransactionCommand> credits = ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService, times(2)).credit(credits.capture());
        WalletTransactionCommand toStudent = credits.getAllValues().get(0);
        WalletTransactionCommand toTutor = credits.getAllValues().get(1);
        assertThat(toStudent.userId()).isEqualTo(contract.getStudent().getUser().getId());
        assertThat(toStudent.amount()).isEqualByComparingTo("450000");
        assertThat(toStudent.purpose()).isEqualTo(WalletTransactionPurpose.CONTRACT_REFUND);
        assertThat(toTutor.userId()).isEqualTo(contract.getTutor().getUser().getId());
        assertThat(toTutor.amount()).isEqualByComparingTo("50000");
        assertThat(toTutor.purpose()).isEqualTo(WalletTransactionPurpose.TUTOR_EARNING);
        assertThat(tutorMissed.getStatus()).isEqualTo(LessonStatus.CANCELLED);
        assertThat(studentSkipped.getStatus()).isEqualTo(LessonStatus.CANCELLED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("450000");
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REFUNDED);
        assertThat(response.calculation().refundAmount()).isEqualByComparingTo("450000");
        assertThat(response.calculation().tutorShareAmount()).isEqualByComparingTo("50000");
        ArgumentCaptor<NotificationDomainEvent> events = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(NotificationDomainEvent::eventType)
                .containsOnly(NotificationEventType.REFUND_REQUEST_APPROVED);
    }

    @Test
    void lessonFoundTaughtIsCompletedAndRefundsNothing() {
        when(lessonRepository.findAllByIdForUpdate(any())).thenReturn(List.of(tutorMissed, studentSkipped));
        when(walletService.credit(any())).thenReturn(walletResponse(UUID.randomUUID()));
        when(walletTransactionRepository.getReferenceById(any())).thenReturn(new WalletTransaction());
        RefundApprovalRequest oneTaught = new RefundApprovalRequest(new BigDecimal("250000"), List.of(
                new RefundApprovalRequest.LessonDecision(tutorMissed.getId(), LessonOutcome.TUTOR_ABSENT),
                new RefundApprovalRequest.LessonDecision(studentSkipped.getId(), LessonOutcome.TAUGHT)), null);

        service.approve(reviewerUserId, request.getId(), oneTaught);

        verify(walletService, times(1)).credit(any());
        assertThat(tutorMissed.getStatus()).isEqualTo(LessonStatus.CANCELLED);
        assertThat(studentSkipped.getStatus()).isEqualTo(LessonStatus.COMPLETED);
    }

    @Test
    void allLessonsTaughtMustBeRejectedInstead() {
        RefundApprovalRequest allTaught = new RefundApprovalRequest(BigDecimal.ZERO, List.of(
                new RefundApprovalRequest.LessonDecision(tutorMissed.getId(), LessonOutcome.TAUGHT),
                new RefundApprovalRequest.LessonDecision(studentSkipped.getId(), LessonOutcome.TAUGHT)), null);

        assertError(() -> service.approve(reviewerUserId, request.getId(), allTaught),
                HttpStatus.CONFLICT, "NOTHING_TO_REFUND");
        verify(walletService, never()).credit(any());
    }

    @Test
    void cannotApproveWhileTutorStillHasTimeToRespond() {
        request.setTutorRespondedAt(null);
        request.setTutorResponseDeadline(LocalDateTime.of(2026, 10, 28, 9, 40));

        assertError(() -> service.approve(reviewerUserId, request.getId(), approval(new BigDecimal("450000"))),
                HttpStatus.CONFLICT, "TUTOR_RESPONSE_WINDOW_OPEN");
    }

    @Test
    void cannotApproveWhileContractHasOpenComplaint() {
        when(complaintRepository.existsByContract_IdAndStatusIn(eq(contract.getId()), any())).thenReturn(true);

        assertError(() -> service.approve(reviewerUserId, request.getId(), approval(new BigDecimal("450000"))),
                HttpStatus.CONFLICT, "CONTRACT_HAS_OPEN_COMPLAINT");
    }

    @Test
    void rejectsStaleExpectedAmountWithoutTouchingTheWallet() {
        assertError(() -> service.approve(reviewerUserId, request.getId(), approval(new BigDecimal("500000"))),
                HttpStatus.CONFLICT, "REFUND_AMOUNT_CHANGED");
        verify(walletService, never()).credit(any());
    }

    @Test
    void requiresDecisionForEveryDisputedLesson() {
        RefundApprovalRequest partial = new RefundApprovalRequest(new BigDecimal("250000"), List.of(
                new RefundApprovalRequest.LessonDecision(tutorMissed.getId(), LessonOutcome.TUTOR_ABSENT)), null);

        assertError(() -> service.approve(reviewerUserId, request.getId(), partial),
                HttpStatus.BAD_REQUEST, "LESSON_DECISION_REQUIRED");
    }

    @Test
    void onlyAssignedEmployeeCanApprove() {
        assertError(() -> service.approve(UUID.randomUUID(), request.getId(), approval(new BigDecimal("450000"))),
                HttpStatus.FORBIDDEN, "REFUND_REQUEST_REVIEW_FORBIDDEN");
    }

    @Test
    void rejectingKeepsWalletsAndNotifiesBothParticipants() {
        var response = service.reject(reviewerUserId, request.getId(), "  Both lessons happened  ");

        assertThat(response.status()).isEqualTo(RefundRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Both lessons happened");
        verify(walletService, never()).credit(any());
        verify(eventPublisher, times(2)).publishEvent(any(NotificationDomainEvent.class));
    }

    @Test
    void detailPreviewsStudentClaimsBeforeAnyDecision() {
        request.setTutorRespondedAt(null);
        request.setTutorResponseDeadline(LocalDateTime.of(2026, 10, 28, 9, 40));
        when(refundRequestRepository.findDetailedById(request.getId())).thenReturn(Optional.of(request));

        var response = service.findById(request.getId());

        assertThat(response.calculation().blockers()).containsExactly("TUTOR_RESPONSE_WINDOW_OPEN");
        assertThat(response.calculation().undecidedLessons()).isEqualTo(2);
        assertThat(response.calculation().unitPrice()).isEqualByComparingTo("250000");
        assertThat(response.calculation().refundAmount()).isEqualByComparingTo("500000");
        assertThat(response.calculation().studentAbsentRate()).isEqualTo(80);
    }

    private RefundApprovalRequest approval(BigDecimal expected) {
        return new RefundApprovalRequest(expected, List.of(
                new RefundApprovalRequest.LessonDecision(tutorMissed.getId(), LessonOutcome.TUTOR_ABSENT),
                new RefundApprovalRequest.LessonDecision(studentSkipped.getId(), LessonOutcome.STUDENT_ABSENT)),
                "Lesson 2: student did not join");
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(status);
            assertThat(exception.getCode()).isEqualTo(code);
        });
    }

    private WalletTransactionResponse walletResponse(UUID transactionId) {
        return new WalletTransactionResponse(transactionId, UUID.randomUUID(), null, null, null, null, null,
                ReferenceType.REFUND_REQUEST, request.getId(), null, null, null, null);
    }

    private ContractPaymentInstallment installment(int sequence, PaymentInstallmentStatus status) {
        ContractPaymentInstallment installment = ContractPaymentInstallment.builder().contract(contract)
                .sequenceNumber(sequence).paymentPeriod(PaymentPeriod.MONTHLY)
                .amount(new BigDecimal("2000000")).status(status).build();
        installment.setId(UUID.randomUUID());
        return installment;
    }

    private Lesson lesson() {
        Lesson lesson = Lesson.builder().contract(contract).date(LocalDate.of(2026, 10, 21))
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(21, 0))
                .status(LessonStatus.PENDING_CONFIRMATION).build();
        lesson.setId(UUID.randomUUID());
        return lesson;
    }

    private RefundRequestLesson row(Lesson lesson, LessonOutcome claim) {
        RefundRequestLesson row = RefundRequestLesson.builder().refundRequest(request).lesson(lesson)
                .claimedOutcome(claim).build();
        row.setId(UUID.randomUUID());
        return row;
    }

    private User user(UUID id, String firstName) {
        User user = User.builder().firstName(firstName).email(firstName.toLowerCase() + "@example.com")
                .status(UserStatus.ACTIVE).build();
        user.setId(id);
        return user;
    }
}
