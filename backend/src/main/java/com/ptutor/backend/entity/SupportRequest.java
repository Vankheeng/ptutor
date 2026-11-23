package com.ptutor.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

/**
 * Yêu cầu hỗ trợ: câu hỏi người dùng tự gửi cho nhân viên CUSTOMER_SERVICE khi trợ lý không trả lời được
 * (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.4). Một vòng hỏi – đáp; hỏi tiếp thì gửi yêu cầu mới có
 * {@code previousRequest}.
 */
@Entity
@Table(name = "support_requests")
@SQLDelete(sql = "UPDATE support_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class SupportRequest extends BaseEntity {

    /** Mã hiển thị, ví dụ HT-000123 (lấy từ sequence support_request_code_seq). */
    @Column(name = "code", nullable = false, length = 20, updatable = false)
    @NonFinal
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @NonFinal
    private User user;

    /** Vai trò lúc gửi (STUDENT / TUTOR). */
    @Column(name = "user_role", nullable = false, length = 20, updatable = false)
    @NonFinal
    private String userRole;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    @NonFinal
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @NonFinal
    private SupportRequestSource source;

    /** Tham chiếu sang schema assistant, không có khoá ngoại. */
    @Column(name = "conversation_id")
    @NonFinal
    private UUID conversationId;

    @Column(name = "message_id")
    @NonFinal
    private UUID messageId;

    /** JSON các lượt hội thoại đã che thông tin cá nhân; chỉ có khi người dùng đồng ý đính kèm. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversation_excerpt", columnDefinition = "jsonb")
    @NonFinal
    private String conversationExcerpt;

    @Column(name = "attach_conversation", nullable = false)
    @NonFinal
    private boolean attachConversation;

    @Column(name = "page_path", length = 200)
    @NonFinal
    private String pagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NonFinal
    private SupportRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    @NonFinal
    private Employee assignedEmployee;

    @Column(name = "answer", columnDefinition = "TEXT")
    @NonFinal
    private String answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_request_id")
    @NonFinal
    private SupportRequest previousRequest;

    @Column(name = "assigned_at")
    @NonFinal
    private LocalDateTime assignedAt;

    @Column(name = "answered_at")
    @NonFinal
    private LocalDateTime answeredAt;

    @Column(name = "closed_at")
    @NonFinal
    private LocalDateTime closedAt;
}
