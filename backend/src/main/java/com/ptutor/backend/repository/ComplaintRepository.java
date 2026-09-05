package com.ptutor.backend.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.enums.ComplaintStatus;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    Page<Complaint> findAllByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Complaint> findAllByUser_IdAndStatusOrderByCreatedAtDesc(
            UUID userId, ComplaintStatus status, Pageable pageable);

    Optional<Complaint> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByContract_IdAndStatusIn(UUID contractId, Collection<ComplaintStatus> statuses);
}
