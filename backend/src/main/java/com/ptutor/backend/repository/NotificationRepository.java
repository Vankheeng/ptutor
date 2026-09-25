package com.ptutor.backend.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Notification;

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
}
