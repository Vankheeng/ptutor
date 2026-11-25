package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.RefundRequestStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.RefundRequestLessonRepository;
import com.ptutor.backend.repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;

/** Releases a paid lesson's share of tuition to the tutor when it becomes final. */
@Service
@RequiredArgsConstructor
public class LessonPayoutService {
    private final ContractRepository contracts;
    private final ContractPaymentInstallmentRepository installments;
    private final WalletTransactionRepository transactions;
    private final RefundRequestLessonRepository disputedLessons;
    private final ContractPaymentInstallmentService installmentService;
    private final WalletService wallets;
    private final JdbcTemplate jdbc;

    @Transactional
    public void release(Lesson lesson) {
        release(lesson, null);
    }

    @Transactional
    public void release(Lesson lesson, BigDecimal settlementAmount) {
        if (lesson.getStatus() != LessonStatus.CONFIRMED && lesson.getStatus() != LessonStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "LESSON_NOT_FINAL", "Lesson is not final");
        }
        Contract contract = contracts.findByIdForSettlement(lesson.getContract().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found"));
        if (disputedLessons.existsByLesson_IdAndRefundRequest_StatusIn(lesson.getId(),
                java.util.List.of(RefundRequestStatus.PENDING, RefundRequestStatus.IN_REVIEW))) {
            throw new ApiException(HttpStatus.CONFLICT, "LESSON_COMPLETION_ON_HOLD",
                    "Lesson payout is on hold while its refund request is reviewed");
        }
        installmentService.requirePaidForLesson(lesson);
        String key = "lesson-payout-" + lesson.getId();
        var tutorId = contract.getTutor().getUser().getId();
        if (transactions.findByWallet_User_IdAndIdempotencyKey(tutorId, key).isPresent()) return;

        BigDecimal value = installments.findAllByContract_IdOrderBySequenceNumberAsc(contract.getId()).stream()
                .filter(item -> item.getStatus() != PaymentInstallmentStatus.CANCELLED)
                .map(ContractPaymentInstallment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (contract.getTotalLession() == null || contract.getTotalLession() <= 0 || value.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_LESSON_PAYOUT", "Contract has no payable lesson value");
        }
        BigDecimal unitPrice = value.divide(BigDecimal.valueOf(contract.getTotalLession()), 0, RoundingMode.HALF_UP)
                .setScale(2);
        BigDecimal amount = settlementAmount == null ? unitPrice : settlementAmount;
        if (amount.compareTo(unitPrice) > 0 || amount.scale() > 2) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_LESSON_PAYOUT", "Lesson payout exceeds its value");
        }
        if (amount.signum() <= 0 || heldTuition(contract.getId()).compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_HELD_TUITION",
                    "Held tuition is insufficient to pay the lesson");
        }
        wallets.credit(new WalletTransactionCommand(tutorId, amount,
                WalletTransactionPurpose.TUTOR_EARNING,
                ReferenceType.LESSON, lesson.getId(), "Earning for completed lesson", key));
    }

    private BigDecimal heldTuition(java.util.UUID contractId) {
        BigDecimal collected = jdbc.queryForObject("""
                select coalesce(sum(case when status = 'REFUNDED' then 0
                                         else amount - coalesce(refunded_amount, 0) end), 0)
                from payments
                where deleted_at is null and payment_type = 'TUITION_PAYMENT'
                  and reference_type = 'CONTRACT' and reference_id = ?
                  and status in ('PAID', 'REFUNDED')
                """, BigDecimal.class, contractId);
        BigDecimal paidToTutor = jdbc.queryForObject("""
                select coalesce(sum(wt.amount), 0)
                from wallet_transactions wt
                where wt.deleted_at is null and wt.transaction_type = 'CREDIT'
                  and wt.purpose = 'TUTOR_EARNING' and wt.status = 'COMPLETED'
                  and ((wt.reference_type = 'CONTRACT' and wt.reference_id = ?)
                    or (wt.reference_type = 'LESSON' and exists (
                        select 1 from lessons lesson where lesson.id = wt.reference_id
                          and lesson.contract_id = ? and lesson.deleted_at is null))
                    or (wt.reference_type = 'REFUND_REQUEST' and exists (
                        select 1 from refund_requests request where request.id = wt.reference_id
                          and request.contract_id = ? and request.deleted_at is null)))
                """, BigDecimal.class, contractId, contractId, contractId);
        return collected.subtract(paidToTutor);
    }
}
