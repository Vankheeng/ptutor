package com.ptutor.backend.studenttutorrequest.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;

public interface StudentTutorRequestRepository extends JpaRepository<StudentTutorRequest, UUID> {

    List<StudentTutorRequest> findAllByStudent_IdOrderByCreatedAtDesc(UUID studentId);

    List<StudentTutorRequest> findAllByStudent_IdAndStatusOrderByCreatedAtDesc(
            UUID studentId, ApplicationStatus status);

    Optional<StudentTutorRequest> findByIdAndStudent_Id(UUID id, UUID studentId);

    List<StudentTutorRequest> findAllByTeachingRequest_Tutor_IdOrderByCreatedAtDesc(UUID tutorId);

    List<StudentTutorRequest> findAllByTeachingRequest_Tutor_IdAndStatusOrderByCreatedAtDesc(
            UUID tutorId, ApplicationStatus status);

    Optional<StudentTutorRequest> findByIdAndTeachingRequest_Tutor_Id(UUID id, UUID tutorId);

    Optional<StudentTutorRequest> findByStudent_IdAndTeachingRequest_Id(UUID studentId, UUID teachingRequestId);
}
