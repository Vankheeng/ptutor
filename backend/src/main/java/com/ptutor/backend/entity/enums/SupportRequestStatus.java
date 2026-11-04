package com.ptutor.backend.entity.enums;

/**
 * Trạng thái yêu cầu hỗ trợ (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.3):
 * OPEN → IN_PROGRESS → ANSWERED → CLOSED; OPEN → CANCELLED; IN_PROGRESS → OPEN (nhân viên trả lại hàng chờ).
 */
public enum SupportRequestStatus {
    OPEN,
    IN_PROGRESS,
    ANSWERED,
    CLOSED,
    CANCELLED
}
