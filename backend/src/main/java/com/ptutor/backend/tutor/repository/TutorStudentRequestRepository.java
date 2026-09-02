package com.ptutor.backend.tutor.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface TutorStudentRequestRepository extends JpaRepository<TutorStudentRequest, UUID> {

    List<TutorStudentRequest> findAllByTutor_IdOrderByCreatedAtDesc(UUID tutorId);

    List<TutorStudentRequest> findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
            UUID tutorId, ApplicationStatus status);

    Optional<TutorStudentRequest> findByIdAndTutor_Id(UUID id, UUID tutorId);

    boolean existsByTutor_IdAndStudyingRequest_IdAndStatusNot(
            UUID tutorId, UUID studyingRequestId, ApplicationStatus status);
}
