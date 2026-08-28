package com.ptutor.backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.ptutor.backend.entity.InvalidToken;

import jakarta.persistence.LockModeType;

public interface InvalidTokenRepository extends JpaRepository<InvalidToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvalidToken> findByToken(String token);

    List<InvalidToken> findByUser_IdAndRevokedAtIsNull(UUID userId);
}
