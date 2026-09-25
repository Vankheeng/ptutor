package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.VnPayPaymentRequest;
import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.response.ContractPaymentInstallmentResponse;
import com.ptutor.backend.dto.response.PaymentInitiationResponse;
import com.ptutor.backend.dto.response.PaymentResponse;
import com.ptutor.backend.dto.response.FinancialTransactionResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.WalletTransaction;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentMethod;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final TeachingRequestRepository teachingRequestRepository;
    private final ContractRepository contractRepository;
    private final ContractPaymentInstallmentRepository installmentRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final StudyingRequestService studyingRequestService;
    private final TeachingRequestService teachingRequestService;
    private final ContractPaymentInstallmentService installmentService;
    private final VnPayService vnPayService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Value("${app.payment.fees.studying-request}")
    private BigDecimal studyingRequestFee;

    @org.springframework.beans.factory.annotation.Value("${app.payment.fees.teaching-request}")
    private BigDecimal teachingRequestFee;

    @Transactional
    public PaymentInitiationResponse createStudyingRequestPayment(
            UUID userId, UUID requestId, VnPayPaymentRequest request, String clientIp) {
        Student student = studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> forbidden("STUDENT_PROFILE_REQUIRED", "Only a student can pay this fee"));
        StudyingRequest studyingRequest = studyingRequestRepository.findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> notFound("STUDYING_REQUEST_NOT_FOUND", "Studying request not found"));
        if (studyingRequest.getStatus() != RequestStatus.DRAFT) {
            throw conflict("INVALID_STUDYING_REQUEST_PAYMENT_STATUS", "Only DRAFT studying requests can be paid");
        }
        return initiate(userId, PaymentType.STUDYING_REQUEST_FEE, ReferenceType.STUDYING_REQUEST,
                studyingRequest.getId(), requirePositiveFee(studyingRequestFee, "studying request"), null, request, clientIp);
    }

    @Transactional
    public PaymentInitiationResponse createTeachingRequestPayment(
            UUID userId, UUID requestId, VnPayPaymentRequest request, String clientIp) {
        Tutor tutor = tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> forbidden("TUTOR_PROFILE_REQUIRED", "Only a tutor can pay this fee"));
        TeachingRequest teachingRequest = teachingRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> notFound("TEACHING_REQUEST_NOT_FOUND", "Teaching request not found"));
        if (teachingRequest.getStatus() != RequestStatus.DRAFT) {
            throw conflict("INVALID_TEACHING_REQUEST_PAYMENT_STATUS", "Only DRAFT teaching requests can be paid");
        }
        return initiate(userId, PaymentType.TEACHING_REQUEST_FEE, ReferenceType.TEACHING_REQUEST,
                teachingRequest.getId(), requirePositiveFee(teachingRequestFee, "teaching request"), null, request, clientIp);
    }

    @Transactional
    public List<ContractPaymentInstallmentResponse> findMyInstallments(UUID userId, UUID contractId) {
        Contract contract = findStudentContract(userId, contractId);
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw conflict("CONTRACT_NOT_ACTIVE", "Payments are available only for ACTIVE contracts");
        }
        return installmentService.ensureForActiveContract(contract).stream().map(this::toInstallmentResponse).toList();
    }

    @Transactional
    public PaymentInitiationResponse createTuitionPayment(
            UUID userId, UUID contractId, UUID installmentId, VnPayPaymentRequest request, String clientIp) {
        Contract contract = findStudentContract(userId, contractId);
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw conflict("CONTRACT_NOT_ACTIVE", "Payments are available only for ACTIVE contracts");
        }
        installmentService.ensureForActiveContract(contract);
        ContractPaymentInstallment installment = installmentRepository.findForStudent(installmentId, contractId, userId)
                .orElseThrow(() -> notFound("PAYMENT_INSTALLMENT_NOT_FOUND", "Payment installment not found"));
        if (installment.getStatus() != PaymentInstallmentStatus.PENDING) {
            throw conflict("PAYMENT_INSTALLMENT_NOT_PAYABLE", "Only pending installments can be paid");
        }
        if (installment.getPaymentPeriod() == com.ptutor.backend.entity.enums.PaymentPeriod.PER_LESSON
                && installment.getLesson() == null) {
            throw conflict("PAYMENT_INSTALLMENT_NOT_READY", "This lesson installment is not scheduled yet");
        }
        return initiate(userId, PaymentType.TUITION_PAYMENT, ReferenceType.CONTRACT, contractId,
                installment.getAmount(), installment, request, clientIp);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findMine(UUID userId, UUID paymentId) {
        return toPaymentResponse(paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> notFound("PAYMENT_NOT_FOUND", "Payment not found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> findMyTransactions(
            UUID userId, FinancialTransactionSource source, String status, String type, String method, Pageable pageable) {
        Stream<FinancialTransactionResponse> values = Stream.concat(
                paymentRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream().map(this::toFinancialPayment),
                Stream.concat(
                        walletTransactionRepository.findAllByWallet_User_IdOrderByCreatedAtDesc(userId).stream()
                                .map(this::toFinancialWalletTransaction),
                        withdrawalRequestRepository.findAllByWallet_User_IdOrderByCreatedAtDesc(userId).stream()
                                .map(this::toFinancialWithdrawal)));
        List<FinancialTransactionResponse> all = values
                .filter(value -> source == null || value.source() == source)
                .filter(value -> status == null || value.status().equalsIgnoreCase(status))
                .filter(value -> type == null || value.type().equalsIgnoreCase(type))
                .filter(value -> method == null || value.method().equalsIgnoreCase(method))
                .sorted(Comparator.comparing(FinancialTransactionResponse::occurredAt).reversed())
                .toList();
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        PageImpl<FinancialTransactionResponse> page = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return PageResponse.from(page, page.getContent());
    }

    @Transactional
    public VnPayIpnResult processIpn(Map<String, String> params) {
        if (!vnPayService.isValidSignature(params)) {
            return VnPayIpnResult.invalid("97", "Invalid signature");
        }
        if (!vnPayService.isExpectedMerchant(params.get("vnp_TmnCode"))) {
            return VnPayIpnResult.invalid("97", "Invalid merchant");
        }
        Payment payment = paymentRepository.findByTransactionCode(params.get("vnp_TxnRef"))
                .orElse(null);
        if (payment == null) {
            return VnPayIpnResult.invalid("01", "Order not found");
        }
        if (!isExpectedAmount(payment, params.get("vnp_Amount"))) {
            return VnPayIpnResult.invalid("04", "Invalid amount");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return VnPayIpnResult.success("02", "Order already confirmed");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return VnPayIpnResult.invalid("02", "Order already processed");
        }

        String responseCode = params.getOrDefault("vnp_ResponseCode", "99");
        String transactionStatus = params.getOrDefault("vnp_TransactionStatus", "99");
        if (!"00".equals(responseCode) || !"00".equals(transactionStatus)
                || payment.getExpiresAt() == null || !payment.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            paymentRepository.markFailedIfPending(payment.getId(), PaymentStatus.PENDING, PaymentStatus.FAILED,
                    responseCode, LocalDateTime.now(clock));
            return VnPayIpnResult.success("00", "Confirm Success");
        }

        int updated = paymentRepository.markPaidIfPending(payment.getId(), PaymentStatus.PENDING, PaymentStatus.PAID,
                params.get("vnp_TransactionNo"), responseCode, LocalDateTime.now(clock));
        if (updated == 0) {
            return VnPayIpnResult.success("02", "Order already confirmed");
        }
        activatePaidPayment(payment);
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                payment.getUser().getId(),
                NotificationEventType.PAYMENT_SUCCEEDED,
                paymentNotificationReferenceType(payment),
                paymentNotificationReferenceId(payment),
                java.util.Map.of("paymentType", payment.getPaymentType().name())));
        return VnPayIpnResult.success("00", "Confirm Success");
    }

    @Transactional(readOnly = true)
    public PaymentResponse inspectReturn(Map<String, String> params) {
        if (!vnPayService.isValidSignature(params)) {
            throw badRequest("INVALID_VNPAY_SIGNATURE", "VNPay return signature is invalid");
        }
        Payment payment = paymentRepository.findByTransactionCode(params.get("vnp_TxnRef"))
                .orElseThrow(() -> notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (!isExpectedAmount(payment, params.get("vnp_Amount"))) {
            throw badRequest("INVALID_VNPAY_AMOUNT", "VNPay return amount is invalid");
        }
        return toPaymentResponse(payment);
    }

    private PaymentInitiationResponse initiate(
            UUID userId, PaymentType type, ReferenceType referenceType, UUID referenceId, BigDecimal amount,
            ContractPaymentInstallment installment, VnPayPaymentRequest request, String clientIp) {
        Payment payment = installment == null
                ? paymentRepository.findFirstByUser_IdAndPaymentTypeAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtDesc(
                        userId, type, referenceType, referenceId, PaymentStatus.PENDING).orElse(null)
                : paymentRepository.findFirstByUser_IdAndPaymentInstallment_IdAndStatusOrderByCreatedAtDesc(
                        userId, installment.getId(), PaymentStatus.PENDING).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (payment != null && payment.getExpiresAt() != null && payment.getExpiresAt().isAfter(now)) {
            return toInitiationResponse(payment, vnPayService.createPaymentUrl(payment, clientIp, request));
        }
        if (payment != null) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderResponseCode("EXPIRED");
            paymentRepository.saveAndFlush(payment);
        }

        User paymentUser = new User();
        paymentUser.setId(userId);
        Payment created = Payment.builder()
                .user(paymentUser)
                .amount(requireWholeVnd(amount))
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentType(type)
                .status(PaymentStatus.PENDING)
                .transactionCode("PT" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .referenceType(referenceType)
                .referenceId(referenceId)
                .paymentInstallment(installment)
                .note(type.name())
                .expiresAt(vnPayService.expiresAt())
                .build();
        try {
            created = paymentRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            Payment current = installment == null
                    ? paymentRepository.findFirstByUser_IdAndPaymentTypeAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtDesc(
                            userId, type, referenceType, referenceId, PaymentStatus.PENDING).orElseThrow(() -> exception)
                    : paymentRepository.findFirstByUser_IdAndPaymentInstallment_IdAndStatusOrderByCreatedAtDesc(
                            userId, installment.getId(), PaymentStatus.PENDING).orElseThrow(() -> exception);
            return toInitiationResponse(current, vnPayService.createPaymentUrl(current, clientIp, request));
        }
        return toInitiationResponse(created, vnPayService.createPaymentUrl(created, clientIp, request));
    }

    private void activatePaidPayment(Payment payment) {
        if (payment.getPaymentType() == PaymentType.STUDYING_REQUEST_FEE) {
            studyingRequestService.activateAfterPayment(payment.getReferenceId());
        } else if (payment.getPaymentType() == PaymentType.TEACHING_REQUEST_FEE) {
            teachingRequestService.activateAfterPayment(payment.getReferenceId());
        } else if (payment.getPaymentType() == PaymentType.TUITION_PAYMENT) {
            ContractPaymentInstallment installment = payment.getPaymentInstallment();
            if (installment == null) {
                throw new IllegalStateException("Tuition payment has no installment");
            }
            installment.setStatus(PaymentInstallmentStatus.PAID);
            installmentRepository.save(installment);
        }
    }

    private NotificationReferenceType paymentNotificationReferenceType(Payment payment) {
        if (payment.getPaymentType() == PaymentType.TUITION_PAYMENT) {
            return NotificationReferenceType.INSTALLMENT;
        }
        return payment.getReferenceType() == ReferenceType.STUDYING_REQUEST
                ? NotificationReferenceType.STUDYING_REQUEST
                : NotificationReferenceType.TEACHING_REQUEST;
    }

    private UUID paymentNotificationReferenceId(Payment payment) {
        return payment.getPaymentType() == PaymentType.TUITION_PAYMENT
                ? payment.getPaymentInstallment().getId()
                : payment.getReferenceId();
    }

    private Contract findStudentContract(UUID userId, UUID contractId) {
        Student student = studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> forbidden("STUDENT_PROFILE_REQUIRED", "Only a student can pay tuition"));
        Contract contract = contractRepository.findByIdAndParticipantUserId(contractId, userId)
                .orElseThrow(() -> notFound("CONTRACT_NOT_FOUND", "Contract not found"));
        if (!contract.getStudent().getId().equals(student.getId())) {
            throw notFound("CONTRACT_NOT_FOUND", "Contract not found");
        }
        return contract;
    }

    private boolean isExpectedAmount(Payment payment, String source) {
        try {
            BigDecimal received = new BigDecimal(source);
            return payment.getAmount().setScale(0, java.math.RoundingMode.UNNECESSARY)
                    .multiply(BigDecimal.valueOf(100)).compareTo(received) == 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private BigDecimal requirePositiveFee(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException("Configured " + label + " fee must be greater than zero");
        }
        return value;
    }

    private BigDecimal requireWholeVnd(BigDecimal value) {
        try {
            if (value == null || value.signum() <= 0) {
                throw new ArithmeticException();
            }
            return value.setScale(0, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw badRequest("INVALID_VNPAY_AMOUNT", "VNPay amount must be a positive whole VND amount");
        }
    }

    private PaymentInitiationResponse toInitiationResponse(Payment payment, String paymentUrl) {
        return new PaymentInitiationResponse(payment.getId(), payment.getPaymentType(), payment.getAmount(),
                payment.getStatus(), paymentUrl, payment.getExpiresAt());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentType(), payment.getPaymentMethod(),
                payment.getStatus(), payment.getAmount(), payment.getReferenceType(), payment.getReferenceId(),
                payment.getPaymentInstallment() == null ? null : payment.getPaymentInstallment().getId(),
                payment.getTransactionCode(), payment.getProviderTransactionNo(), payment.getExpiresAt(),
                payment.getPaidAt(), payment.getCreatedAt());
    }

    private ContractPaymentInstallmentResponse toInstallmentResponse(ContractPaymentInstallment value) {
        return new ContractPaymentInstallmentResponse(value.getId(), value.getSequenceNumber(), value.getPaymentPeriod(),
                value.getAmount(), value.getDueDate(), value.getLesson() == null ? null : value.getLesson().getId(),
                value.getStatus());
    }

    private FinancialTransactionResponse toFinancialPayment(Payment value) {
        return new FinancialTransactionResponse(value.getId(), FinancialTransactionSource.PAYMENT,
                value.getPaymentType().name(), value.getPaymentMethod().name(), value.getStatus().name(),
                value.getAmount(), value.getReferenceId(), value.getNote(), value.getCreatedAt());
    }

    private FinancialTransactionResponse toFinancialWalletTransaction(WalletTransaction value) {
        return new FinancialTransactionResponse(value.getId(), FinancialTransactionSource.WALLET_TRANSACTION,
                value.getTransactionType().name(), "WALLET", value.getStatus().name(), value.getAmount(),
                value.getReferenceId(), value.getDescription(), value.getCreatedAt());
    }

    private FinancialTransactionResponse toFinancialWithdrawal(WithdrawalRequest value) {
        return new FinancialTransactionResponse(value.getId(), FinancialTransactionSource.WITHDRAWAL,
                "WITHDRAWAL", "WALLET", value.getStatus().name(), value.getAmount(), null,
                value.getNote(), value.getCreatedAt());
    }

    private ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException forbidden(String code, String message) { return new ApiException(HttpStatus.FORBIDDEN, code, message); }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public record VnPayIpnResult(String responseCode, String message) {
        static VnPayIpnResult success(String code, String message) { return new VnPayIpnResult(code, message); }
        static VnPayIpnResult invalid(String code, String message) { return new VnPayIpnResult(code, message); }
    }
}
