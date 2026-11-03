package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.WalletSecurityChallenge;
import com.ptutor.backend.entity.enums.WalletSecurityChallengePurpose;

import jakarta.persistence.LockModeType;

public interface WalletSecurityChallengeRepository extends JpaRepository<WalletSecurityChallenge, UUID> {

    Optional<WalletSecurityChallenge> findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(
            UUID userId, WalletSecurityChallengePurpose purpose);

    Optional<WalletSecurityChallenge> findFirstByUser_IdAndPurposeAndIdempotencyKeyAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            UUID userId, WalletSecurityChallengePurpose purpose, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from WalletSecurityChallenge challenge join fetch challenge.user "
            + "where challenge.id = :id and challenge.user.id = :userId")
    Optional<WalletSecurityChallenge> findByIdAndUserIdForUpdate(
            @Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from WalletSecurityChallenge challenge join fetch challenge.user "
            + "where challenge.user.id = :userId and challenge.purpose = :purpose "
            + "and challenge.consumedAt is null and challenge.invalidatedAt is null "
            + "order by challenge.createdAt desc")
    List<WalletSecurityChallenge> findActiveForUpdate(
            @Param("userId") UUID userId, @Param("purpose") WalletSecurityChallengePurpose purpose);

    @Query("select challenge from WalletSecurityChallenge challenge "
            + "where challenge.user.id = :userId and challenge.purpose = :purpose "
            + "and challenge.createdAt > :cutoff order by challenge.createdAt desc")
    List<WalletSecurityChallenge> findRecent(
            @Param("userId") UUID userId,
            @Param("purpose") WalletSecurityChallengePurpose purpose,
            @Param("cutoff") LocalDateTime cutoff);
}
