package com.ptutor.backend.complaint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Evidence;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findAllByComplaint_IdOrderByCreatedAtAsc(UUID complaintId);

    Optional<Evidence> findByIdAndComplaint_Id(UUID evidenceId, UUID complaintId);
}
