package com.ptutor.backend.repository;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.enums.NotificationAudience;
import com.ptutor.backend.entity.enums.NotificationCampaignStatus;
import com.ptutor.backend.entity.enums.NotificationCategory;
import com.ptutor.backend.entity.enums.NotificationRecordType;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findAllByUser_IdAndIsReadOrderByCreatedAtDesc(
            UUID userId, boolean isRead, Pageable pageable);

    java.util.Optional<Notification> findByIdAndUser_Id(UUID notificationId, UUID userId);

    long countByUser_IdAndIsRead(UUID userId, boolean isRead);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.isRead = true,
                   notification.updatedAt = CURRENT_TIMESTAMP
             where notification.user.id = :userId
               and notification.isRead = false
               and notification.deletedAt is null
            """)
    int markAllAsRead(@Param("userId") UUID userId);

    @Query("""
            select notification from Notification notification
            where notification.recordType = com.ptutor.backend.entity.enums.NotificationRecordType.BROADCAST_MASTER
              and (:status is null or notification.campaignStatus = :status)
              and (:audience is null or notification.audience = :audience)
              and (:category is null or notification.category = :category)
              and (:keyword = '' or lower(notification.title) like lower(concat('%', :keyword, '%'))
                   or lower(notification.content) like lower(concat('%', :keyword, '%')))
            """)
    Page<Notification> findCampaigns(
            @Param("status") NotificationCampaignStatus status,
            @Param("audience") NotificationAudience audience,
            @Param("category") NotificationCategory category,
            @Param("keyword") String keyword,
            Pageable pageable);

    Optional<Notification> findByIdAndRecordType(UUID id, NotificationRecordType recordType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification from Notification notification
            where notification.id = :id
              and notification.recordType = com.ptutor.backend.entity.enums.NotificationRecordType.BROADCAST_MASTER
            """)
    Optional<Notification> findCampaignByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select notification.id from Notification notification
            where notification.recordType = com.ptutor.backend.entity.enums.NotificationRecordType.BROADCAST_MASTER
              and notification.campaignStatus = com.ptutor.backend.entity.enums.NotificationCampaignStatus.SCHEDULED
              and notification.scheduledAt <= :now
            order by notification.scheduledAt
            """)
    List<UUID> findDueCampaignIds(@Param("now") LocalDateTime now, Pageable pageable);

    long countByParentNotification_IdAndIsRead(UUID campaignId, boolean isRead);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.title = :title,
                   notification.content = :content,
                   notification.category = :category,
                   notification.updatedAt = CURRENT_TIMESTAMP
             where notification.parentNotification.id = :campaignId
               and notification.recordType = com.ptutor.backend.entity.enums.NotificationRecordType.BROADCAST_DELIVERY
               and notification.deletedAt is null
            """)
    int updateCampaignDeliveries(
            @Param("campaignId") UUID campaignId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("category") NotificationCategory category);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.deletedAt = CURRENT_TIMESTAMP,
                   notification.updatedAt = CURRENT_TIMESTAMP
             where notification.parentNotification.id = :campaignId
               and notification.recordType = com.ptutor.backend.entity.enums.NotificationRecordType.BROADCAST_DELIVERY
               and notification.deletedAt is null
            """)
    int retractCampaignDeliveries(@Param("campaignId") UUID campaignId);
}
