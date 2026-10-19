package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.CertificateMapper;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.ReviewRepository;
import com.ptutor.backend.repository.TutorRepository;

@ExtendWith(MockitoExtension.class)
class AdminTutorProfileReviewServiceTest {

    @Mock TutorRepository tutorRepository;
    @Mock CertificateRepository certificateRepository;
    @Mock ContractRepository contractRepository;
    @Mock LessonRepository lessonRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock CertificateMapper certificateMapper;
    @Mock ContractMapper contractMapper;
    @Mock TutorRecommendationScoreService scoreService;
    @Mock ApplicationEventPublisher eventPublisher;

    private AdminTutorService service;
    private UUID reviewerUserId;
    private UUID tutorUserId;
    private Tutor tutor;

    @BeforeEach
    void setUp() {
        service = new AdminTutorService(
                tutorRepository, certificateRepository, contractRepository, lessonRepository, reviewRepository,
                employeeRepository, certificateMapper, contractMapper, scoreService, eventPublisher,
                Clock.fixed(Instant.parse("2026-10-18T08:00:00Z"), ZoneOffset.UTC));
        reviewerUserId = UUID.randomUUID();
        tutorUserId = UUID.randomUUID();

        User tutorUser = User.builder().email("tutor@example.com").firstName("Nguyen").lastName("An").build();
        tutorUser.setId(tutorUserId);
        tutor = Tutor.builder().user(tutorUser).profileStatus(TutorProfileStatus.PENDING).build();
        tutor.setId(UUID.randomUUID());
    }

    @Test
    void approvingProfileNotifiesTutor() {
        givenPendingTutorAndReviewer();

        service.verifyProfile(reviewerUserId, tutorUserId);

        NotificationDomainEvent event = publishedEvent();
        assertThat(tutor.getProfileStatus()).isEqualTo(TutorProfileStatus.VERIFIED);
        assertThat(event.recipientUserId()).isEqualTo(tutorUserId);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.TUTOR_PROFILE_APPROVED);
        assertThat(event.referenceType()).isEqualTo(NotificationReferenceType.USER);
    }

    @Test
    void rejectingProfileNotifiesTutorWithReason() {
        givenPendingTutorAndReviewer();

        service.rejectProfile(reviewerUserId, tutorUserId, "  Education does not match the certificates.  ");

        NotificationDomainEvent event = publishedEvent();
        assertThat(tutor.getProfileStatus()).isEqualTo(TutorProfileStatus.REJECTED);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.TUTOR_PROFILE_REJECTED);
        assertThat(event.data()).containsEntry("reason", "Education does not match the certificates.");
    }

    @Test
    void invalidRejectionReasonSendsNoNotification() {
        assertThatThrownBy(() -> service.rejectProfile(reviewerUserId, tutorUserId, "Too short"))
                .isInstanceOf(ApiException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectionNotificationTellsTutorHowToResubmit() {
        NotificationContent content = new NotificationTemplateFactory().create(NotificationDomainEvent.of(
                tutorUserId, NotificationEventType.TUTOR_PROFILE_REJECTED, NotificationReferenceType.USER,
                tutorUserId, java.util.Map.of("reason", "Missing teaching methodology")));

        assertThat(content.title()).isEqualTo("Tutor profile rejected");
        assertThat(content.content()).contains("Missing teaching methodology.", "submit it for review again");
    }

    @Test
    void rejectionNotificationDoesNotDoubleTheReasonFullStop() {
        NotificationContent content = new NotificationTemplateFactory().create(NotificationDomainEvent.of(
                tutorUserId, NotificationEventType.TUTOR_PROFILE_REJECTED, NotificationReferenceType.USER,
                tutorUserId, java.util.Map.of("reason", "Introduction is too short.")));

        assertThat(content.content()).contains("Introduction is too short. Update").doesNotContain("..");
    }

    private void givenPendingTutorAndReviewer() {
        User reviewerUser = User.builder().firstName("Tran").lastName("Binh").build();
        reviewerUser.setId(reviewerUserId);
        Employee reviewer = Employee.builder()
                .user(reviewerUser)
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.CONTENT_REVIEWER)
                .build();
        when(tutorRepository.findByUserIdForUpdate(tutorUserId)).thenReturn(Optional.of(tutor));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(tutorRepository.findAdminDetailByUserId(tutorUserId)).thenReturn(Optional.of(tutor));
    }

    private NotificationDomainEvent publishedEvent() {
        ArgumentCaptor<NotificationDomainEvent> captor = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
