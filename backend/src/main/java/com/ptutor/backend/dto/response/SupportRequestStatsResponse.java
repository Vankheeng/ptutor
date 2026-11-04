package com.ptutor.backend.dto.response;

import java.util.Map;

/**
 * Số liệu yêu cầu hỗ trợ (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.9). Thời gian tính bằng phút, trên các
 * yêu cầu tạo trong {@code windowDays} ngày gần nhất; null khi chưa có mẫu.
 */
public record SupportRequestStatsResponse(
        Map<String, Long> byStatus,
        Map<String, Long> bySource,
        int windowDays,
        Long minutesToAssignP50,
        Long minutesToAssignP95,
        Long minutesToAnswerP50,
        Long minutesToAnswerP95) {
}
