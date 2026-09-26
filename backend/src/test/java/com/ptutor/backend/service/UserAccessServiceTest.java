package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.SuspensionType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceTest {

    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void reactivatesExpiredTemporarySuspensionLazily() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("student@example.com")
                .status(UserStatus.BLOCKED)
                .suspensionType(SuspensionType.TEMPORARY)
                .suspendedUntil(LocalDateTime.of(2026, 9, 26, 7, 59))
                .build();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        User result = new UserAccessService(userRepository, eventPublisher, clock).refreshAccessState(userId);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.getReactivationReason())
                .isEqualTo(UserAccessService.AUTOMATIC_REACTIVATION_REASON);
        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(any(NotificationDomainEvent.class));
    }

    @Test
    void activeAccountDoesNotTakeWriteLock() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().status(UserStatus.ACTIVE).build();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        new UserAccessService(userRepository, eventPublisher, clock).refreshAccessState(userId);

        verify(userRepository, never()).findByIdForUpdate(userId);
    }
}
