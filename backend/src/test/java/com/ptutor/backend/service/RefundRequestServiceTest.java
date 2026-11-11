package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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

import com.ptutor.backend.dto.request.RefundRequestCreateRequest;
import com.ptutor.backend.dto.request.TutorRefundResponseRequest;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.RefundRequestLesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonOutcome;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.RefundRequestRepository;
import com.ptutor.backend.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class RefundRequestServiceTest {

    @Mock RefundRequestRepository refundRequestRepository;
    @Mock RefundRequestLessonRepository refundRequestLessonRepository;
    @Mock ContractRepository contractRepository;
    @Mock StudentRepository studentRepository;
    @Mock LessonRepository lessonRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private RefundRequestService service;
    private Student student;
    private User studentUser;
    private User tutorUser;
    private Contract contract;
    private Lesson pendingLesson;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T03:20:00Z"), ZoneOffset.UTC);
        RefundPolicy policy = new RefundPolicy(100, 80, 48);
        RefundAssessor assessor = new RefundAssessor(
                installmentRepository, paymentRepository, refundRequestRepository, policy);
        service = new RefundRequestService(
                refundRequestRepository, refundRequestLessonRepository, contractRepository, studentRepository,
                lessonRepository, evidenceRepository, employeeRepository, assessor, policy,
                new RefundRequestViews(refundRequestLessonRepository, evidenceRepository), eventPublisher, clock);

        studentUser = user("Ha");
        student = Student.builder().user(studentUser).build();
        student.setId(UUID.randomUUID());
        tutorUser = user("Khoa");
        Tutor tutor = Tutor.builder().user(tutorUser).build();
        tutor.setId(UUID.randomUUID());
        contract = Contract.builder().student(student).tutor(tutor).totalLession(16).status(ContractStatus.ACTIVE).build();
        contract.setId(UUID.randomUUID());
        pendingLesson = Lesson.builder().contract(contract).status(LessonStatus.PENDING_CONFIRMATION).build();
        pendingLesson.setId(UUID.randomUUID());

        Payment paid = Payment.builder().paymentType(PaymentType.TUITION_PAYMENT).status(PaymentStatus.PAID)
                .amount(new BigDecimal("2000000")).build();
        lenient().when(studentRepository.findByUser_Id(studentUser.getId())).thenReturn(Optional.of(student));
        lenient().when(contractRepository.findByIdAndParticipantUserId(contract.getId(), studentUser.getId()))
                .thenReturn(Optional.of(contract));
        lenient().when(paymentRepository.findAllForContract(contract.getId(), ReferenceType.CONTRACT))
                .thenReturn(List.of(paid));
        lenient().when(refundRequestRepository.saveAndFlush(any(RefundRequest.class))).thenAnswer(invocation -> {
            RefundRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(UUID.randomUUID());
            }
            return request;
        });
    }

    @Test
    void createsRequestWithClaimPerLessonAndNotifiesAccountantsAndTutor() {
        UUID accountantUserId = UUID.randomUUID();
        when(lessonRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(pendingLesson));
        when(employeeRepository.findActiveUserIdsForJobFunction(any())).thenReturn(List.of(accountantUserId));

        var response = service.create(studentUser.getId(), createRequest(LessonOutcome.STUDENT_ABSENT));

        assertThat(response.status()).isEqualTo(RefundRequestStatus.PENDING);
        assertThat(response.tutorResponseDeadline()).isEqualTo(LocalDateTime.of(2026, 10, 26, 3, 20));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RefundRequestLesson>> rows = ArgumentCaptor.forClass(List.class);
        verify(refundRequestLessonRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getLesson()).isEqualTo(pendingLesson);
            assertThat(row.getClaimedOutcome()).isEqualTo(LessonOutcome.STUDENT_ABSENT);
        });
        ArgumentCaptor<NotificationDomainEvent> events = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues())
                .extracting(NotificationDomainEvent::eventType, NotificationDomainEvent::recipientUserId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                NotificationEventType.REFUND_REQUEST_REVIEW_REQUIRED, accountantUserId),
                        org.assertj.core.groups.Tuple.tuple(
                                NotificationEventType.REFUND_REQUEST_SUBMITTED, tutorUser.getId()));
        // The deadline is shown to the tutor in Vietnam time.
        assertThat(events.getAllValues().get(1).data()).containsEntry("deadline", "10:20 26/10/2026");
    }

    @Test
    void claimMustBeAnAbsence() {
        assertError(() -> service.create(studentUser.getId(), createRequest(LessonOutcome.TAUGHT)),
                HttpStatus.BAD_REQUEST, "INVALID_CLAIMED_OUTCOME");
    }

    @Test
    void cannotDisputeLessonThatIsNoLongerAwaitingConfirmation() {
        pendingLesson.setStatus(LessonStatus.COMPLETED);
        when(lessonRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(pendingLesson));

        assertError(() -> service.create(studentUser.getId(), createRequest(LessonOutcome.TUTOR_ABSENT)),
                HttpStatus.CONFLICT, "LESSON_NOT_DISPUTABLE");
    }

    @Test
    void contractCanHaveOnlyOneOpenRequest() {
        when(refundRequestRepository.existsByContract_IdAndStatusIn(eq(contract.getId()), any())).thenReturn(true);

        assertError(() -> service.create(studentUser.getId(), createRequest(LessonOutcome.TUTOR_ABSENT)),
                HttpStatus.CONFLICT, "REFUND_REQUEST_ALREADY_OPEN");
    }

    @Test
    void contractWithoutPaidTuitionIsNotRefundable() {
        when(paymentRepository.findAllForContract(contract.getId(), ReferenceType.CONTRACT)).thenReturn(List.of());

        assertError(() -> service.create(studentUser.getId(), createRequest(LessonOutcome.TUTOR_ABSENT)),
                HttpStatus.CONFLICT, "CONTRACT_NOT_REFUNDABLE");
    }

    @Test
    void tutorRespondsOnceWithPositionForEachDisputedLesson() {
        RefundRequest request = openRequest();
        RefundRequestLesson row = disputedRow(request);
        when(refundRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(refundRequestLessonRepository
                .findAllByRefundRequest_IdOrderByLesson_DateAscLesson_StartTimeAsc(request.getId()))
                .thenReturn(List.of(row));

        var response = service.respond(tutorUser.getId(), request.getId(), new TutorRefundResponseRequest(
                "  I taught it online  ",
                List.of(new TutorRefundResponseRequest.LessonPosition(pendingLesson.getId(), LessonOutcome.TAUGHT)),
                null));

        assertThat(response.tutorResponse()).isEqualTo("I taught it online");
        assertThat(row.getTutorPosition()).isEqualTo(LessonOutcome.TAUGHT);
        assertThat(request.getTutorRespondedAt()).isEqualTo(LocalDateTime.of(2026, 10, 24, 3, 20));
    }

    @Test
    void tutorMustGiveAPositionForEveryDisputedLesson() {
        RefundRequest request = openRequest();
        when(refundRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(refundRequestLessonRepository
                .findAllByRefundRequest_IdOrderByLesson_DateAscLesson_StartTimeAsc(request.getId()))
                .thenReturn(List.of(disputedRow(request)));

        assertError(() -> service.respond(tutorUser.getId(), request.getId(),
                        new TutorRefundResponseRequest("No", List.of(), null)),
                HttpStatus.BAD_REQUEST, "LESSON_POSITION_REQUIRED");
    }

    @Test
    void tutorCannotRespondAfterDeadlineOrTwice() {
        RefundRequest late = openRequest();
        late.setTutorResponseDeadline(LocalDateTime.of(2026, 10, 24, 3, 0));
        RefundRequest answered = openRequest();
        answered.setTutorRespondedAt(LocalDateTime.of(2026, 10, 23, 9, 0));
        when(refundRequestRepository.findByIdForUpdate(late.getId())).thenReturn(Optional.of(late));
        when(refundRequestRepository.findByIdForUpdate(answered.getId())).thenReturn(Optional.of(answered));
        TutorRefundResponseRequest body = new TutorRefundResponseRequest("Late", List.of(), null);

        assertError(() -> service.respond(tutorUser.getId(), late.getId(), body),
                HttpStatus.CONFLICT, "TUTOR_RESPONSE_NOT_ALLOWED");
        assertError(() -> service.respond(tutorUser.getId(), answered.getId(), body),
                HttpStatus.CONFLICT, "TUTOR_RESPONSE_ALREADY_SUBMITTED");
    }

    @Test
    void studentCanOnlyCancelOwnPendingRequest() {
        RefundRequest request = openRequest();
        when(refundRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertError(() -> service.cancel(tutorUser.getId(), request.getId()),
                HttpStatus.NOT_FOUND, "REFUND_REQUEST_NOT_FOUND");
        var response = service.cancel(studentUser.getId(), request.getId());

        assertThat(response.status()).isEqualTo(RefundRequestStatus.CANCELLED);
    }

    private RefundRequestCreateRequest createRequest(LessonOutcome claim) {
        return new RefundRequestCreateRequest(contract.getId(), "The tutor did not come",
                List.of(new RefundRequestCreateRequest.DisputedLesson(pendingLesson.getId(), claim)), null);
    }

    private RefundRequest openRequest() {
        RefundRequest request = RefundRequest.builder().contract(contract).student(student)
                .description("The tutor did not come").status(RefundRequestStatus.PENDING)
                .tutorResponseDeadline(LocalDateTime.of(2026, 10, 26, 3, 20)).build();
        request.setId(UUID.randomUUID());
        return request;
    }

    private RefundRequestLesson disputedRow(RefundRequest request) {
        return RefundRequestLesson.builder().refundRequest(request).lesson(pendingLesson)
                .claimedOutcome(LessonOutcome.TUTOR_ABSENT).build();
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(status);
            assertThat(exception.getCode()).isEqualTo(code);
        });
    }

    private User user(String firstName) {
        User user = User.builder().firstName(firstName).email(firstName.toLowerCase() + "@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
