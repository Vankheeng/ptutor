package com.ptutor.backend.dto.request;

import java.util.UUID;

import com.ptutor.backend.entity.enums.SupportRequestSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Người dùng gửi câu hỏi cho nhân viên (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.5). */
public record SupportRequestCreateRequest(
        @NotBlank(message = "Question is required")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question,
        UUID conversationId,
        UUID messageId,
        boolean attachConversation,
        @NotNull(message = "Source is required")
        SupportRequestSource source,
        @Size(max = 200, message = "Page path must not exceed 200 characters")
        String pagePath,
        UUID previousRequestId) {
}
