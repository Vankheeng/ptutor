package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface TutorStudentRequestRepository extends JpaRepository<TutorStudentRequest, UUID> {

    @EntityGraph(attributePaths = {"tutor", "tutor.user", "grade", "studyingRequest", "studyingRequest.subject"})
    Page<TutorStudentRequest> findAllByTutor_IdOrderByCreatedAtDesc(UUID tutorId, Pageable pageable);

    @EntityGraph(attributePaths = {"tutor", "tutor.user", "grade", "studyingRequest", "studyingRequest.subject"})
    Page<TutorStudentRequest> findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
            UUID tutorId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"tutor", "tutor.user", "grade", "studyingRequest", "studyingRequest.subject"})
    Optional<TutorStudentRequest> findByIdAndTutor_Id(UUID id, UUID tutorId);

    @EntityGraph(attributePaths = {"tutor", "tutor.user", "grade", "studyingRequest", "studyingRequest.subject"})
    Page<TutorStudentRequest> findAllByStudyingRequest_IdOrderByCreatedAtDesc(
            UUID studyingRequestId, Pageable pageable);

    @EntityGraph(attributePaths = {"tutor", "tutor.user", "grade", "studyingRequest", "studyingRequest.subject"})
    Page<TutorStudentRequest> findAllByStudyingRequest_IdAndStatusOrderByCreatedAtDesc(
            UUID studyingRequestId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"tutor", "tutor.user", "grade", "studyingRequest", "studyingRequest.subject"})
    Optional<TutorStudentRequest> findByIdAndStudyingRequest_Id(UUID id, UUID studyingRequestId);

    boolean existsByTutor_IdAndStudyingRequest_IdAndStatus(
            UUID tutorId, UUID studyingRequestId, ApplicationStatus status);

    long countByStudyingRequest_IdAndStatus(UUID studyingRequestId, ApplicationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TutorStudentRequest request
               set request.status = :cancelledStatus,
                   request.updatedAt = :now
             where request.studyingRequest.id = :studyingRequestId
               and request.status in :activeStatuses
               and request.deletedAt is null
            """)
    int cancelActiveByStudyingRequestId(
            @Param("studyingRequestId") UUID studyingRequestId,
            @Param("activeStatuses") Collection<ApplicationStatus> activeStatuses,
            @Param("cancelledStatus") ApplicationStatus cancelledStatus,
            @Param("now") LocalDateTime now);
}
