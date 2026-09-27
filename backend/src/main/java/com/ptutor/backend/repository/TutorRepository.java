package com.ptutor.backend.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.entity.enums.UserStatus;

import jakarta.persistence.LockModeType;

public interface TutorRepository extends JpaRepository<Tutor, UUID> {

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
