package com.ptutor.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RequestStatus;

public interface TeachingRequestRepository extends JpaRepository<TeachingRequest, UUID> {

    List<TeachingRequest> findAllByTutor_IdOrderByCreatedAtDesc(UUID tutorId);

    List<TeachingRequest> findAllByTutor_IdAndStatusOrderByCreatedAtDesc(UUID tutorId, RequestStatus status);

    Optional<TeachingRequest> findByIdAndTutor_Id(UUID id, UUID tutorId);

    List<TeachingRequest> findAllByStatusOrderByCreatedAtDesc(RequestStatus status);

    List<TeachingRequest> findAllByOrderByCreatedAtDesc();
}
