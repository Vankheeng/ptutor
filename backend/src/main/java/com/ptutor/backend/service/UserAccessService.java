package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.SuspensionType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAccessService {

    public static final String AUTOMATIC_REACTIVATION_REASON = "TEMPORARY_SUSPENSION_EXPIRED";

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public User refreshAccessState(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_AUTHENTICATED_USER",
                        "Authenticated user no longer exists"));
        if (isExpiredTemporarySuspension(user, now())) {
            user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_AUTHENTICATED_USER",
                            "Authenticated user no longer exists"));
            reactivateIfExpired(user);
        }
        return user;
    }

    @Transactional
    public int reactivateExpiredAccounts() {
        LocalDateTime now = now();
        var users = userRepository.findExpiredTemporarySuspensionsForUpdate(now);
        users.forEach(user -> reactivateExpired(user, now));
        return users.size();
    }

    private void reactivateIfExpired(User user) {
        LocalDateTime now = now();
        if (isExpiredTemporarySuspension(user, now)) {
            reactivateExpired(user, now);
        }
    }

    private boolean isExpiredTemporarySuspension(User user, LocalDateTime now) {
        return user.getStatus() == UserStatus.BLOCKED
                && user.getSuspensionType() == SuspensionType.TEMPORARY
                && user.getSuspendedUntil() != null
                && !user.getSuspendedUntil().isAfter(now);
    }

    private void reactivateExpired(User user, LocalDateTime now) {
        user.setStatus(UserStatus.ACTIVE);
        user.setReactivatedAt(now);
        user.setReactivatedByUser(null);
        user.setReactivationReason(AUTOMATIC_REACTIVATION_REASON);
        userRepository.save(user);
        publish(user, NotificationEventType.ACCOUNT_REACTIVATED, Map.of(
                "email", user.getEmail(),
                "reason", AUTOMATIC_REACTIVATION_REASON));
    }

    private void publish(User user, NotificationEventType eventType, Map<String, String> data) {
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                user.getId(), eventType, NotificationReferenceType.USER, user.getId(), data));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
