package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;

import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCitizenIdHash(String citizenIdHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

    @Query("""
            select user
            from User user
            where (
                exists (select student.id from Student student where student.user = user)
                or exists (select tutor.id from Tutor tutor where tutor.user = user)
            )
              and (:role is null
                   or (:role = 'STUDENT' and exists (
                       select student.id from Student student where student.user = user
                   ))
                   or (:role = 'TUTOR' and exists (
                       select tutor.id from Tutor tutor where tutor.user = user
                   )))
              and (:status is null or user.status = :status)
              and (:keyword is null
                   or lower(user.email) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(user.firstName, ''), ' ', coalesce(user.lastName, '')))
                      like lower(concat('%', :keyword, '%')))
            """)
    Page<User> findAdminUsers(
            @Param("role") String role,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select user
            from User user
            where user.status = com.ptutor.backend.entity.enums.UserStatus.BLOCKED
              and user.suspensionType = com.ptutor.backend.entity.enums.SuspensionType.TEMPORARY
              and user.suspendedUntil <= :now
            """)
    List<User> findExpiredTemporarySuspensionsForUpdate(@Param("now") LocalDateTime now);

}
