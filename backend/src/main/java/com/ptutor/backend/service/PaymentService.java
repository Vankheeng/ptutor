package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.VnPayPaymentRequest;
import com.ptutor.backend.dto.request.WalletTopUpRequest;
import com.ptutor.backend.dto.command.WalletTransactionCommand;
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
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
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

    private static final BigDecimal MIN_WALLET_TOP_UP = new BigDecimal("10000");
    private static final BigDecimal MAX_WALLET_TOP_UP = new BigDecimal("100000000");

    private final PaymentRepository paymentRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final TeachingRequestRepository teachingRequestRepository;
    private final ContractRepository contractRepository;
    private final ContractPaymentInstallmentRepository installmentRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletService walletService;
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
    public PaymentInitiationResponse createWalletTopUp(
            UUID userId, WalletTopUpRequest request, String clientIp) {
        BigDecimal amount = requireValidTopUpAmount(request.amount());
        User paymentUser = new User();
        paymentUser.setId(userId);
        Payment payment = Payment.builder()
                .user(paymentUser)
                .amount(amount)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentType(PaymentType.WALLET_TOP_UP)
                .status(PaymentStatus.PENDING)
                .transactionCode("PT" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .referenceType(ReferenceType.PAYMENT)
                .note(PaymentType.WALLET_TOP_UP.name())
                .expiresAt(vnPayService.expiresAt())
                .build();
        Payment saved = paymentRepository.saveAndFlush(payment);
        saved.setReferenceId(saved.getId());
        saved = paymentRepository.saveAndFlush(saved);
        VnPayPaymentRequest vnPayRequest = new VnPayPaymentRequest(request.bankCode(), request.locale());
        return toInitiationResponse(saved, vnPayService.createPaymentUrl(saved, clientIp, vnPayRequest));
    }

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
    public PaymentResponse payStudyingRequestWithWallet(UUID userId, UUID requestId) {
        Student student = studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> forbidden("STUDENT_PROFILE_REQUIRED", "Only a student can pay this fee"));
        StudyingRequest studyingRequest = studyingRequestRepository.findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> notFound("STUDYING_REQUEST_NOT_FOUND", "Studying request not found"));

        Payment existingPaid = findPayment(
                userId, PaymentType.STUDYING_REQUEST_FEE, ReferenceType.STUDYING_REQUEST,
                requestId, null, PaymentStatus.PAID);
        if (existingPaid != null) {
            return toPaymentResponse(existingPaid);
        }
        if (studyingRequest.getStatus() != RequestStatus.DRAFT) {
            throw conflict("INVALID_STUDYING_REQUEST_PAYMENT_STATUS",
                    "Only DRAFT studying requests can be paid");
        }

        return completeWalletPayment(
                userId,
                PaymentType.STUDYING_REQUEST_FEE,
                ReferenceType.STUDYING_REQUEST,
                requestId,
                requirePositiveFee(studyingRequestFee, "studying request"),
                null,
                WalletTransactionPurpose.STUDYING_REQUEST_FEE,
                "Studying request publication fee",
                "payment:studying-request:" + requestId);
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
                teachingRequest.getId(), calculateTeachingRequestFee(teachingRequest), null, request, clientIp);
    }

    @Transactional
    public PaymentResponse payTeachingRequestWithWallet(UUID userId, UUID requestId) {
        Tutor tutor = tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> forbidden("TUTOR_PROFILE_REQUIRED", "Only a tutor can pay this fee"));
        TeachingRequest teachingRequest = teachingRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> notFound("TEACHING_REQUEST_NOT_FOUND", "Teaching request not found"));

        Payment existingPaid = findPayment(
                userId, PaymentType.TEACHING_REQUEST_FEE, ReferenceType.TEACHING_REQUEST,
                requestId, null, PaymentStatus.PAID);
        if (existingPaid != null) {
            return toPaymentResponse(existingPaid);
        }
        if (teachingRequest.getStatus() != RequestStatus.DRAFT) {
            throw conflict("INVALID_TEACHING_REQUEST_PAYMENT_STATUS",
                    "Only DRAFT teaching requests can be paid");
        }

        return completeWalletPayment(
                userId,
                PaymentType.TEACHING_REQUEST_FEE,
                ReferenceType.TEACHING_REQUEST,
                requestId,
                calculateTeachingRequestFee(teachingRequest),
                null,
                WalletTransactionPurpose.TEACHING_REQUEST_FEE,
                "Teaching request publication fee",
                "payment:teaching-request:" + requestId);
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

    @Transactional
    public PaymentResponse payTuitionWithWallet(UUID userId, UUID contractId, UUID installmentId) {
        Contract contract = findStudentContract(userId, contractId);
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw conflict("CONTRACT_NOT_ACTIVE", "Payments are available only for ACTIVE contracts");
        }
        installmentService.ensureForActiveContract(contract);
        ContractPaymentInstallment installment = installmentRepository.findForStudent(installmentId, contractId, userId)
                .orElseThrow(() -> notFound("PAYMENT_INSTALLMENT_NOT_FOUND", "Payment installment not found"));

        Payment existingPaid = findPayment(
                userId, PaymentType.TUITION_PAYMENT, ReferenceType.CONTRACT,
                contractId, installment, PaymentStatus.PAID);
        if (existingPaid != null) {
            return toPaymentResponse(existingPaid);
        }
        if (installment.getStatus() != PaymentInstallmentStatus.PENDING) {
            throw conflict("PAYMENT_INSTALLMENT_NOT_PAYABLE", "Only pending installments can be paid");
        }
        if (installment.getPaymentPeriod() == com.ptutor.backend.entity.enums.PaymentPeriod.PER_LESSON
                && installment.getLesson() == null) {
            throw conflict("PAYMENT_INSTALLMENT_NOT_READY", "This lesson installment is not scheduled yet");
        }

        return completeWalletPayment(
                userId,
                PaymentType.TUITION_PAYMENT,
                ReferenceType.CONTRACT,
                contractId,
                installment.getAmount(),
                installment,
                WalletTransactionPurpose.TUITION_PAYMENT,
                "Contract tuition installment payment",
                "payment:installment:" + installmentId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findMine(UUID userId, UUID paymentId) {
        return toPaymentResponse(paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> notFound("PAYMENT_NOT_FOUND", "Payment not found")));
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponse findMyFinancialTransaction(
            UUID userId, FinancialTransactionSource source, UUID transactionId) {
        return switch (source) {
            case PAYMENT -> paymentRepository.findByIdAndUser_Id(transactionId, userId)
                    .map(this::toFinancialPayment)
                    .orElseThrow(() -> notFound("TRANSACTION_NOT_FOUND", "Transaction not found"));
            case WALLET_TRANSACTION -> walletTransactionRepository.findByIdAndWallet_User_Id(transactionId, userId)
                    .map(this::toFinancialWalletTransaction)
                    .orElseThrow(() -> notFound("TRANSACTION_NOT_FOUND", "Transaction not found"));
            case WITHDRAWAL -> withdrawalRequestRepository.findByIdAndWallet_User_Id(transactionId, userId)
                    .map(this::toFinancialWithdrawal)
                    .orElseThrow(() -> notFound("TRANSACTION_NOT_FOUND", "Transaction not found"));
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialTransactionResponse> findMyTransactions(
            UUID userId, FinancialTransactionSource source, String status, String type, String method,
            LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Stream<FinancialTransactionResponse> values = Stream.concat(
                paymentRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream().map(this::toFinancialPayment),
                Stream.concat(
                        walletTransactionRepository.findAllByWallet_User_IdOrderByCreatedAtDesc(userId).stream()
                                .map(this::toFinancialWalletTransaction),
                        withdrawalRequestRepository.findAllByWallet_User_IdOrderByCreatedAtDesc(userId).stream()
                                .map(this::toFinancialWithdrawal)));
        List<FinancialTransactionResponse> raw = values.toList();
        Map<String, Integer> walletPaymentLedgerCounts = new HashMap<>();
        raw.stream()
                .filter(value -> value.source() == FinancialTransactionSource.WALLET_TRANSACTION)
                .filter(value -> "DEBIT".equals(value.type()) && isSuccessfulFinancialStatus(value.status()))
                .forEach(value -> {
                    String paymentType = walletPaymentType(value.description());
                    if (paymentType != null) {
                        String key = financialPaymentKey(paymentType, value.referenceId(), value.amount());
                        walletPaymentLedgerCounts.merge(key, 1, Integer::sum);
                    }
                });
        Set<UUID> walletPaymentDuplicates = new HashSet<>();
        raw.stream()
                .filter(value -> value.source() == FinancialTransactionSource.PAYMENT)
                .filter(value -> "WALLET".equals(value.method()) && isSuccessfulFinancialStatus(value.status()))
                .forEach(value -> {
                    String key = financialPaymentKey(value.type(), value.referenceId(), value.amount());
                    int matchingLedgerEntries = walletPaymentLedgerCounts.getOrDefault(key, 0);
                    if (matchingLedgerEntries > 0) {
                        walletPaymentDuplicates.add(value.id());
                        walletPaymentLedgerCounts.put(key, matchingLedgerEntries - 1);
                    }
                });
        Set<UUID> creditedTopUpIds = new HashSet<>();
        raw.stream()
                .filter(value -> value.source() == FinancialTransactionSource.WALLET_TRANSACTION)
                .filter(value -> value.description() != null && value.description().toLowerCase(Locale.ROOT).contains("top-up"))
                .map(FinancialTransactionResponse::referenceId)
                .filter(java.util.Objects::nonNull)
                .forEach(creditedTopUpIds::add);
        Set<UUID> withdrawalRequestIds = new HashSet<>();
        raw.stream()
                .filter(value -> value.source() == FinancialTransactionSource.WITHDRAWAL)
                .map(FinancialTransactionResponse::id)
                .forEach(withdrawalRequestIds::add);

        List<FinancialTransactionResponse> all = raw.stream()
                .filter(value -> !isTopUp(value) || isSuccessfulFinancialStatus(value.status()))
                // A successful top-up is represented by its wallet credit; hide the matching payment row.
                .filter(value -> !(value.source() == FinancialTransactionSource.PAYMENT
                        && "WALLET_TOP_UP".equals(value.type()) && creditedTopUpIds.contains(value.id())))
                // A successful wallet debit is the ledger record for a payment; hide its matching Payment row.
                .filter(value -> source != null || !walletPaymentDuplicates.contains(value.id()))
                // Keep the wallet ledger entry for accounting, but show the linked withdrawal request only once.
                .filter(value -> source == FinancialTransactionSource.WALLET_TRANSACTION
                        || value.source() != FinancialTransactionSource.WALLET_TRANSACTION
                        || !withdrawalRequestIds.contains(value.referenceId())
                        || !"DEBIT".equals(value.type()))
                .filter(value -> source == null || value.source() == source)
                .filter(value -> matchesFinancialStatus(value.status(), status))
                .filter(value -> matchesFinancialType(value, type))
                .filter(value -> method == null || value.method().equalsIgnoreCase(method))
                .filter(value -> fromDate == null || !value.occurredAt().toLocalDate().isBefore(fromDate))
                .filter(value -> toDate == null || !value.occurredAt().toLocalDate().isAfter(toDate))
                .sorted(Comparator.comparing(FinancialTransactionResponse::occurredAt).reversed())
                .toList();
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        PageImpl<FinancialTransactionResponse> page = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return PageResponse.from(page, page.getContent());
    }

    private boolean isTopUp(FinancialTransactionResponse value) {
        return "WALLET_TOP_UP".equals(value.type())
                || (value.source() == FinancialTransactionSource.WALLET_TRANSACTION
                        && value.description() != null
                        && value.description().toLowerCase(Locale.ROOT).contains("top-up"));
    }

    private String walletPaymentType(String description) {
        if (description == null) return null;
        return switch (description) {
            case "Teaching request publication fee", "Phí đăng yêu cầu dạy" -> PaymentType.TEACHING_REQUEST_FEE.name();
            case "Studying request publication fee", "Phí đăng yêu cầu học" -> PaymentType.STUDYING_REQUEST_FEE.name();
            case "Contract tuition installment payment", "Thanh toán học phí" -> PaymentType.TUITION_PAYMENT.name();
            default -> null;
        };
    }

    private String financialPaymentKey(String type, UUID referenceId, BigDecimal amount) {
        return type + ":" + referenceId + ":" + amount.stripTrailingZeros().toPlainString();
    }

    private boolean isSuccessfulFinancialStatus(String status) {
        return List.of("PAID", "COMPLETED", "SUCCESS", "SUCCEEDED").contains(status.toUpperCase(Locale.ROOT));
    }

    private boolean matchesFinancialStatus(String actual, String requested) {
        if (requested == null || requested.isBlank()) return true;
        if ("COMPLETED".equalsIgnoreCase(requested)) {
            return List.of("COMPLETED", "PAID", "SUCCESS", "SUCCEEDED").contains(actual.toUpperCase(Locale.ROOT));
        }
        return actual.equalsIgnoreCase(requested);
    }

    private boolean matchesFinancialType(FinancialTransactionResponse value, String requested) {
        if (requested == null || requested.isBlank()) return true;
        String filter = requested.toUpperCase(Locale.ROOT);
        String description = value.description() == null ? "" : value.description().toLowerCase(Locale.ROOT);
        return switch (filter) {
            case "TOP_UP" -> "WALLET_TOP_UP".equals(value.type())
                    || (value.source() == FinancialTransactionSource.WALLET_TRANSACTION && description.contains("top-up"));
            case "WITHDRAWAL" -> value.source() == FinancialTransactionSource.WITHDRAWAL || "WITHDRAWAL".equals(value.type())
                    || description.contains("thu hồi yêu cầu rút tiền") || description.contains("withdrawal request cancelled");
            case "DEBIT" -> value.source() == FinancialTransactionSource.WALLET_TRANSACTION && "DEBIT".equals(value.type());
            case "REFUND" -> "REFUNDED".equals(value.status()) || value.type().toUpperCase(Locale.ROOT).contains("REFUND")
                    || description.contains("refund") || description.contains("hoàn tiền");
            default -> value.type().equalsIgnoreCase(filter);
        };
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
        publishPaymentSucceeded(payment);
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
        if (payment.getPaymentType() == PaymentType.WALLET_TOP_UP) {
            walletService.credit(new WalletTransactionCommand(
                    payment.getUser().getId(),
                    payment.getAmount(),
                    WalletTransactionPurpose.TOP_UP,
                    ReferenceType.PAYMENT,
                    payment.getId(),
                    "Wallet top-up via VNPay",
                    "payment:top-up:" + payment.getId()));
        } else if (payment.getPaymentType() == PaymentType.STUDYING_REQUEST_FEE) {
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

    private PaymentResponse completeWalletPayment(
            UUID userId,
            PaymentType paymentType,
            ReferenceType referenceType,
            UUID referenceId,
            BigDecimal amount,
            ContractPaymentInstallment installment,
            WalletTransactionPurpose purpose,
            String description,
            String idempotencyKey) {
        walletService.debit(new WalletTransactionCommand(
                userId, amount, purpose, referenceType, referenceId, description, idempotencyKey));

        // A concurrent retry waits on the wallet lock. Recheck after the debit call
        // so that it can reuse the payment committed by the first request.
        Payment existingPaid = findPayment(
                userId, paymentType, referenceType, referenceId, installment, PaymentStatus.PAID);
        if (existingPaid != null) {
            return toPaymentResponse(existingPaid);
        }

        Payment pendingVnPay = findPayment(
                userId, paymentType, referenceType, referenceId, installment, PaymentStatus.PENDING);
        if (pendingVnPay != null) {
            pendingVnPay.setStatus(PaymentStatus.CANCELLED);
            pendingVnPay.setProviderResponseCode("PAID_BY_WALLET");
            paymentRepository.saveAndFlush(pendingVnPay);
        }

        User paymentUser = new User();
        paymentUser.setId(userId);
        Payment payment = Payment.builder()
                .user(paymentUser)
                .amount(amount)
                .paymentMethod(PaymentMethod.WALLET)
                .paymentType(paymentType)
                .status(PaymentStatus.PAID)
                .transactionCode("PW" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .providerResponseCode("WALLET")
                .referenceType(referenceType)
                .referenceId(referenceId)
                .paymentInstallment(installment)
                .note(paymentType.name())
                .paidAt(LocalDateTime.now(clock))
                .build();
        Payment saved = paymentRepository.saveAndFlush(payment);

        activatePaidPayment(saved);
        publishPaymentSucceeded(saved);
        return toPaymentResponse(saved);
    }

    private Payment findPayment(
            UUID userId,
            PaymentType paymentType,
            ReferenceType referenceType,
            UUID referenceId,
            ContractPaymentInstallment installment,
            PaymentStatus status) {
        return installment == null
                ? paymentRepository
                        .findFirstByUser_IdAndPaymentTypeAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtDesc(
                                userId, paymentType, referenceType, referenceId, status)
                        .orElse(null)
                : paymentRepository
                        .findFirstByUser_IdAndPaymentInstallment_IdAndStatusOrderByCreatedAtDesc(
                                userId, installment.getId(), status)
                        .orElse(null);
    }

    private void publishPaymentSucceeded(Payment payment) {
        if (payment.getPaymentType() == PaymentType.WALLET_TOP_UP
                || payment.getPaymentMethod() == PaymentMethod.WALLET) {
            return;
        }
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                payment.getUser().getId(),
                NotificationEventType.PAYMENT_SUCCEEDED,
                paymentNotificationReferenceType(payment),
                paymentNotificationReferenceId(payment),
                java.util.Map.of("paymentType", payment.getPaymentType().name())));
    }

    private NotificationReferenceType paymentNotificationReferenceType(Payment payment) {
        if (payment.getPaymentType() == PaymentType.WALLET_TOP_UP) {
            return NotificationReferenceType.PAYMENT;
        }
        if (payment.getPaymentType() == PaymentType.TUITION_PAYMENT) {
            return NotificationReferenceType.INSTALLMENT;
        }
        return payment.getReferenceType() == ReferenceType.STUDYING_REQUEST
                ? NotificationReferenceType.STUDYING_REQUEST
                : NotificationReferenceType.TEACHING_REQUEST;
    }

    private UUID paymentNotificationReferenceId(Payment payment) {
        if (payment.getPaymentType() == PaymentType.WALLET_TOP_UP) {
            return payment.getId();
        }
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

    private BigDecimal calculateTeachingRequestFee(TeachingRequest request) {
        Integer quantity = request.getQuantity();
        if (quantity == null || quantity < 1) {
            throw new IllegalStateException("Teaching request must have at least one student");
        }
        return requirePositiveFee(teachingRequestFee, "teaching request")
                .multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal requireValidTopUpAmount(BigDecimal value) {
        BigDecimal amount;
        try {
            if (value == null) {
                throw new ArithmeticException();
            }
            amount = value.setScale(0, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw badRequest("INVALID_WALLET_TOP_UP_AMOUNT", "Top-up amount must be a whole VND amount");
        }
        if (amount.compareTo(MIN_WALLET_TOP_UP) < 0 || amount.compareTo(MAX_WALLET_TOP_UP) > 0) {
            throw badRequest("INVALID_WALLET_TOP_UP_AMOUNT",
                    "Top-up amount must be between 10000 and 100000000 VND");
        }
        return amount;
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
                value.getAmount(), value.getReferenceId(), financialPaymentDescription(value), value.getCreatedAt());
    }

    private String financialPaymentDescription(Payment value) {
        return switch (value.getPaymentType()) {
            case WALLET_TOP_UP -> "Nạp tiền vào ví PTutor";
            case TUITION_PAYMENT -> "Thanh toán học phí";
            case STUDYING_REQUEST_FEE -> "Phí đăng yêu cầu học";
            case TEACHING_REQUEST_FEE -> "Phí đăng yêu cầu dạy";
        };
    }

    private FinancialTransactionResponse toFinancialWalletTransaction(WalletTransaction value) {
        return new FinancialTransactionResponse(value.getId(), FinancialTransactionSource.WALLET_TRANSACTION,
                value.getTransactionType().name(), "WALLET", value.getStatus().name(), value.getAmount(),
                value.getReferenceId(), financialWalletDescription(value), value.getCreatedAt());
    }

    private String financialWalletDescription(WalletTransaction value) {
        return switch (value.getPurpose()) {
            case TUITION_PAYMENT -> "Thanh toán học phí";
            case STUDYING_REQUEST_FEE -> "Phí đăng yêu cầu học";
            case TEACHING_REQUEST_FEE -> "Phí đăng yêu cầu dạy";
            default -> value.getDescription();
        };
    }

    private FinancialTransactionResponse toFinancialWithdrawal(WithdrawalRequest value) {
        String destination = "Rút về " + value.getBankName() + " ••••" + value.getAccountNumberLastFour()
                + " · " + value.getAccountHolderName();
        String description = value.getNote() == null || value.getNote().isBlank()
                ? destination
                : destination + " · " + value.getNote();
        return new FinancialTransactionResponse(value.getId(), FinancialTransactionSource.WITHDRAWAL,
                "WITHDRAWAL", "WALLET", value.getStatus().name(), value.getAmount(), null,
                description, value.getCreatedAt());
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
