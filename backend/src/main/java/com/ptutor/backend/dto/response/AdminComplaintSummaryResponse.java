package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.entity.enums.ComplaintStatus;

public record AdminComplaintSummaryResponse(
        UUID id,
        UUID complainantUserId,
        String complainantName,
        String complainantEmail,
        UserRole complainantRole,
        UUID contractId,
        String title,
        ComplaintStatus status,
        UUID assignedEmployeeId,
        String assignedEmployeeName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
