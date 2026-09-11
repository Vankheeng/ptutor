package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.WithdrawalRequest;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    Optional<WithdrawalRequest> findByWallet_IdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    Optional<WithdrawalRequest> findByIdAndWallet_Id(UUID withdrawalId, UUID walletId);

    Page<WithdrawalRequest> findAllByWallet_User_Id(UUID userId, Pageable pageable);
}
