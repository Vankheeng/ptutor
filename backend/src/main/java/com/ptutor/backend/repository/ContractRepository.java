package com.ptutor.backend.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;

import jakarta.persistence.LockModeType;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    @Query("""
            select contract
            from Contract contract
            where contract.id = :contractId
              and (contract.student.user.id = :userId or contract.tutor.user.id = :userId)
            """)
    Optional<Contract> findByIdAndParticipantUserId(
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {
            "student", "student.user", "tutor", "tutor.user", "subject", "grade", "createdBy", "signedBy",
            "tutorStudentRequest", "studentTutorRequest", "renewedFromContract"
    })
    @Query("""
            select contract
            from Contract contract
            where (contract.student.user.id = :userId or contract.tutor.user.id = :userId)
              and (:status is null or contract.status = :status)
            """)
    Page<Contract> findAllForParticipant(
            @Param("userId") UUID userId,
            @Param("status") ContractStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "student", "student.user", "tutor", "tutor.user", "subject", "grade", "createdBy", "signedBy",
            "tutorStudentRequest", "studentTutorRequest", "renewedFromContract"
    })
    @Query("""
            select contract
            from Contract contract
            where contract.id = :contractId
              and (contract.student.user.id = :userId or contract.tutor.user.id = :userId)
            """)
    Optional<Contract> findDetailedByIdAndParticipantUserId(
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "student", "student.user", "tutor", "tutor.user", "subject", "grade", "createdBy", "signedBy",
            "tutorStudentRequest", "studentTutorRequest", "renewedFromContract"
    })
    @Query("""
            select contract
            from Contract contract
            where contract.id = :contractId
              and (contract.student.user.id = :userId or contract.tutor.user.id = :userId)
            """)
    Optional<Contract> findDetailedByIdAndParticipantUserIdForUpdate(
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId);

    boolean existsByTutorStudentRequest_IdAndStatusNot(UUID tutorStudentRequestId, ContractStatus excludedStatus);

    boolean existsByStudentTutorRequest_IdAndStatusNot(UUID studentTutorRequestId, ContractStatus excludedStatus);

    boolean existsByRenewedFromContract_IdAndStatusNot(UUID renewedFromContractId, ContractStatus excludedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Contract contract
               set contract.status = :activeStatus,
                   contract.signedBy = :signedBy,
                   contract.signedAt = :now,
                   contract.updatedAt = :now
             where contract.id = :contractId
               and contract.status = :pendingStatus
               and contract.createdBy.id <> :userId
               and (contract.student.user.id = :userId or contract.tutor.user.id = :userId)
               and contract.endDate > :today
               and contract.deletedAt is null
            """)
    int activatePendingByCounterparty(
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId,
            @Param("signedBy") User signedBy,
            @Param("pendingStatus") ContractStatus pendingStatus,
            @Param("activeStatus") ContractStatus activeStatus,
            @Param("today") LocalDate today,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Contract contract
               set contract.status = :cancelledStatus,
                   contract.updatedAt = :now
             where contract.id = :contractId
               and contract.status = :pendingStatus
               and contract.createdBy.id <> :userId
               and (contract.student.user.id = :userId or contract.tutor.user.id = :userId)
               and contract.deletedAt is null
            """)
    int rejectPendingByCounterparty(
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId,
            @Param("pendingStatus") ContractStatus pendingStatus,
            @Param("cancelledStatus") ContractStatus cancelledStatus,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Contract contract
               set contract.status = :cancelledStatus,
                   contract.updatedAt = :now
             where contract.id = :contractId
               and contract.status = :pendingStatus
               and contract.createdBy.id = :userId
               and contract.deletedAt is null
            """)
    int cancelPendingByCreator(
            @Param("contractId") UUID contractId,
            @Param("userId") UUID userId,
            @Param("pendingStatus") ContractStatus pendingStatus,
            @Param("cancelledStatus") ContractStatus cancelledStatus,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Contract contract
               set contract.status = :completedStatus,
                   contract.updatedAt = :now
             where contract.status = :activeStatus
               and contract.endDate <= :today
               and contract.deletedAt is null
            """)
    int completeActiveContractsEndingOnOrBefore(
            @Param("today") LocalDate today,
            @Param("now") LocalDateTime now,
            @Param("activeStatus") ContractStatus activeStatus,
            @Param("completedStatus") ContractStatus completedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Contract contract
               set contract.status = :cancelledStatus,
                   contract.updatedAt = :now
             where contract.status = :pendingStatus
               and contract.endDate <= :today
               and contract.deletedAt is null
            """)
    int cancelPendingContractsEndingOnOrBefore(
            @Param("today") LocalDate today,
            @Param("now") LocalDateTime now,
            @Param("pendingStatus") ContractStatus pendingStatus,
            @Param("cancelledStatus") ContractStatus cancelledStatus);
}
