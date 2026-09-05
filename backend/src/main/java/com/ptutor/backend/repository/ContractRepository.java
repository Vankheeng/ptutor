package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Contract;

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
}
