package com.ptutor.backend.complaint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.enums.ComplaintStatus;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

    List<Complaint> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Complaint> findByIdAndUser_Id(UUID complaintId, UUID userId);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findAllByStatusOrderByCreatedAtDesc(ComplaintStatus status);
}
