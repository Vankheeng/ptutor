package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.enums.LessonOutcome;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.PaymentRepository;
import com.ptutor.backend.repository.RefundRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Gathers a contract's price, tuition paid and earlier refunds and runs {@link RefundCalculator} on them.
 */
@Component
@RequiredArgsConstructor
public class RefundAssessor {

    private static final Set<PaymentStatus> COLLECTED_STATUSES = EnumSet.of(PaymentStatus.PAID, PaymentStatus.REFUNDED);

    private final ContractPaymentInstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final RefundPolicy refundPolicy;

    public record Assessment(RefundCalculator.Result result, List<Payment> tuitionPayments) {
    }

    public List<Payment> tuitionPayments(UUID contractId) {
        return paymentRepository.findAllForContract(contractId, ReferenceType.CONTRACT).stream()
                .filter(payment -> payment.getPaymentType() == PaymentType.TUITION_PAYMENT)
                .filter(payment -> COLLECTED_STATUSES.contains(payment.getStatus()))
                .toList();
    }

    public BigDecimal paidTuition(UUID contractId) {
        return sum(tuitionPayments(contractId).stream().map(Payment::getAmount).toList());
    }

    public Assessment assess(Contract contract, List<LessonOutcome> outcomes) {
        UUID contractId = contract.getId();
        BigDecimal contractValue = sum(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contractId)
                .stream()
                .filter(installment -> installment.getStatus() != PaymentInstallmentStatus.CANCELLED)
                .map(ContractPaymentInstallment::getAmount)
                .toList());
        List<Payment> payments = tuitionPayments(contractId);
        RefundCalculator.Result result = RefundCalculator.calculate(
                contractValue,
                contract.getTotalLession() == null ? 0 : contract.getTotalLession(),
                sum(payments.stream().map(Payment::getAmount).toList()),
                refundRequestRepository.sumRefundedAmountForContract(contractId),
                outcomes,
                refundPolicy.rates());
        return new Assessment(result, payments);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
