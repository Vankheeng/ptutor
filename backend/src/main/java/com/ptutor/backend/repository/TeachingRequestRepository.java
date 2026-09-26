package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RequestStatus;

import jakarta.persistence.LockModeType;

public interface TeachingRequestRepository extends JpaRepository<TeachingRequest, UUID> {

    List<TeachingRequest> findAllByTutor_IdOrderByCreatedAtDesc(UUID tutorId);

    List<TeachingRequest> findAllByTutor_IdAndStatusOrderByCreatedAtDesc(UUID tutorId, RequestStatus status);

    Optional<TeachingRequest> findByIdAndTutor_Id(UUID id, UUID tutorId);

    @EntityGraph(attributePaths = { "tutor", "tutor.user" })
    List<TeachingRequest> findAllByStatusOrderByCreatedAtDesc(RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "tutor", "tutor.user", "gradeAssociations", "gradeAssociations.grade" })
    @Query("""
            select distinct request
            from TeachingRequest request
            left join request.gradeAssociations gradeAssociation
            where request.status = :status
              and (:subjectId is null or request.subject.id = :subjectId)
              and (:gradeId is null or gradeAssociation.grade.id = :gradeId)
            order by request.createdAt desc
            """)
    List<TeachingRequest> findPublicByFilters(
            @Param("status") RequestStatus status,
            @Param("subjectId") UUID subjectId,
            @Param("gradeId") UUID gradeId,
            Pageable pageable);

    List<TeachingRequest> findAllByOrderByCreatedAtDesc();

    @Query(value = """
            select request from TeachingRequest request
            join fetch request.tutor tutor
            join fetch tutor.user user
            left join fetch request.subject subject
            where request.status = :status
              and (:keyword = ''
                   or lower(coalesce(request.title, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(request.note, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(subject.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(request.customSubjectName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(user.email) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(user.firstName, ''), ' ', coalesce(user.lastName, '')))
                      like lower(concat('%', :keyword, '%')))
            """, countQuery = """
            select count(request) from TeachingRequest request
            join request.tutor tutor
            join tutor.user user
            left join request.subject subject
            where request.status = :status
              and (:keyword = ''
                   or lower(coalesce(request.title, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(request.note, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(subject.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(request.customSubjectName, '')) like lower(concat('%', :keyword, '%'))
                   or lower(user.email) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(user.firstName, ''), ' ', coalesce(user.lastName, '')))
                      like lower(concat('%', :keyword, '%')))
            """)
    Page<TeachingRequest> findAllForAdmin(
            @Param("status") RequestStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            select request from TeachingRequest request
            join fetch request.tutor tutor
            join fetch tutor.user
            left join fetch request.subject
            left join fetch request.reviewedBy reviewer
            left join fetch reviewer.user
            where request.id = :id
            """)
    Optional<TeachingRequest> findByIdForAdmin(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from TeachingRequest request
            join fetch request.tutor tutor
            join fetch tutor.user
            left join fetch request.subject
            where request.id = :id
            """)
    Optional<TeachingRequest> findByIdForReview(@Param("id") UUID id);
}
