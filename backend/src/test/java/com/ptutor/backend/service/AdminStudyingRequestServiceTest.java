package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.request.StudyingRequestApprovalRequest;
import com.ptutor.backend.dto.request.TeachingRequestApprovalRequest;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.event.EmbeddingRefreshRequestedEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.SubjectRepository;

@ExtendWith(MockitoExtension.class)
class AdminStudyingRequestServiceTest {

    @Mock StudyingRequestRepository studyingRequestRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock WalletService walletService;
    @Mock NotificationService notificationService;
    @Mock ApplicationEventPublisher eventPublisher;

    private AdminStudyingRequestService service;
    private UUID reviewerUserId;
    private StudyingRequest request;
    private Employee reviewer;

    @BeforeEach
    void setUp() {
        service = new AdminStudyingRequestService(
                studyingRequestRepository, paymentRepository, subjectRepository, employeeRepository,
                walletService, notificationService, eventPublisher,
                Clock.fixed(Instant.parse("2026-09-24T03:00:00Z"), ZoneOffset.UTC));
        reviewerUserId = UUID.randomUUID();
        User studentUser = User.builder()
                .firstName("Binh").lastName("Tran").email("student@example.com").build();
        studentUser.setId(UUID.randomUUID());
        Student student = Student.builder().user(studentUser).build();
        student.setId(UUID.randomUUID());
        Grade grade = Grade.builder().name("Grade 10").level(10).status(CatalogStatus.ACTIVE).build();
        grade.setId(UUID.randomUUID());
        request = StudyingRequest.builder()
                .student(student).grade(grade).title("Find a Korean tutor").note("Korean")
                .learningMode(LearningMode.ONLINE).status(RequestStatus.PENDING_REVIEW).build();
        request.setId(UUID.randomUUID());
        User reviewerUser = User.builder().firstName("Admin").lastName("One").build();
        reviewer = Employee.builder().user(reviewerUser).build();
        reviewer.setId(UUID.randomUUID());
    }

    @Test
    void approveCreatesSubjectOpensRequestAndStoresReviewMetadata() {
        var approval = new StudyingRequestApprovalRequest(
                new TeachingRequestApprovalRequest.SubjectResolution(
                        TeachingRequestApprovalRequest.Action.CREATE,
                        null, "  Korean language  ", "  Korean for beginners  "));
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(subjectRepository.findByNameIgnoreCase("Korean language")).thenReturn(Optional.empty());
        when(subjectRepository.saveAndFlush(any(Subject.class))).thenAnswer(invocation -> {
            Subject subject = invocation.getArgument(0);
            subject.setId(UUID.randomUUID());
            return subject;
        });
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.approve(reviewerUserId, request.getId(), approval);

        assertThat(response.status()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.subjectName()).isEqualTo("Korean language");
        assertThat(response.note()).isEqualTo("Korean");
        assertThat(response.reviewer().id()).isEqualTo(reviewer.getId());
        assertThat(response.reviewedAt()).isEqualTo("2026-09-24T03:00:00");
        assertThat(request.getSubject().getDescription()).isEqualTo("Korean for beginners");
        assertThat(request.getSubject().getStatus()).isEqualTo(CatalogStatus.ACTIVE);
        verify(notificationService).createStudyingRequestReviewNotification(
                request.getStudent().getUser(), request.getId().toString(), request.getTitle(), true, null, null);
        // Live from now on: the posting gets its tags and vector and can be recommended.
        verify(eventPublisher).publishEvent(new EmbeddingRefreshRequestedEvent(
                RecommendationEntityType.STUDYING_REQUEST, request.getId()));
    }

    @Test
    void approveCanLinkExistingActiveSubject() {
        Subject existing = Subject.builder().name("Korean language").status(CatalogStatus.ACTIVE).build();
        existing.setId(UUID.randomUUID());
        var approval = new StudyingRequestApprovalRequest(
                new TeachingRequestApprovalRequest.SubjectResolution(
                        TeachingRequestApprovalRequest.Action.USE_EXISTING,
                        existing.getId(), null, null));
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(subjectRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.approve(reviewerUserId, request.getId(), approval);

        assertThat(response.subjectId()).isEqualTo(existing.getId());
        assertThat(request.getSubject()).isSameAs(existing);
        verify(subjectRepository, never()).saveAndFlush(any());
    }

    @Test
    void approveRejectsDuplicateSubjectName() {
        Subject existing = Subject.builder().name("Korean language").status(CatalogStatus.ACTIVE).build();
        existing.setId(UUID.randomUUID());
        var approval = new StudyingRequestApprovalRequest(
                new TeachingRequestApprovalRequest.SubjectResolution(
                        TeachingRequestApprovalRequest.Action.CREATE, null, "korean language", null));
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(subjectRepository.findByNameIgnoreCase("korean language")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.approve(reviewerUserId, request.getId(), approval))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("SUBJECT_ALREADY_EXISTS");
                });
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_REVIEW);
    }

    @Test
    void approveRequiresSubjectResolution() {
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));

        assertThatThrownBy(() -> service.approve(reviewerUserId, request.getId(), null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("SUBJECT_RESOLUTION_REQUIRED");
                });
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectRefundsPaidFeeToWalletAndMarksPaymentRefunded() {
        User studentUser = request.getStudent().getUser();
        Payment payment = Payment.builder()
                .user(studentUser)
                .amount(new BigDecimal("50000"))
                .paymentType(PaymentType.STUDYING_REQUEST_FEE)
                .referenceType(ReferenceType.STUDYING_REQUEST)
                .referenceId(request.getId())
                .status(PaymentStatus.PAID)
                .build();
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(paymentRepository.findForRefund(
                studentUser.getId(), PaymentType.STUDYING_REQUEST_FEE,
                ReferenceType.STUDYING_REQUEST, request.getId(), PaymentStatus.PAID, PageRequest.of(0, 1)))
                .thenReturn(List.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.reject(reviewerUserId, request.getId(), "  Not a school subject  ");

        assertThat(response.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Not a school subject");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<WalletTransactionCommand> command = ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService).credit(command.capture());
        assertThat(command.getValue().amount()).isEqualByComparingTo("50000");
        assertThat(command.getValue().purpose()).isEqualTo(WalletTransactionPurpose.STUDYING_REQUEST_REFUND);
        assertThat(command.getValue().referenceId()).isEqualTo(request.getId());
        assertThat(command.getValue().idempotencyKey()).isEqualTo("studying-request-refund-" + request.getId());
        verify(eventPublisher, never()).publishEvent(any());
        verify(notificationService).createStudyingRequestReviewNotification(
                studentUser, request.getId().toString(), request.getTitle(), false,
                "Not a school subject", payment.getAmount());
    }

    @Test
    void rejectFailsWithoutPaidFeeAndDoesNotChangeRequest() {
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(paymentRepository.findForRefund(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.reject(reviewerUserId, request.getId(), "Invalid note"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_PAYMENT_NOT_FOUND");
                });

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_REVIEW);
        verify(walletService, never()).credit(any());
    }

    @Test
    void alreadyProcessedRequestCannotBeReviewedAgain() {
        request.setStatus(RequestStatus.OPEN);
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(reviewerUserId, request.getId(), "Too late"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_ALREADY_REVIEWED");
                });
        verify(walletService, never()).credit(any());
    }

    @Test
    void reviewRequiresEmployeeProfile() {
        when(studyingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(reviewerUserId, request.getId(), "Invalid note"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("EMPLOYEE_PROFILE_REQUIRED");
                });
    }
}
