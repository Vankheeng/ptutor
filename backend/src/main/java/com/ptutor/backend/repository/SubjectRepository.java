package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByNameIgnoreCase(String name);

    List<Subject> findAllByStatusOrderByNameAsc(CatalogStatus status);

    List<Subject> findAllByOrderByNameAsc();

    @Query(value = """
            SELECT s.*
            FROM subjects s
            LEFT JOIN (
                SELECT subject_id, COUNT(*) AS request_count
                FROM teaching_requests
                WHERE deleted_at IS NULL AND status = 'OPEN'
                GROUP BY subject_id
            ) teaching ON teaching.subject_id = s.id
            LEFT JOIN (
                SELECT subject_id, COUNT(*) AS request_count
                FROM studying_requests
                WHERE deleted_at IS NULL AND status = 'OPEN'
                GROUP BY subject_id
            ) studying ON studying.subject_id = s.id
            WHERE s.deleted_at IS NULL AND s.status = :status
            ORDER BY COALESCE(teaching.request_count, 0) + COALESCE(studying.request_count, 0) DESC, s.name ASC
            """, nativeQuery = true)
    List<Subject> findAllByStatusOrderByOpenRequestCountDesc(@Param("status") String status);
}
