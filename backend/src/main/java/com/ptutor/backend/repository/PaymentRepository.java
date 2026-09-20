package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.enums.PaymentStatus;
import com.ptutor.backend.entity.enums.PaymentType;
import com.ptutor.backend.entity.enums.ReferenceType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = { "paymentInstallment", "paymentInstallment.contract" })
    Optional<Payment> findByTransactionCode(String transactionCode);

    @EntityGraph(attributePaths = { "paymentInstallment", "paymentInstallment.contract" })
    Optional<Payment> findByIdAndUser_Id(UUID id, UUID userId);

    @EntityGraph(attributePaths = { "paymentInstallment", "paymentInstallment.contract" })
    Optional<Payment> findFirstByUser_IdAndPaymentTypeAndReferenceTypeAndReferenceIdAndStatusOrderByCreatedAtDesc(
            UUID userId, PaymentType paymentType, ReferenceType referenceType, UUID referenceId, PaymentStatus status);

    Optional<Payment> findFirstByUser_IdAndPaymentInstallment_IdAndStatusOrderByCreatedAtDesc(
            UUID userId, UUID paymentInstallmentId, PaymentStatus status);

    List<Payment> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Payment payment
               set payment.status = :paidStatus,
                   payment.providerTransactionNo = :providerTransactionNo,
                   payment.providerResponseCode = :responseCode,
                   payment.paidAt = :paidAt,
                   payment.updatedAt = :paidAt
             where payment.id = :paymentId
               and payment.status = :pendingStatus
               and payment.deletedAt is null
            """)
    int markPaidIfPending(
            @Param("paymentId") UUID paymentId,
            @Param("pendingStatus") PaymentStatus pendingStatus,
            @Param("paidStatus") PaymentStatus paidStatus,
            @Param("providerTransactionNo") String providerTransactionNo,
            @Param("responseCode") String responseCode,
            @Param("paidAt") LocalDateTime paidAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Payment payment
               set payment.status = :failedStatus,
                   payment.providerResponseCode = :responseCode,
                   payment.updatedAt = :now
             where payment.id = :paymentId
               and payment.status = :pendingStatus
               and payment.deletedAt is null
            """)
    int markFailedIfPending(
            @Param("paymentId") UUID paymentId,
            @Param("pendingStatus") PaymentStatus pendingStatus,
            @Param("failedStatus") PaymentStatus failedStatus,
            @Param("responseCode") String responseCode,
            @Param("now") LocalDateTime now);
}
