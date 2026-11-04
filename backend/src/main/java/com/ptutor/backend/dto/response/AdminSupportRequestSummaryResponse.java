package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;

/** Một dòng trong hàng chờ của nhân viên CUSTOMER_SERVICE. */
public record AdminSupportRequestSummaryResponse(
        UUID id,
        String code,
        String question,
        String userRole,
        String requesterName,
        SupportRequestSource source,
        SupportRequestStatus status,
        UUID assignedEmployeeId,
        String assignedEmployeeName,
        boolean attachConversation,
        LocalDateTime createdAt,
        LocalDateTime answeredAt) {
}
