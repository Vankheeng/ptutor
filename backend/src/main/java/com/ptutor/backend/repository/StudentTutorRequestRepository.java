package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface StudentTutorRequestRepository extends JpaRepository<StudentTutorRequest, UUID> {

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    Page<StudentTutorRequest> findAllByStudent_IdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    Page<StudentTutorRequest> findAllByStudent_IdAndStatusOrderByCreatedAtDesc(
            UUID studentId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "student", "student.user", "grade", "teachingRequest", "teachingRequest.subject",
            "teachingRequest.tutor", "teachingRequest.tutor.user"
    })
    Optional<StudentTutorRequest> findByIdAndStudent_Id(UUID id, UUID studentId);

    boolean existsByStudent_IdAndTeachingRequest_IdAndStatus(
            UUID studentId, UUID teachingRequestId, ApplicationStatus status);

    List<StudentTutorRequest> findAllByTeachingRequest_IdOrderByCreatedAtDesc(UUID teachingRequestId);

    List<StudentTutorRequest> findAllByTeachingRequest_IdAndStatusOrderByCreatedAtDesc(
            UUID teachingRequestId, ApplicationStatus status);

    Optional<StudentTutorRequest> findByIdAndTeachingRequest_Id(UUID id, UUID teachingRequestId);

    long countByTeachingRequest_IdAndStatus(UUID teachingRequestId, ApplicationStatus status);

    @Query("""
            select application.teachingRequest.id as teachingRequestId,
                   count(application.id) as studentRequestCount,
                   sum(case when application.status = :pendingStatus then 1 else 0 end) as pendingStudentRequestCount
            from StudentTutorRequest application
            where application.teachingRequest.id in :teachingRequestIds
            group by application.teachingRequest.id
            """)
    List<TeachingRequestStudentRequestCount> countByTeachingRequestIds(
            @Param("teachingRequestIds") List<UUID> teachingRequestIds,
            @Param("pendingStatus") ApplicationStatus pendingStatus);
}
