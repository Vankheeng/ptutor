package com.ptutor.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.dto.request.SuspendAccountRequest;
import com.ptutor.backend.dto.response.AdminUserAccountResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.SuspensionType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountSuspensionService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final EmployeeRepository employeeRepository;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserAccountResponse> findAll(
            UserRole role, UserStatus status, String keyword, Pageable pageable) {
        if (role != null && role != UserRole.STUDENT && role != UserRole.TUTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_TARGET_NOT_SUPPORTED",
                    "Only STUDENT and TUTOR accounts can be managed here");
        }
        // Keep the search parameter typed as VARCHAR for PostgreSQL. Binding a null
        // value in the JPQL LOWER/CONCAT expression can otherwise be inferred as BYTEA.
        String normalizedKeyword = keyword == null || keyword.isBlank() ? "" : keyword.strip();
        Page<User> users = userRepository.findAdminUsers(
                role == null ? null : role.name(), status, normalizedKeyword, pageable);
        return PageResponse.from(users, users.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public AdminUserAccountResponse findById(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> userNotFound(userId));
        ensureSupportedTarget(user);
        return toResponse(user);
    }

    @Transactional
    public AdminUserAccountResponse suspend(UUID actorUserId, UUID targetUserId, SuspendAccountRequest request) {
        if (actorUserId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_SUSPENSION_NOT_ALLOWED",
                    "You cannot suspend your own account");
        }
        User target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> userNotFound(targetUserId));
        ensureSupportedTarget(target);
        if (target.getStatus() == UserStatus.BLOCKED) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_SUSPENDED",
                    "The account is already suspended");
        }
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE",
                    "Only an active account can be suspended");
        }

        User actor = findActor(actorUserId, "INVALID_SUSPENSION_ACTOR");
        LocalDateTime now = now();
        LocalDateTime suspendedUntil = validateAndConvertEnd(request, now);

        target.setStatus(UserStatus.BLOCKED);
        target.setSuspensionType(request.type());
        target.setSuspensionReason(request.reason().strip());
        target.setSuspendedAt(now);
        target.setSuspendedUntil(suspendedUntil);
        target.setSuspendedByUser(actor);
        target.setReactivatedAt(null);
        target.setReactivatedByUser(null);
        target.setReactivationReason(null);
        target.setSuspensionCount((target.getSuspensionCount() == null ? 0 : target.getSuspensionCount()) + 1);

        User saved = userRepository.saveAndFlush(target);
        refreshTokenService.revokeAllForUser(targetUserId);
        publishSuspension(saved);
        return toResponse(saved);
    }

    @Transactional
    public AdminUserAccountResponse reactivate(UUID actorUserId, UUID targetUserId, String reason) {
        if (reason == null || reason.isBlank() || reason.strip().length() < 10 || reason.strip().length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REACTIVATION_REASON",
                    "Reactivation reason must contain between 10 and 1000 characters");
        }
        User target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> userNotFound(targetUserId));
        ensureSupportedTarget(target);
        if (target.getStatus() != UserStatus.BLOCKED) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_SUSPENDED",
                    "Only a suspended account can be reactivated");
        }
        User actor = findActor(actorUserId, "INVALID_REACTIVATION_ACTOR");

        target.setStatus(UserStatus.ACTIVE);
        target.setReactivatedAt(now());
        target.setReactivatedByUser(actor);
        target.setReactivationReason(reason.strip());
        User saved = userRepository.saveAndFlush(target);
        publish(saved, NotificationEventType.ACCOUNT_REACTIVATED, Map.of(
                "email", saved.getEmail(),
                "reason", saved.getReactivationReason()));
        return toResponse(saved);
    }

    private LocalDateTime validateAndConvertEnd(SuspendAccountRequest request, LocalDateTime now) {
        if (request.type() == SuspensionType.PERMANENT) {
            if (request.suspendedUntil() != null) {
                throw invalidPeriod("Permanent suspension must not have an end time");
            }
            return null;
        }
        if (request.suspendedUntil() == null) {
            throw invalidPeriod("Temporary suspension requires an end time");
        }
        LocalDateTime end = LocalDateTime.ofInstant(request.suspendedUntil(), ZoneOffset.UTC);
        if (!end.isAfter(now)) {
            throw invalidPeriod("Suspension end time must be in the future");
        }
        return end;
    }

    private void ensureSupportedTarget(User user) {
        boolean student = studentRepository.findByUser_Id(user.getId()).isPresent();
        boolean tutor = tutorRepository.findByUser_Id(user.getId()).isPresent();
        if (student == tutor) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_TARGET_NOT_SUPPORTED",
                    "Only an account with exactly one STUDENT or TUTOR profile can be managed");
        }
    }

    private UserRole roleOf(User user) {
        if (studentRepository.findByUser_Id(user.getId()).isPresent()) {
            return UserRole.STUDENT;
        }
        if (tutorRepository.findByUser_Id(user.getId()).isPresent()) {
            return UserRole.TUTOR;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_TARGET_NOT_SUPPORTED",
                "Only STUDENT and TUTOR accounts can be managed here");
    }

    private User findActor(UUID actorUserId, String errorCode) {
        if (employeeRepository.findByUser_Id(actorUserId).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode,
                    "Only an employee or administrator can change account status");
        }
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, errorCode,
                        "The current administrator account was not found"));
    }

    private AdminUserAccountResponse toResponse(User user) {
        return new AdminUserAccountResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getPhone(),
                roleOf(user), user.getStatus(), user.getSuspensionType(), user.getSuspensionReason(),
                instant(user.getSuspendedAt()), instant(user.getSuspendedUntil()),
                idOf(user.getSuspendedByUser()), instant(user.getReactivatedAt()),
                idOf(user.getReactivatedByUser()), user.getReactivationReason(),
                user.getSuspensionCount() == null ? 0 : user.getSuspensionCount(),
                instant(user.getCreatedAt()), instant(user.getUpdatedAt()));
    }

    private void publishSuspension(User user) {
        publish(user, NotificationEventType.ACCOUNT_SUSPENDED, Map.of(
                "email", user.getEmail(),
                "reason", user.getSuspensionReason(),
                "type", user.getSuspensionType().name(),
                "until", user.getSuspendedUntil() == null ? "" : instant(user.getSuspendedUntil()).toString()));
    }

    private void publish(User user, NotificationEventType type, Map<String, String> data) {
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                user.getId(), type, NotificationReferenceType.USER, user.getId(), data));
    }

    private UUID idOf(User user) {
        return user == null ? null : user.getId();
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ApiException invalidPeriod(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUSPENSION_PERIOD", message);
    }

    private ApiException userNotFound(UUID userId) {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found: " + userId);
    }
}
