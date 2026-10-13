package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.FavoriteTutor;

public interface FavoriteTutorRepository extends JpaRepository<FavoriteTutor, UUID> {

    /**
     * One row per (student, tutor), also counting soft-deleted ones: liking a tutor again brings the
     * old row back instead of violating the unique constraint.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO favorite_tutors (id, student_id, tutor_id, created_at, updated_at)
            VALUES (:id, :studentId, :tutorId, :now, :now)
            ON CONFLICT (student_id, tutor_id) DO UPDATE SET deleted_at = NULL, updated_at = :now
            """, nativeQuery = true)
    int addOrRestore(
            @Param("id") UUID id, @Param("studentId") UUID studentId, @Param("tutorId") UUID tutorId,
            @Param("now") LocalDateTime now);

    boolean existsByStudent_IdAndTutor_Id(UUID studentId, UUID tutorId);
}
