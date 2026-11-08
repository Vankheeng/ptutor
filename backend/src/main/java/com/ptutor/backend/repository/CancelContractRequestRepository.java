package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.CancelContractRequest;
import com.ptutor.backend.entity.enums.CancelContractRequestStatus;

public interface CancelContractRequestRepository extends JpaRepository<CancelContractRequest, UUID> {

    boolean existsByContract_IdAndStatus(UUID contractId, CancelContractRequestStatus status);

    Optional<CancelContractRequest> findByContract_IdAndStatus(
            UUID contractId, CancelContractRequestStatus status);
}
