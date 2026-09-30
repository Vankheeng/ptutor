package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
import com.ptutor.backend.dto.request.VnPayPaymentRequest;
import com.ptutor.backend.dto.request.WalletTopUpRequest;
import com.ptutor.backend.dto.response.PaymentInitiationResponse;
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
import com.ptutor.backend.entity.enums.NotificationReferenceType;
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
    void createWalletTopUpCreatesPendingVnPayPayment() {
        UUID paymentId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 26, 3, 15);
        when(vnPayService.expiresAt()).thenReturn(expiresAt);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(paymentId);
            }
            return payment;
        });
        when(vnPayService.createPaymentUrl(any(Payment.class), eq("203.0.113.10"),
                any(VnPayPaymentRequest.class))).thenReturn("https://sandbox.vnpayment.vn/top-up");

        PaymentInitiationResponse response = service.createWalletTopUp(
                userId, new WalletTopUpRequest(new BigDecimal("250000"), "NCB", "vn"), "203.0.113.10");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).saveAndFlush(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertThat(payment.getId()).isEqualTo(paymentId);
        assertThat(payment.getUser().getId()).isEqualTo(userId);
        assertThat(payment.getAmount()).isEqualByComparingTo("250000");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(payment.getPaymentType()).isEqualTo(PaymentType.WALLET_TOP_UP);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getReferenceType()).isEqualTo(ReferenceType.PAYMENT);
        assertThat(payment.getReferenceId()).isEqualTo(paymentId);
        assertThat(payment.getExpiresAt()).isEqualTo(expiresAt);

        ArgumentCaptor<VnPayPaymentRequest> vnPayRequestCaptor =
                ArgumentCaptor.forClass(VnPayPaymentRequest.class);
        verify(vnPayService).createPaymentUrl(eq(payment), eq("203.0.113.10"), vnPayRequestCaptor.capture());
        assertThat(vnPayRequestCaptor.getValue().bankCode()).isEqualTo("NCB");
        assertThat(vnPayRequestCaptor.getValue().locale()).isEqualTo("vn");

        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(response.paymentType()).isEqualTo(PaymentType.WALLET_TOP_UP);
        assertThat(response.amount()).isEqualByComparingTo("250000");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.paymentUrl()).isEqualTo("https://sandbox.vnpayment.vn/top-up");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(walletService, never()).credit(any());
    }

    @Test
    void createWalletTopUpRejectsAmountsOutsideAllowedWholeVndRange() {
        assertInvalidTopUpAmount("9999");
        assertInvalidTopUpAmount("100000001");
        assertInvalidTopUpAmount("10000.50");

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(vnPayService, never()).createPaymentUrl(any(), any(), any());
    }

    @Test
    void successfulWalletTopUpIpnCreditsWalletAndPublishesSuccess() {
        UUID paymentId = UUID.randomUUID();
        String transactionCode = "PTTOPUP001";
        Payment payment = walletTopUpPayment(paymentId, transactionCode);
        Map<String, String> params = successfulIpn(transactionCode);
        when(vnPayService.isValidSignature(params)).thenReturn(true);
        when(vnPayService.isExpectedMerchant("PTUTOR")).thenReturn(true);
        when(paymentRepository.findByTransactionCode(transactionCode)).thenReturn(Optional.of(payment));
        when(paymentRepository.markPaidIfPending(
                eq(paymentId), eq(PaymentStatus.PENDING), eq(PaymentStatus.PAID),
                eq("VNP123456"), eq("00"), any(LocalDateTime.class))).thenReturn(1);

        PaymentService.VnPayIpnResult result = service.processIpn(params);

        assertThat(result.responseCode()).isEqualTo("00");
        assertThat(result.message()).isEqualTo("Confirm Success");
        ArgumentCaptor<WalletTransactionCommand> commandCaptor =
                ArgumentCaptor.forClass(WalletTransactionCommand.class);
        verify(walletService).credit(commandCaptor.capture());
        WalletTransactionCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.amount()).isEqualByComparingTo("250000");
        assertThat(command.purpose()).isEqualTo(WalletTransactionPurpose.TOP_UP);
        assertThat(command.referenceType()).isEqualTo(ReferenceType.PAYMENT);
        assertThat(command.referenceId()).isEqualTo(paymentId);
        assertThat(command.idempotencyKey()).isEqualTo("payment:top-up:" + paymentId);

        ArgumentCaptor<NotificationDomainEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationDomainEvent event = eventCaptor.getValue();
        assertThat(event.recipientUserId()).isEqualTo(userId);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.PAYMENT_SUCCEEDED);
        assertThat(event.referenceType()).isEqualTo(NotificationReferenceType.PAYMENT);
        assertThat(event.referenceId()).isEqualTo(paymentId.toString());
        assertThat(event.data()).containsEntry("paymentType", "WALLET_TOP_UP");
    }

    @Test
    void concurrentWalletTopUpIpnDoesNotCreditWalletAgain() {
        UUID paymentId = UUID.randomUUID();
        String transactionCode = "PTTOPUP002";
        Payment payment = walletTopUpPayment(paymentId, transactionCode);
        Map<String, String> params = successfulIpn(transactionCode);
        when(vnPayService.isValidSignature(params)).thenReturn(true);
        when(vnPayService.isExpectedMerchant("PTUTOR")).thenReturn(true);
        when(paymentRepository.findByTransactionCode(transactionCode)).thenReturn(Optional.of(payment));
        when(paymentRepository.markPaidIfPending(
                eq(paymentId), eq(PaymentStatus.PENDING), eq(PaymentStatus.PAID),
                eq("VNP123456"), eq("00"), any(LocalDateTime.class))).thenReturn(0);

        PaymentService.VnPayIpnResult result = service.processIpn(params);

        assertThat(result.responseCode()).isEqualTo("02");
        assertThat(result.message()).isEqualTo("Order already confirmed");
        verify(walletService, never()).credit(any());
        verify(eventPublisher, never()).publishEvent(any());
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

    private void assertInvalidTopUpAmount(String value) {
        assertThatThrownBy(() -> service.createWalletTopUp(
                userId, new WalletTopUpRequest(new BigDecimal(value), null, null), "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_WALLET_TOP_UP_AMOUNT");
                });
    }

    private Payment walletTopUpPayment(UUID paymentId, String transactionCode) {
        User user = new User();
        user.setId(userId);
        Payment payment = Payment.builder()
                .user(user)
                .amount(new BigDecimal("250000"))
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentType(PaymentType.WALLET_TOP_UP)
                .status(PaymentStatus.PENDING)
                .transactionCode(transactionCode)
                .referenceType(ReferenceType.PAYMENT)
                .referenceId(paymentId)
                .expiresAt(LocalDateTime.of(2026, 9, 26, 3, 15))
                .build();
        payment.setId(paymentId);
        return payment;
    }

    private Map<String, String> successfulIpn(String transactionCode) {
        return Map.of(
                "vnp_TmnCode", "PTUTOR",
                "vnp_TxnRef", transactionCode,
                "vnp_Amount", "25000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "VNP123456",
                "vnp_SecureHash", "valid-signature");
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
