package com.ptutor.backend.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
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

import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;
import com.ptutor.backend.entity.enums.NotificationRecordType;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @NonFinal
    private User user;

    @Builder.Default
    @Column(name = "record_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @NonFinal
    private NotificationRecordType recordType = NotificationRecordType.SYSTEM_EVENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_notification_id")
    @NonFinal
    private Notification parentNotification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    @NonFinal
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    @NonFinal
    private User updatedByUser;

    @Column(name = "title", nullable = false, length = 255)
    @NonFinal
    private String title;

    @Column(name = "content", columnDefinition = "text")
    @NonFinal
    private String content;

    @Column(name = "reference_id", length = 255)
    @NonFinal
    private String referenceId;

    @Column(name = "type", nullable = false, length = 50)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(name = "event_type", length = 80)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private NotificationEventType eventType;

    @Column(name = "reference_type", length = 50)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private NotificationReferenceType referenceType;

    @Column(name = "deduplication_key", length = 255)
    @NonFinal
    private String deduplicationKey;

    @Column(name = "is_read", nullable = false)
    @NonFinal
    private Boolean isRead;

    @Column(name = "category", length = 30)
    @Enumerated(EnumType.STRING)
    @NonFinal
    private NotificationCategory category;

    @Column(name = "audience", length = 20)
    @Enumerated(EnumType.STRING)
    @NonFinal
    private NotificationAudience audience;

    @Column(name = "campaign_status", length = 30)
    @Enumerated(EnumType.STRING)
    @NonFinal
    private NotificationCampaignStatus campaignStatus;

    @Column(name = "scheduled_at")
    @NonFinal
    private LocalDateTime scheduledAt;

    @Column(name = "processing_started_at")
    @NonFinal
    private LocalDateTime processingStartedAt;

    @Column(name = "sent_at")
    @NonFinal
    private LocalDateTime sentAt;

    @Builder.Default
    @Column(name = "recipient_count", nullable = false)
    @NonFinal
    private Integer recipientCount = 0;

    @Column(name = "failure_reason", columnDefinition = "text")
    @NonFinal
    private String failureReason;
}
