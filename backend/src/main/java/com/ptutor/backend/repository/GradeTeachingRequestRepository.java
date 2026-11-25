package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.GradeTeachingRequest;

public interface GradeTeachingRequestRepository extends JpaRepository<GradeTeachingRequest, UUID> {

    boolean existsByTeachingRequest_IdAndGrade_Id(UUID teachingRequestId, UUID gradeId);
}
