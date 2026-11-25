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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.request.TeachingRequestApprovalRequest;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;

@ExtendWith(MockitoExtension.class)
class AdminTeachingRequestServiceTest {

    @Mock TeachingRequestRepository teachingRequestRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock WalletService walletService;
    @Mock NotificationService notificationService;

    private AdminTeachingRequestService service;
    private UUID reviewerUserId;
    private TeachingRequest request;
    private Employee reviewer;

    @BeforeEach
    void setUp() {
        service = new AdminTeachingRequestService(
                teachingRequestRepository, paymentRepository, subjectRepository, employeeRepository,
                walletService, notificationService,
                Clock.fixed(Instant.parse("2026-09-24T03:00:00Z"), ZoneOffset.UTC));
        reviewerUserId = UUID.randomUUID();
        User tutorUser = User.builder()
                .firstName("An").lastName("Nguyen").email("tutor@example.com").build();
        tutorUser.setId(UUID.randomUUID());
        Tutor tutor = Tutor.builder().user(tutorUser).build();
        tutor.setId(UUID.randomUUID());
        Subject subject = Subject.builder().name("Mathematics").status(CatalogStatus.ACTIVE).build();
        subject.setId(UUID.randomUUID());
        request = TeachingRequest.builder()
                .tutor(tutor).title("Math tutor").note("Please review")
                .subject(subject)
                .expectedPrice(new BigDecimal("150000"))
                .teachingMode(TeachingMode.ONLINE).status(RequestStatus.PENDING_REVIEW).build();
        request.setId(UUID.randomUUID());
        User reviewerUser = User.builder().firstName("Admin").lastName("One").build();
        reviewer = Employee.builder().user(reviewerUser).build();
        reviewer.setId(UUID.randomUUID());
    }

    @Test
    void approveOpensPendingRequestAndStoresReviewMetadata() {
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.approve(reviewerUserId, request.getId(), null);

        assertThat(response.status()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.reviewer().id()).isEqualTo(reviewer.getId());
        assertThat(response.reviewedAt()).isEqualTo("2026-09-24T03:00:00");
        verify(notificationService).createTeachingRequestReviewNotification(
                request.getTutor().getUser(), request.getId().toString(), request.getTitle(), true, null, null);
    }

    @Test
    void approveCustomSubjectCreatesAndLinksActiveSubject() {
        request.setSubject(null);
        request.setCustomSubjectName("AI basics");
        var approval = new TeachingRequestApprovalRequest(
                new TeachingRequestApprovalRequest.SubjectResolution(
                        TeachingRequestApprovalRequest.Action.CREATE,
                        null, "  Artificial Intelligence  ", "  AI fundamentals  "));
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(subjectRepository.findByNameIgnoreCase("Artificial Intelligence")).thenReturn(Optional.empty());
        when(subjectRepository.saveAndFlush(any(Subject.class))).thenAnswer(invocation -> {
            Subject subject = invocation.getArgument(0);
            subject.setId(UUID.randomUUID());
            return subject;
        });
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.approve(reviewerUserId, request.getId(), approval);

        assertThat(response.status()).isEqualTo(RequestStatus.OPEN);
        assertThat(request.getSubject().getName()).isEqualTo("Artificial Intelligence");
        assertThat(request.getSubject().getDescription()).isEqualTo("AI fundamentals");
        assertThat(request.getSubject().getStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(response.customSubjectName()).isEqualTo("AI basics");
    }

    @Test
    void approveCustomSubjectCanLinkExistingActiveSubject() {
        request.setSubject(null);
        request.setCustomSubjectName("AI basics");
        Subject existing = Subject.builder().name("Artificial Intelligence").status(CatalogStatus.ACTIVE).build();
        existing.setId(UUID.randomUUID());
        var approval = new TeachingRequestApprovalRequest(
                new TeachingRequestApprovalRequest.SubjectResolution(
                        TeachingRequestApprovalRequest.Action.USE_EXISTING,
                        existing.getId(), null, null));
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(subjectRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.approve(reviewerUserId, request.getId(), approval);

        assertThat(response.subjectId()).isEqualTo(existing.getId());
        assertThat(request.getSubject()).isSameAs(existing);
        verify(subjectRepository, never()).saveAndFlush(any());
    }

    @Test
    void approveCustomSubjectRequiresResolution() {
        request.setSubject(null);
        request.setCustomSubjectName("AI basics");
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));

        assertThatThrownBy(() -> service.approve(reviewerUserId, request.getId(), null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("SUBJECT_RESOLUTION_REQUIRED");
                });
    }

    @Test
    void rejectRefundsPaidFeeToWalletAndMarksPaymentRefunded() {
        Payment payment = Payment.builder()
                .user(request.getTutor().getUser())
                .amount(new BigDecimal("50000"))
                .paymentType(PaymentType.TEACHING_REQUEST_FEE)
                .referenceType(ReferenceType.TEACHING_REQUEST)
                .referenceId(request.getId())
                .status(PaymentStatus.PAID)
                .build();
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(paymentRepository.findForRefund(
                request.getTutor().getUser().getId(), PaymentType.TEACHING_REQUEST_FEE,
                ReferenceType.TEACHING_REQUEST, request.getId(), PaymentStatus.PAID, PageRequest.of(0, 1)))
                .thenReturn(List.of(payment));
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.reject(reviewerUserId, request.getId(), "  Invalid contact details  ");

        assertThat(response.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Invalid contact details");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<WalletTransactionCommand> command = ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService).credit(command.capture());
        assertThat(command.getValue().amount()).isEqualByComparingTo("50000");
        assertThat(command.getValue().purpose()).isEqualTo(WalletTransactionPurpose.TEACHING_REQUEST_REFUND);
        assertThat(command.getValue().referenceId()).isEqualTo(request.getId());
        assertThat(command.getValue().idempotencyKey()).isEqualTo("teaching-request-refund-" + request.getId());
    }

    @Test
    void rejectFailsWithoutPaidFeeAndDoesNotChangeRequest() {
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(paymentRepository.findForRefund(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.reject(reviewerUserId, request.getId(), "Invalid note"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("TEACHING_REQUEST_PAYMENT_NOT_FOUND");
                });

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_REVIEW);
        verify(walletService, never()).credit(any());
    }

    @Test
    void alreadyProcessedRequestCannotBeReviewedAgain() {
        request.setStatus(RequestStatus.REJECTED);
        when(teachingRequestRepository.findByIdForReview(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(reviewerUserId, request.getId(), null))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("TEACHING_REQUEST_ALREADY_REVIEWED");
                });
    }
}
