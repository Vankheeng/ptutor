package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ComplaintStatus;

public record ComplaintResponse(
        UUID id,
        UUID userId,
        UUID contractId,
        String title,
        String content,
        ComplaintStatus status,
        String resolution,
        LocalDateTime resolvedAt,
        List<Evidence> evidences,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Evidence(UUID id, String fileUrl, String fileType) {
    }
}
