package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.dto.enums.ComplaintRelation;
import com.ptutor.backend.entity.enums.ComplaintStatus;

public record AdminStudentComplaintResponse(
        UUID complaintId,
        ComplaintRelation relation,
        UUID complainantUserId,
        String complainantName,
        String complainantEmail,
        UUID contractId,
        String title,
        ComplaintStatus status,
        UUID assignedEmployeeId,
        String assignedEmployeeName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
