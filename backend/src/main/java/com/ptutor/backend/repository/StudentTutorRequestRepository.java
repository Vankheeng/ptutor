package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface StudentTutorRequestRepository extends JpaRepository<StudentTutorRequest, UUID> {

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
