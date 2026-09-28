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
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.response.PaymentResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentMethod;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.WalletTransactionRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.security.VnPayService;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock StudyingRequestRepository studyingRequestRepository;
    @Mock TeachingRequestRepository teachingRequestRepository;
    @Mock ContractRepository contractRepository;
    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock StudentRepository studentRepository;
    @Mock TutorRepository tutorRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock WalletService walletService;
    @Mock StudyingRequestService studyingRequestService;
    @Mock TeachingRequestService teachingRequestService;
    @Mock ContractPaymentInstallmentService installmentService;
    @Mock VnPayService vnPayService;
    @Mock ApplicationEventPublisher eventPublisher;

    private PaymentService service;
    private UUID userId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                paymentRepository,
                studyingRequestRepository,
                teachingRequestRepository,
                contractRepository,
                installmentRepository,
                studentRepository,
                tutorRepository,
                walletTransactionRepository,
                withdrawalRequestRepository,
                walletService,
                studyingRequestService,
                teachingRequestService,
                installmentService,
                vnPayService,
                Clock.fixed(Instant.parse("2026-09-26T03:00:00Z"), ZoneOffset.UTC),
                eventPublisher);
        ReflectionTestUtils.setField(service, "studyingRequestFee", new BigDecimal("50000"));
        ReflectionTestUtils.setField(service, "teachingRequestFee", new BigDecimal("70000"));

        userId = UUID.randomUUID();
        requestId = UUID.randomUUID();
    }

    @Test
    void walletPaymentDebitsOnceActivatesRequestAndPublishesSuccess() {
        givenDraftStudyingRequest();
        when(findStudyingPayment(PaymentStatus.PAID)).thenReturn(Optional.empty());
        when(findStudyingPayment(PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });

        PaymentResponse response = service.payStudyingRequestWithWallet(userId, requestId);

        ArgumentCaptor<WalletTransactionCommand> walletCommand =
                ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService).debit(walletCommand.capture());
        assertThat(walletCommand.getValue().userId()).isEqualTo(userId);
        assertThat(walletCommand.getValue().amount()).isEqualByComparingTo("50000");
        assertThat(walletCommand.getValue().purpose()).isEqualTo(WalletTransactionPurpose.STUDYING_REQUEST_FEE);
        assertThat(walletCommand.getValue().referenceType()).isEqualTo(ReferenceType.STUDYING_REQUEST);
        assertThat(walletCommand.getValue().referenceId()).isEqualTo(requestId);
        assertThat(walletCommand.getValue().idempotencyKey()).isEqualTo("payment:studying-request:" + requestId);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
        assertThat(paymentCaptor.getValue().getPaymentType()).isEqualTo(PaymentType.STUDYING_REQUEST_FEE);
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentCaptor.getValue().getPaidAt()).isNotNull();
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET);
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verify(studyingRequestService).activateAfterPayment(requestId);

        ArgumentCaptor<NotificationDomainEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipientUserId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(NotificationEventType.PAYMENT_SUCCEEDED);
    }

    @Test
    void insufficientWalletBalanceDoesNotCreatePaymentOrActivateRequest() {
        givenDraftStudyingRequest();
        when(findStudyingPayment(PaymentStatus.PAID)).thenReturn(Optional.empty());
        when(walletService.debit(any())).thenThrow(new ApiException(
                HttpStatus.CONFLICT, "INSUFFICIENT_WALLET_BALANCE", "Wallet balance is insufficient"));

        assertThatThrownBy(() -> service.payStudyingRequestWithWallet(userId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_WALLET_BALANCE");
                });

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(studyingRequestService, never()).activateAfterPayment(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retryReturnsExistingPaidPaymentWithoutDebitingWalletAgain() {
        givenDraftStudyingRequest();
        User user = new User();
        user.setId(userId);
        Payment existing = Payment.builder()
                .user(user)
                .amount(new BigDecimal("50000"))
                .paymentMethod(PaymentMethod.WALLET)
                .paymentType(PaymentType.STUDYING_REQUEST_FEE)
                .status(PaymentStatus.PAID)
                .referenceType(ReferenceType.STUDYING_REQUEST)
                .referenceId(requestId)
                .transactionCode("PW-EXISTING")
                .build();
        existing.setId(UUID.randomUUID());
        when(findStudyingPayment(PaymentStatus.PAID)).thenReturn(Optional.of(existing));

        PaymentResponse response = service.payStudyingRequestWithWallet(userId, requestId);

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verify(walletService, never()).debit(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(studyingRequestService, never()).activateAfterPayment(any());
    }

    @Test
    void teachingRequestCanBePaidWithWallet() {
        UUID tutorId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Tutor tutor = Tutor.builder().user(user).build();
        tutor.setId(tutorId);
        TeachingRequest teachingRequest = TeachingRequest.builder()
                .tutor(tutor)
                .status(RequestStatus.DRAFT)
                .build();
        teachingRequest.setId(requestId);

        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));
        when(teachingRequestRepository.findByIdAndTutor_Id(requestId, tutorId))
                .thenReturn(Optional.of(teachingRequest));
        when(findTeachingPayment(PaymentStatus.PAID)).thenReturn(Optional.empty());
        when(findTeachingPayment(PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });

        PaymentResponse response = service.payTeachingRequestWithWallet(userId, requestId);

        ArgumentCaptor<WalletTransactionCommand> walletCommand =
                ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService).debit(walletCommand.capture());
        assertThat(walletCommand.getValue().amount()).isEqualByComparingTo("70000");
        assertThat(walletCommand.getValue().purpose()).isEqualTo(WalletTransactionPurpose.TEACHING_REQUEST_FEE);
        assertThat(walletCommand.getValue().referenceType()).isEqualTo(ReferenceType.TEACHING_REQUEST);
        assertThat(walletCommand.getValue().referenceId()).isEqualTo(requestId);
        assertThat(walletCommand.getValue().idempotencyKey()).isEqualTo("payment:teaching-request:" + requestId);
        assertThat(response.paymentType()).isEqualTo(PaymentType.TEACHING_REQUEST_FEE);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET);
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        verify(teachingRequestService).activateAfterPayment(requestId);
        verify(eventPublisher).publishEvent(any(NotificationDomainEvent.class));
    }

    @Test
    void tuitionInstallmentCanBePaidWithWallet() {
        UUID studentId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Student student = Student.builder().user(user).build();
        student.setId(studentId);
        Contract contract = Contract.builder()
                .student(student)
                .status(ContractStatus.ACTIVE)
                .build();
        contract.setId(contractId);
        ContractPaymentInstallment installment = ContractPaymentInstallment.builder()
                .contract(contract)
                .paymentPeriod(PaymentPeriod.MONTHLY)
                .amount(new BigDecimal("1200000"))
                .status(PaymentInstallmentStatus.PENDING)
                .build();
        installment.setId(installmentId);

        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(contractRepository.findByIdAndParticipantUserId(contractId, userId))
                .thenReturn(Optional.of(contract));
        when(installmentRepository.findForStudent(installmentId, contractId, userId))
                .thenReturn(Optional.of(installment));
        when(findInstallmentPayment(installmentId, PaymentStatus.PAID)).thenReturn(Optional.empty());
        when(findInstallmentPayment(installmentId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });

        PaymentResponse response = service.payTuitionWithWallet(userId, contractId, installmentId);

        ArgumentCaptor<WalletTransactionCommand> walletCommand =
                ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService).debit(walletCommand.capture());
        assertThat(walletCommand.getValue().amount()).isEqualByComparingTo("1200000");
        assertThat(walletCommand.getValue().purpose()).isEqualTo(WalletTransactionPurpose.TUITION_PAYMENT);
        assertThat(walletCommand.getValue().referenceType()).isEqualTo(ReferenceType.CONTRACT);
        assertThat(walletCommand.getValue().referenceId()).isEqualTo(contractId);
        assertThat(walletCommand.getValue().idempotencyKey()).isEqualTo("payment:installment:" + installmentId);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentInstallment()).isEqualTo(installment);
        assertThat(response.paymentType()).isEqualTo(PaymentType.TUITION_PAYMENT);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.WALLET);
        assertThat(installment.getStatus()).isEqualTo(PaymentInstallmentStatus.PAID);
        verify(installmentRepository).save(installment);
        verify(eventPublisher).publishEvent(any(NotificationDomainEvent.class));
    }

    private void givenDraftStudyingRequest() {
        UUID studentId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Student student = Student.builder().user(user).build();
        student.setId(studentId);
        StudyingRequest studyingRequest = StudyingRequest.builder()
                .student(student)
                .status(RequestStatus.DRAFT)
                .build();
        studyingRequest.setId(requestId);

        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(studyingRequestRepository.findByIdAndStudent_Id(requestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
    }

    private Optional<Payment> findStudyingPayment(PaymentStatus status) {
        return paymentRepository
                .findFirstByUser_IdAndPaymentTypeAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        PaymentType.STUDYING_REQUEST_FEE,
                        ReferenceType.STUDYING_REQUEST,
                        requestId,
                        status);
    }

    private Optional<Payment> findTeachingPayment(PaymentStatus status) {
        return paymentRepository
                .findFirstByUser_IdAndPaymentTypeAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        PaymentType.TEACHING_REQUEST_FEE,
                        ReferenceType.TEACHING_REQUEST,
                        requestId,
                        status);
    }

    private Optional<Payment> findInstallmentPayment(UUID installmentId, PaymentStatus status) {
        return paymentRepository.findFirstByUser_IdAndPaymentInstallment_IdAndStatusOrderByCreatedAtDesc(
                userId, installmentId, status);
    }
}
