package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface TutorStudentRequestRepository extends JpaRepository<TutorStudentRequest, UUID> {

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
