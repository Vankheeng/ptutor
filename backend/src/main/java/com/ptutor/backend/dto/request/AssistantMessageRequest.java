package com.ptutor.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Câu hỏi gửi trợ lý (docs/support-chatbot/07-api.md mục 7.2). Không có {@code conversationId} → hội thoại mới.
 * Độ dài 1–1.000 ký tự được kiểm ở {@code AssistantController} để trả đúng mã {@code INVALID_MESSAGE}.
 */
public record AssistantMessageRequest(
        UUID conversationId,
        String message,
        @Size(max = 200, message = "pagePath must not exceed 200 characters")
        String pagePath) {
}
