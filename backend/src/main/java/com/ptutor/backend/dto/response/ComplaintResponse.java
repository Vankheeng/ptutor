package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.ComplaintCategory;
import com.ptutor.backend.entity.enums.ComplaintFaultParty;
import com.ptutor.backend.entity.enums.ComplaintStatus;

public record ComplaintResponse(
        UUID id,
        UUID userId,
        UUID contractId,
        String title,
        String content,
        ComplaintCategory category,
        ComplaintStatus status,
        String resolution,
        ComplaintFaultParty faultParty,
        LocalDateTime resolvedAt,
        String respondentResponse,
        LocalDateTime respondentRespondedAt,
        List<Evidence> evidences,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Evidence(UUID id, String fileUrl, String fileType, UUID uploadedByUserId) {
    }
}
