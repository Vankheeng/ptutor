package com.ptutor.backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.InvalidToken;

import java.time.LocalDateTime;

public interface InvalidTokenRepository extends JpaRepository<InvalidToken, UUID> {

    Optional<InvalidToken> findByToken(String token);

    @Modifying
    @Query("""
            update InvalidToken token
            set token.revokedAt = :now, token.updatedAt = :now
            where token.id = :id
              and token.revokedAt is null
              and token.expiresAt > :now
              and token.deletedAt is null
            """)
    int revokeIfActive(@Param("id") java.util.UUID id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            update InvalidToken token
            set token.revokedAt = :now, token.updatedAt = :now
            where token.id = :id
              and token.revokedAt is null
              and token.deletedAt is null
            """)
    int revokeIfNotRevoked(@Param("id") java.util.UUID id, @Param("now") LocalDateTime now);

    List<InvalidToken> findByUser_IdAndRevokedAtIsNull(UUID userId);
}
