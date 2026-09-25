package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.enums.PaymentInstallmentStatus;
import com.ptutor.backend.entity.enums.ContractStatus;

public interface ContractPaymentInstallmentRepository extends JpaRepository<ContractPaymentInstallment, UUID> {

    List<ContractPaymentInstallment> findAllByContract_IdOrderBySequenceNumberAsc(UUID contractId);

    long countByContract_IdAndLessonIsNotNull(UUID contractId);

    @Query("""
            select installment from ContractPaymentInstallment installment
            where installment.id = :installmentId
              and installment.contract.id = :contractId
              and installment.contract.student.user.id = :userId
            """)
    Optional<ContractPaymentInstallment> findForStudent(
            @Param("installmentId") UUID installmentId,
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId);

    Optional<ContractPaymentInstallment> findFirstByContract_IdAndStatusAndLessonIsNullOrderBySequenceNumberAsc(
            UUID contractId, PaymentInstallmentStatus status);

    @Query("""
            select installment
            from ContractPaymentInstallment installment
            join fetch installment.contract contract
            join fetch contract.student student
            join fetch student.user user
            where installment.status = :status
              and installment.dueDate = :dueDate
              and contract.status = :contractStatus
            """)
    List<ContractPaymentInstallment> findDueInstallments(
            @Param("status") PaymentInstallmentStatus status,
            @Param("dueDate") java.time.LocalDate dueDate,
            @Param("contractStatus") ContractStatus contractStatus);
}
