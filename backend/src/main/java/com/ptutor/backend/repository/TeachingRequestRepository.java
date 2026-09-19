package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RequestStatus;

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
}
