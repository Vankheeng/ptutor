package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.response.AdminCertificateDetailResponse;
import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.EmployeeRepository;

@ExtendWith(MockitoExtension.class)
class AdminCertificateServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T08:30:00Z");

    @Mock CertificateRepository certificateRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock NotificationService notificationService;

    private AdminCertificateService service;
    private UUID reviewerUserId;
    private UUID certificateId;
    private Certificate certificate;
    private Employee reviewer;

    @BeforeEach
    void setUp() {
        service = new AdminCertificateService(certificateRepository, employeeRepository,
                notificationService, Clock.fixed(NOW, ZoneOffset.UTC));
        reviewerUserId = UUID.randomUUID();
        certificateId = UUID.randomUUID();
        certificate = certificate(CertificateStatus.PENDING);
        reviewer = employee(reviewerUserId);
    }

    @Test
    void approveStoresReviewMetadataAndSendsNotification() {
        when(certificateRepository.findByIdForUpdate(certificateId)).thenReturn(Optional.of(certificate));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(certificateRepository.saveAndFlush(certificate)).thenReturn(certificate);

        AdminCertificateDetailResponse response = service.approve(reviewerUserId, certificateId);

        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.VERIFIED);
        assertThat(certificate.getReviewedBy()).isSameAs(reviewer);
        assertThat(certificate.getReviewedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(certificate.getRejectionReason()).isNull();
        assertThat(response.status()).isEqualTo(CertificateStatus.VERIFIED);
        verify(notificationService).createCertificateReviewNotification(
                certificate.getTutor().getUser(), certificateId.toString(), certificate.getName(), true, null);
    }

    @Test
    void rejectNormalizesReasonAndSendsNotification() {
        when(certificateRepository.findByIdForUpdate(certificateId)).thenReturn(Optional.of(certificate));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(certificateRepository.saveAndFlush(certificate)).thenReturn(certificate);

        AdminCertificateDetailResponse response = service.reject(
                reviewerUserId, certificateId, "  Image is unreadable  ");

        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.REJECTED);
        assertThat(certificate.getRejectionReason()).isEqualTo("Image is unreadable");
        assertThat(response.rejectionReason()).isEqualTo("Image is unreadable");
        verify(notificationService).createCertificateReviewNotification(
                certificate.getTutor().getUser(), certificateId.toString(), certificate.getName(), false,
                "Image is unreadable");
    }

    @Test
    void approveRejectsExpiredCertificate() {
        certificate.setExpiryDate(LocalDate.of(2026, 9, 5));
        when(certificateRepository.findByIdForUpdate(certificateId)).thenReturn(Optional.of(certificate));

        assertThatThrownBy(() -> service.approve(reviewerUserId, certificateId))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("CERTIFICATE_EXPIRED");
                });
    }

    @Test
    void reviewRejectsCertificateThatIsNoLongerPending() {
        certificate.setStatus(CertificateStatus.VERIFIED);
        when(certificateRepository.findByIdForUpdate(certificateId)).thenReturn(Optional.of(certificate));

        assertThatThrownBy(() -> service.approve(reviewerUserId, certificateId))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("CERTIFICATE_ALREADY_REVIEWED"));
    }

    @Test
    void rejectRequiresNonBlankReasonAtServiceBoundary() {
        assertThatThrownBy(() -> service.reject(reviewerUserId, certificateId, "   "))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_REJECTION_REASON");
                });
    }

    @Test
    void reviewRejectsUnknownCertificate() {
        when(certificateRepository.findByIdForUpdate(certificateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(reviewerUserId, certificateId))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Certificate certificate(CertificateStatus status) {
        User tutorUser = User.builder()
                .firstName("Van")
                .lastName("Nguyen")
                .email("tutor@example.com")
                .build();
        Tutor tutor = Tutor.builder().user(tutorUser).build();
        tutor.setId(UUID.randomUUID());
        Certificate value = Certificate.builder()
                .tutor(tutor)
                .name("IELTS 8.0")
                .status(status)
                .expiryDate(LocalDate.of(2027, 5, 20))
                .build();
        value.setId(certificateId);
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private Employee employee(UUID userId) {
        User user = User.builder().firstName("Admin").lastName("User").build();
        user.setId(userId);
        Employee value = Employee.builder().user(user).build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
