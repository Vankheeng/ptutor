package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.enums.CertificateStatus;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findAllByTutor_IdOrderByCreatedAtDesc(UUID tutorId);

    List<Certificate> findAllByTutor_IdAndStatusOrderByCreatedAtDesc(UUID tutorId, CertificateStatus status);

    Optional<Certificate> findByIdAndTutor_Id(UUID id, UUID tutorId);

    @Query(value = """
            SELECT certificate FROM Certificate certificate
            JOIN FETCH certificate.tutor tutor
            JOIN FETCH tutor.user user
            WHERE (:status IS NULL OR certificate.status = :status)
              AND (LOWER(certificate.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(certificate.issuingOrganization, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(CONCAT(COALESCE(user.firstName, ''), ' ', COALESCE(user.lastName, '')))
                      LIKE LOWER(CONCAT('%', :keyword, '%')))
            """, countQuery = """
            SELECT COUNT(certificate) FROM Certificate certificate
            JOIN certificate.tutor tutor
            JOIN tutor.user user
            WHERE (:status IS NULL OR certificate.status = :status)
              AND (LOWER(certificate.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(certificate.issuingOrganization, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(CONCAT(COALESCE(user.firstName, ''), ' ', COALESCE(user.lastName, '')))
                      LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Certificate> findAllForReview(
            @Param("status") CertificateStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT certificate FROM Certificate certificate
            JOIN FETCH certificate.tutor tutor
            JOIN FETCH tutor.user user
            LEFT JOIN FETCH certificate.reviewedBy reviewer
            LEFT JOIN FETCH reviewer.user
            WHERE certificate.id = :id
            """)
    Optional<Certificate> findByIdForAdmin(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT certificate FROM Certificate certificate
            JOIN FETCH certificate.tutor tutor
            JOIN FETCH tutor.user
            WHERE certificate.id = :id
            """)
    Optional<Certificate> findByIdForUpdate(@Param("id") UUID id);
}
