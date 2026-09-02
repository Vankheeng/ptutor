package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.StudyingRequest;

public interface StudyingRequestRepository extends JpaRepository<StudyingRequest, UUID> {
}
