package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ptutor.backend.entity.WalletTransaction;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, UUID>, JpaSpecificationExecutor<WalletTransaction> {

    List<WalletTransaction> findAllByWallet_User_IdOrderByCreatedAtDesc(UUID userId);

    Optional<WalletTransaction> findByWallet_IdAndIdempotencyKey(UUID walletId, String idempotencyKey);
}
