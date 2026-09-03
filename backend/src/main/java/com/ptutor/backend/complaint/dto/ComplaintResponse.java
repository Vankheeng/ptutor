package com.ptutor.backend.complaint.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.enums.ComplaintStatus;

public record ComplaintResponse(
        UUID id,
        UUID userId,
        UUID contractId,
        UUID employeeId,
        String title,
        String content,
        ComplaintStatus status,
        String resolution,
        LocalDateTime resolvedAt,
        List<ComplaintEvidenceResponse> evidences,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ComplaintResponse from(Complaint complaint, List<ComplaintEvidenceResponse> evidences) {
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getUser().getId(),
                complaint.getContract().getId(),
                complaint.getEmployee() == null ? null : complaint.getEmployee().getId(),
                complaint.getTitle(),
                complaint.getContent(),
                complaint.getStatus(),
                complaint.getResolution(),
                complaint.getResolvedAt(),
                evidences,
                complaint.getCreatedAt(),
                complaint.getUpdatedAt());
    }
}
