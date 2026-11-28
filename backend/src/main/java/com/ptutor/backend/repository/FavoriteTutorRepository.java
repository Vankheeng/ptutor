package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.FavoriteTutor;

public interface FavoriteTutorRepository extends JpaRepository<FavoriteTutor, UUID> {

    /**
     * One row per (student, teaching request), also counting soft-deleted ones: liking a posting again brings the
     * old row back instead of violating the unique constraint.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO favorite_tutors (id, student_id, tutor_id, teaching_request_id, created_at, updated_at)
            VALUES (:id, :studentId, :tutorId, :teachingRequestId, :now, :now)
            ON CONFLICT (student_id, teaching_request_id) DO UPDATE
              SET deleted_at = NULL, updated_at = :now
            """, nativeQuery = true)
    int addOrRestore(
            @Param("id") UUID id, @Param("studentId") UUID studentId, @Param("tutorId") UUID tutorId,
            @Param("teachingRequestId") UUID teachingRequestId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE favorite_tutors SET deleted_at = :now, updated_at = :now
            WHERE student_id = :studentId AND teaching_request_id = :teachingRequestId AND deleted_at IS NULL
            """, nativeQuery = true)
    int removePosting(
            @Param("studentId") UUID studentId, @Param("teachingRequestId") UUID teachingRequestId,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE favorite_tutors SET deleted_at = :now, updated_at = :now
            WHERE student_id = (SELECT id FROM students WHERE user_id = :studentUserId)
              AND tutor_id = :tutorId AND deleted_at IS NULL
            """, nativeQuery = true)
    int removeForStudentUser(
            @Param("studentUserId") UUID studentUserId, @Param("tutorId") UUID tutorId,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE favorite_tutors SET deleted_at = :now, updated_at = :now
            WHERE student_id = (SELECT id FROM students WHERE user_id = :studentUserId)
              AND teaching_request_id = :teachingRequestId AND deleted_at IS NULL
            """, nativeQuery = true)
    int removePostingForStudentUser(
            @Param("studentUserId") UUID studentUserId,
            @Param("teachingRequestId") UUID teachingRequestId,
            @Param("now") LocalDateTime now);

    @Query("select distinct favorite.tutor.user.id from FavoriteTutor favorite where favorite.student.user.id = :userId")
    List<UUID> findTutorUserIdsByStudentUserId(@Param("userId") UUID userId);

    @Query("select favorite.teachingRequest.id from FavoriteTutor favorite "
            + "where favorite.student.user.id = :userId and favorite.teachingRequest is not null")
    List<UUID> findTeachingRequestIdsByStudentUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = { "tutor", "teachingRequest" })
    List<FavoriteTutor> findAllByStudent_User_IdOrderByUpdatedAtDesc(UUID userId);

    boolean existsByStudent_IdAndTeachingRequest_Id(UUID studentId, UUID teachingRequestId);
}
