package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;

import tools.jackson.databind.JsonNode;

/**
 * Chi tiết cho nhân viên: câu hỏi, đoạn hội thoại (nếu người dùng đồng ý đính kèm), người gửi và các yêu cầu trước của
 * cùng người (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.5).
 */
public record AdminSupportRequestDetailResponse(
        UUID id,
        String code,
        String question,
        String userRole,
        Requester requester,
        SupportRequestSource source,
        SupportRequestStatus status,
        String pagePath,
        boolean attachConversation,
        JsonNode conversationExcerpt,
        String answer,
        UUID assignedEmployeeId,
        String assignedEmployeeName,
        boolean assignedToMe,
        UUID previousRequestId,
        List<History> history,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime answeredAt,
        LocalDateTime closedAt) {

    public record Requester(UUID userId, String fullName, String email) {
    }

    public record History(UUID id, String code, String question, SupportRequestStatus status,
                          LocalDateTime createdAt) {
    }
}
