package com.ptutor.backend.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Certificate;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.tutor.dto.CertificateRequest;
import com.ptutor.backend.tutor.dto.CertificateResponse;
import com.ptutor.backend.tutor.repository.CertificateRepository;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock CertificateRepository certificateRepository;
    @Mock TutorRepository tutorRepository;

    private CertificateService certificateService;
    private UUID userId;
    private UUID tutorId;

    @BeforeEach
    void setUp() {
        certificateService = new CertificateService(certificateRepository, tutorRepository);
        userId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
    }

    @Test
    void createSetsPendingStatusAndUsesFlushForGeneratedTimestamps() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        when(certificateRepository.saveAndFlush(any(Certificate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CertificateResponse response = certificateService.create(userId, request());

        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateRepository).saveAndFlush(captor.capture());

        Certificate saved = captor.getValue();
        assertThat(saved.getTutor().getId()).isEqualTo(tutorId);
        assertThat(saved.getName()).isEqualTo("IELTS 8.0");
        assertThat(saved.getStatus()).isEqualTo(CertificateStatus.PENDING);
        assertThat(response.tutorId()).isEqualTo(tutorId);
        assertThat(response.status()).isEqualTo(CertificateStatus.PENDING);
    }

    @Test
    void findMineWithoutStatusReturnsAllCertificates() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        Certificate pending = certificate(CertificateStatus.PENDING);
        Certificate verified = certificate(CertificateStatus.VERIFIED);
        when(certificateRepository.findAllByTutor_IdOrderByCreatedAtDesc(tutorId))
                .thenReturn(List.of(pending, verified));

        List<CertificateResponse> response = certificateService.findMine(userId, null);

        assertThat(response).extracting(CertificateResponse::status)
                .containsExactly(CertificateStatus.PENDING, CertificateStatus.VERIFIED);
        verify(certificateRepository).findAllByTutor_IdOrderByCreatedAtDesc(tutorId);
    }

    @Test
    void findMineFiltersByStatusWhenStatusIsProvided() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        Certificate rejected = certificate(CertificateStatus.REJECTED);
        when(certificateRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, CertificateStatus.REJECTED)).thenReturn(List.of(rejected));

        List<CertificateResponse> response = certificateService.findMine(userId, CertificateStatus.REJECTED);

        assertThat(response).singleElement()
                .extracting(CertificateResponse::status)
                .isEqualTo(CertificateStatus.REJECTED);
        verify(certificateRepository).findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, CertificateStatus.REJECTED);
    }

    @Test
    void findVerifiedByTutorIdReturnsOnlyVerifiedCertificates() {
        when(tutorRepository.findById(tutorId)).thenReturn(Optional.of(tutor()));
        Certificate verified = certificate(CertificateStatus.VERIFIED);
        when(certificateRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, CertificateStatus.VERIFIED)).thenReturn(List.of(verified));

        List<CertificateResponse> response = certificateService.findVerifiedByTutorId(tutorId);

        assertThat(response).singleElement()
                .extracting(CertificateResponse::status)
                .isEqualTo(CertificateStatus.VERIFIED);
        verify(certificateRepository).findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, CertificateStatus.VERIFIED);
    }

    @Test
    void findVerifiedByTutorIdRejectsUnknownTutor() {
        when(tutorRepository.findById(tutorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.findVerifiedByTutorId(tutorId))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("TUTOR_NOT_FOUND");
                });
    }

    @Test
    void updateResetsCertificateToPending() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        Certificate rejected = certificate(CertificateStatus.REJECTED);
        UUID certificateId = rejected.getId();
        when(certificateRepository.findByIdAndTutor_Id(certificateId, tutorId))
                .thenReturn(Optional.of(rejected));
        when(certificateRepository.saveAndFlush(rejected)).thenReturn(rejected);

        CertificateResponse response = certificateService.update(userId, certificateId, request());

        assertThat(rejected.getStatus()).isEqualTo(CertificateStatus.PENDING);
        assertThat(rejected.getName()).isEqualTo("IELTS 8.0");
        assertThat(response.status()).isEqualTo(CertificateStatus.PENDING);
        verify(certificateRepository).saveAndFlush(rejected);
    }

    @Test
    void updateRejectsVerifiedCertificate() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        Certificate verified = certificate(CertificateStatus.VERIFIED);
        UUID certificateId = verified.getId();
        when(certificateRepository.findByIdAndTutor_Id(certificateId, tutorId))
                .thenReturn(Optional.of(verified));

        assertThatThrownBy(() -> certificateService.update(userId, certificateId, request()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("VERIFIED_CERTIFICATE_IMMUTABLE");
                });
        verify(certificateRepository, never()).saveAndFlush(any(Certificate.class));
    }

    @Test
    void deleteOnlyDeletesCertificateBelongingToTutor() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        Certificate certificate = certificate(CertificateStatus.PENDING);
        when(certificateRepository.findByIdAndTutor_Id(certificate.getId(), tutorId))
                .thenReturn(Optional.of(certificate));

        certificateService.delete(userId, certificate.getId());

        verify(certificateRepository).delete(certificate);
    }

    @Test
    void rejectsUsersWithoutTutorProfile() {
        UUID anotherUserId = UUID.randomUUID();
        when(tutorRepository.findByUser_Id(anotherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.findMine(anotherUserId, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only a tutor can manage certificates");
    }

    private Certificate certificate(CertificateStatus status) {
        Certificate certificate = Certificate.builder()
                .tutor(tutor())
                .name("Old certificate")
                .status(status)
                .build();
        certificate.setId(UUID.randomUUID());
        certificate.setCreatedAt(LocalDateTime.now());
        certificate.setUpdatedAt(LocalDateTime.now());
        return certificate;
    }

    private Tutor tutor() {
        Tutor tutor = Tutor.builder().build();
        tutor.setId(tutorId);
        return tutor;
    }

    private CertificateRequest request() {
        return new CertificateRequest(
                " IELTS 8.0 ",
                "British Council",
                "English language certificate",
                LocalDate.of(2025, 5, 20),
                LocalDate.of(2027, 5, 20),
                "https://cdn.example.com/certificates/ielts.pdf");
    }
}
