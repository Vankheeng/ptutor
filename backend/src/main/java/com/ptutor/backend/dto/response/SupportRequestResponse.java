package com.ptutor.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;

/**
 * Yêu cầu hỗ trợ nhìn từ phía người gửi. Không có tên nhân viên trả lời và không có đoạn hội thoại đính kèm: người
 * dùng đã có cuộc trò chuyện của mình; tool {@code list_my_support_requests} của trợ lý cũng đọc đúng các trường này.
 *
 * <p>Múi giờ: {@code createdAt}, {@code updatedAt} do Hibernate ghi theo múi giờ JVM; {@code answeredAt},
 * {@code closedAt} do service ghi bằng {@code LocalDateTime.now(clock)} (UTC) — xem nhãn trong tool-catalog.
 */
public record SupportRequestResponse(
        UUID id,
        String code,
        String question,
        SupportRequestSource source,
        SupportRequestStatus status,
        String answer,
        boolean attachConversation,
        UUID previousRequestId,
        String previousRequestCode,
        LocalDateTime createdAt,
        LocalDateTime answeredAt,
        LocalDateTime closedAt) {
}
