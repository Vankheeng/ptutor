package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ptutor.backend.dto.request.SuspendAccountRequest;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.SuspensionType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountSuspensionServiceTest {

    @Mock UserRepository userRepository;
    @Mock StudentRepository studentRepository;
    @Mock TutorRepository tutorRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock ApplicationEventPublisher eventPublisher;

    private AccountSuspensionService service;
    private User target;
    private User actor;
    private UUID targetId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        service = new AccountSuspensionService(
                userRepository, studentRepository, tutorRepository, employeeRepository,
                refreshTokenService, eventPublisher,
                Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC));
        targetId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        target = User.builder()
                .email("student@example.com")
                .status(UserStatus.ACTIVE)
                .suspensionCount(0)
                .build();
        target.setId(targetId);
        actor = User.builder().email("admin@example.com").status(UserStatus.ACTIVE).build();
        actor.setId(actorId);

        when(studentRepository.findByUser_Id(targetId))
                .thenReturn(Optional.of(Student.builder().user(target).build()));
        when(tutorRepository.findByUser_Id(targetId)).thenReturn(Optional.empty());
    }

    @Test
    void suspendsTemporarilyAndRevokesRefreshTokens() {
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(employeeRepository.findByUser_Id(actorId))
                .thenReturn(Optional.of(Employee.builder().user(actor).build()));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        var response = service.suspend(actorId, targetId, new SuspendAccountRequest(
                SuspensionType.TEMPORARY,
                "Repeatedly harassed another platform user.",
                Instant.parse("2026-09-27T08:00:00Z")));

        assertThat(response.status()).isEqualTo(UserStatus.BLOCKED);
        assertThat(response.suspensionType()).isEqualTo(SuspensionType.TEMPORARY);
        assertThat(response.suspensionCount()).isEqualTo(1);
        assertThat(target.getSuspendedByUser()).isEqualTo(actor);
        verify(refreshTokenService).revokeAllForUser(targetId);
        verify(eventPublisher).publishEvent(any(NotificationDomainEvent.class));
    }

    @Test
    void rejectsPermanentSuspensionWithEndTime() {
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(employeeRepository.findByUser_Id(actorId))
                .thenReturn(Optional.of(Employee.builder().user(actor).build()));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.suspend(actorId, targetId, new SuspendAccountRequest(
                SuspensionType.PERMANENT,
                "Repeatedly harassed another platform user.",
                Instant.parse("2026-09-27T08:00:00Z"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must not have an end time");
    }

    @Test
    void manuallyReactivatesAndKeepsLastSuspensionData() {
        target.setStatus(UserStatus.BLOCKED);
        target.setSuspensionType(SuspensionType.PERMANENT);
        target.setSuspensionReason("Repeated platform abuse");
        when(userRepository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(employeeRepository.findByUser_Id(actorId))
                .thenReturn(Optional.of(Employee.builder().user(actor).build()));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        var response = service.reactivate(actorId, targetId, "Appeal was reviewed and accepted.");

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.suspensionReason()).isEqualTo("Repeated platform abuse");
        assertThat(response.reactivationReason()).isEqualTo("Appeal was reviewed and accepted.");
        assertThat(target.getReactivatedByUser()).isEqualTo(actor);
    }
}
