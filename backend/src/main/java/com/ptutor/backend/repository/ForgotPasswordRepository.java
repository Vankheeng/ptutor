package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.ForgotPassword;

public interface ForgotPasswordRepository extends JpaRepository<ForgotPassword, UUID> {

    Optional<ForgotPassword> findFirstByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ForgotPassword token
               set token.usedAt = :now,
                   token.updatedAt = :now
             where token.user.id = :userId
               and token.usedAt is null
               and token.deletedAt is null
            """)
    int invalidateAllUnusedByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ForgotPassword token
               set token.usedAt = :now,
                   token.updatedAt = :now
             where token.id = :id
               and token.usedAt is null
               and token.expiresAt > :now
               and token.deletedAt is null
            """)
    int consumeIfActive(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
