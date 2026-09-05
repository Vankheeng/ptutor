package com.ptutor.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Evidence;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findAllByComplaint_IdInOrderByCreatedAtAsc(Collection<UUID> complaintIds);
}
