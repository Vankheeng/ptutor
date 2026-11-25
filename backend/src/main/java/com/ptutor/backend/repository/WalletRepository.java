package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Wallet;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUser_Id(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from Wallet wallet join fetch wallet.user where wallet.user.id = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
