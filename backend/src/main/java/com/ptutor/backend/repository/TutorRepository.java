package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;

import jakarta.persistence.LockModeType;

public interface TutorRepository extends JpaRepository<Tutor, UUID> {

    /**
     * Recomputes the aggregate columns of every tutor from reviews, contracts and the
     * applications the tutor decided. acceptance_rate stays NULL below 3 decisions.
     */
    String REFRESH_AGGREGATES_SQL = """
            WITH rv AS (
                SELECT tutor_id, AVG(rating) AS avg_rating, COUNT(*) AS cnt
                FROM reviews WHERE deleted_at IS NULL AND rating IS NOT NULL
                GROUP BY tutor_id
            ), ct AS (
                SELECT tutor_id,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                       COUNT(DISTINCT student_id) FILTER (WHERE status IN ('ACTIVE', 'COMPLETED')) AS students
                FROM contracts WHERE deleted_at IS NULL
                GROUP BY tutor_id
            ), rq AS (
                SELECT tr.tutor_id,
                       COUNT(*) FILTER (WHERE s.status = 'ACCEPTED') AS accepted,
                       COUNT(*) FILTER (WHERE s.status IN ('ACCEPTED', 'REJECTED')) AS decided,
                       AVG(GREATEST(EXTRACT(EPOCH FROM (COALESCE(s.responded_at, s.updated_at) - s.created_at)), 0)
                               / 3600.0)
                           FILTER (WHERE s.status IN ('ACCEPTED', 'REJECTED')) AS resp_hours
                FROM student_tutor_requests s
                JOIN teaching_requests tr ON tr.id = s.teaching_request_id
                WHERE s.deleted_at IS NULL
                GROUP BY tr.tutor_id
            )
            UPDATE tutors t SET
                average_rating            = COALESCE(ROUND(rv.avg_rating, 2), 0),
                total_reviews             = COALESCE(rv.cnt, 0),
                completed_contracts_count = COALESCE(ct.completed, 0),
                total_students_taught     = COALESCE(ct.students, 0),
                acceptance_rate           = CASE WHEN rq.decided >= 3
                                                 THEN ROUND(100.0 * rq.accepted / rq.decided, 2) END,
                avg_response_time_hours   = CASE WHEN rq.resp_hours IS NOT NULL
                                                 THEN LEAST(ROUND(CAST(rq.resp_hours AS numeric), 2), 9999.99) END,
                updated_at                = CURRENT_TIMESTAMP
            FROM tutors x
            LEFT JOIN rv ON rv.tutor_id = x.id
            LEFT JOIN ct ON ct.tutor_id = x.id
            LEFT JOIN rq ON rq.tutor_id = x.id
            WHERE t.id = x.id AND t.deleted_at IS NULL
            """;

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = REFRESH_AGGREGATES_SQL, nativeQuery = true)
    int refreshAllAggregates();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = REFRESH_AGGREGATES_SQL + " AND t.id = :tutorId", nativeQuery = true)
    int refreshAggregates(@Param("tutorId") UUID tutorId);

    Optional<Tutor> findByUser_Id(UUID userId);

    @EntityGraph(attributePaths = { "user", "user.district", "user.district.province" })
    @Query(value = """
            select tutor
            from Tutor tutor
            where (:accountStatus is null or tutor.user.status = :accountStatus)
              and (:profileStatus is null or tutor.profileStatus = :profileStatus)
              and (:minScore is null or tutor.recommendationScore >= :minScore)
              and (:maxScore is null or tutor.recommendationScore <= :maxScore)
              and (lower(tutor.user.email) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(tutor.user.phone, '')) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(tutor.user.firstName, ''), ' ',
                       coalesce(tutor.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
            """,
            countQuery = """
                    select count(tutor)
                    from Tutor tutor
                    where (:accountStatus is null or tutor.user.status = :accountStatus)
                      and (:profileStatus is null or tutor.profileStatus = :profileStatus)
                      and (:minScore is null or tutor.recommendationScore >= :minScore)
                      and (:maxScore is null or tutor.recommendationScore <= :maxScore)
                      and (lower(tutor.user.email) like lower(concat('%', :keyword, '%'))
                           or lower(coalesce(tutor.user.phone, '')) like lower(concat('%', :keyword, '%'))
                           or lower(concat(coalesce(tutor.user.firstName, ''), ' ',
                               coalesce(tutor.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
                    """)
    Page<Tutor> findAllForAdmin(
            @Param("accountStatus") UserStatus accountStatus,
            @Param("profileStatus") TutorProfileStatus profileStatus,
            @Param("minScore") java.math.BigDecimal minScore,
            @Param("maxScore") java.math.BigDecimal maxScore,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "user.district", "user.district.province",
            "profileReviewedBy", "profileReviewedBy.user"
    })
    @Query("select tutor from Tutor tutor where tutor.user.id = :userId")
    Optional<Tutor> findAdminDetailByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user", "profileReviewedBy", "profileReviewedBy.user" })
    @Query("select tutor from Tutor tutor where tutor.user.id = :userId")
    Optional<Tutor> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "user" })
    @Query("select tutor from Tutor tutor where tutor.id = :tutorId")
    Optional<Tutor> findByIdForScoreUpdate(@Param("tutorId") UUID tutorId);

    @Query("select tutor.id from Tutor tutor")
    List<UUID> findAllIdsForScoreRecalculation();
}
