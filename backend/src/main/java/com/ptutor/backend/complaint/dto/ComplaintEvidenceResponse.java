package com.ptutor.backend.complaint.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.Evidence;

public record ComplaintEvidenceResponse(
        UUID id,
        String fileUrl,
        String fileType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ComplaintEvidenceResponse from(Evidence evidence) {
        return new ComplaintEvidenceResponse(
                evidence.getId(),
                evidence.getFileUrl(),
                evidence.getFileType(),
                evidence.getCreatedAt(),
                evidence.getUpdatedAt());
    }
}
