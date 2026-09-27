package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.TutorRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TutorRecommendationScoreServiceTest {

    @Mock TutorRepository tutorRepository;
    @Mock CertificateRepository certificateRepository;

    private TutorRecommendationScoreService scoreService;

    @BeforeEach
    void setUp() {
        scoreService = new TutorRecommendationScoreService(
                tutorRepository,
                certificateRepository,
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-09-27T05:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void calculatesVersionedScoreFromSystemMetrics() {
        UUID tutorId = UUID.randomUUID();
        User user = User.builder().suspensionCount(1).build();
        Tutor tutor = Tutor.builder()
                .user(user)
                .averageRating(new BigDecimal("4.50"))
                .totalReviews(10)
                .completedContractsCount(10)
                .totalStudentsTaught(5)
                .acceptanceRate(new BigDecimal("80.00"))
                .avgResponseTimeHours(new BigDecimal("12.00"))
                .profileStatus(TutorProfileStatus.VERIFIED)
                .build();
        tutor.setId(tutorId);

        when(tutorRepository.findByIdForScoreUpdate(tutorId)).thenReturn(Optional.of(tutor));
        when(certificateRepository.countByTutor_IdAndStatus(tutorId, CertificateStatus.VERIFIED))
                .thenReturn(2L);
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tutor updated = scoreService.recalculateByTutorId(tutorId);

        assertThat(updated.getRecommendationScore()).isEqualByComparingTo("66.33");
        assertThat(updated.getScoreFormulaVersion()).isEqualTo("v1");
        assertThat(updated.getScoreUpdatedAt()).isEqualTo("2026-09-27T05:00:00");
        assertThat(updated.getScoreBreakdown().get("violationPenalty").decimalValue())
                .isEqualByComparingTo("5");
        verify(tutorRepository).save(tutor);
    }

    @Test
    void clampsScoreAtZero() {
        UUID tutorId = UUID.randomUUID();
        User user = User.builder().suspensionCount(99).build();
        Tutor tutor = Tutor.builder()
                .user(user)
                .averageRating(BigDecimal.ZERO)
                .totalReviews(100)
                .completedContractsCount(0)
                .totalStudentsTaught(0)
                .acceptanceRate(BigDecimal.ZERO)
                .avgResponseTimeHours(BigDecimal.valueOf(100))
                .profileStatus(TutorProfileStatus.REJECTED)
                .build();
        tutor.setId(tutorId);

        when(tutorRepository.findByIdForScoreUpdate(tutorId)).thenReturn(Optional.of(tutor));
        when(certificateRepository.countByTutor_IdAndStatus(tutorId, CertificateStatus.VERIFIED))
                .thenReturn(0L);
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(scoreService.recalculateByTutorId(tutorId).getRecommendationScore())
                .isEqualByComparingTo("0.00");
    }
}
