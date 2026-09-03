package com.ptutor.backend.tutor.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.GradeTeachingRequest;

public interface GradeTeachingRequestRepository extends JpaRepository<GradeTeachingRequest, UUID> {

    List<GradeTeachingRequest> findAllByTeachingRequest_Id(UUID requestId);

    boolean existsByTeachingRequest_IdAndGrade_Id(UUID teachingRequestId, UUID gradeId);
}
