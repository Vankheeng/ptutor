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
        Long minutesToAnswerP95,
        RefusalFollowUp refusalFollowUp) {

    /**
     * Tỉ lệ yêu cầu hỗ trợ trên số lượt trợ lý từ chối / trả lời một phần trong {@code windowDays} ngày (mục 14.9):
     * cho biết người dùng có thật sự cần nhân viên khi trợ lý không trả lời được. Lượt từ chối đếm ở assistant-service;
     * cả khối là null khi không lấy được số đó (trợ lý tắt hoặc lỗi). Tỉ lệ null khi mẫu số bằng 0.
     */
    public record RefusalFollowUp(
            long abstainTurns,
            long partialTurns,
            long requestsAfterAbstain,
            long requestsAfterPartial,
            Double abstainRequestRate,
            Double partialRequestRate) {
    }
}
