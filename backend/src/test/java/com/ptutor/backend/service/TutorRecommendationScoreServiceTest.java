package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.ComplaintCategory;
import com.ptutor.backend.entity.enums.ComplaintFaultParty;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.TutorComplaintPenaltyRepository;
import com.ptutor.backend.repository.TutorComplaintPenaltyRepository.FaultComplaint;
import com.ptutor.backend.repository.TutorRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TutorRecommendationScoreServiceTest {

    @Mock TutorRepository tutorRepository;
    @Mock CertificateRepository certificateRepository;
    @Mock TutorComplaintPenaltyRepository complaintPenaltyRepository;

    private TutorRecommendationScoreService scoreService;

    @BeforeEach
    void setUp() {
        scoreService = new TutorRecommendationScoreService(
                tutorRepository,
                certificateRepository,
                complaintPenaltyRepository,
                new RecommendationSettings(),
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
        assertThat(updated.getScoreFormulaVersion()).isEqualTo("v2");
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

    @Test
    void scoreHasNoComplaintPenaltyWithoutFaultComplaints() {
        Tutor tutor = scoredTutor();

        Tutor updated = scoreService.recalculateByTutorId(tutor.getId());

        // Same total as v1 for a tutor without complaints.
        assertThat(updated.getRecommendationScore()).isEqualByComparingTo("71.33");
        assertThat(updated.getScoreBreakdown().get("complaintPenalty").decimalValue()).isEqualByComparingTo("0");
        assertThat(updated.getScoreBreakdown().get("faultComplaints").intValue()).isZero();
        // Only complaints from the last 365 days are asked for; contracts are not counted when there are none.
        verify(complaintPenaltyRepository).findFaultComplaints(tutor.getId(), LocalDateTime.of(2025, 9, 27, 5, 0));
        verify(complaintPenaltyRepository, never()).countSignedContracts(any());
    }

    /** The four examples of design 06, section 6.8. */
    @ParameterizedTest
    @CsvSource({
            "ABSENCE, 1, 0, 8.00, 1.00",
            "ABSENCE, 1, 20, 1.60, 1.00",
            "ABSENCE, 3, 5, 12.00, 3.00",
            "SERIOUS_MISCONDUCT, 1, 0, 20.00, 3.00"
    })
    void complaintPenaltyFollowsTheDesignExamples(
            ComplaintCategory category, int complaints, long contracts, String penalty, String weight) {
        Tutor tutor = scoredTutor();
        faultComplaints(tutor, contracts, java.util.Collections.nCopies(
                complaints, new FaultComplaint(category, ComplaintFaultParty.RESPONDENT, false)));

        Tutor updated = scoreService.recalculateByTutorId(tutor.getId());

        assertThat(updated.getScoreBreakdown().get("complaintPenalty").decimalValue()).isEqualByComparingTo(penalty);
        assertThat(updated.getScoreBreakdown().get("faultComplaints").intValue()).isEqualTo(complaints);
        assertThat(updated.getScoreBreakdown().get("complaintWeight").decimalValue()).isEqualByComparingTo(weight);
        assertThat(updated.getRecommendationScore())
                .isEqualByComparingTo(new BigDecimal("71.33").subtract(new BigDecimal(penalty)));
    }

    @ParameterizedTest
    @CsvSource({
            "LATE_OR_SHORT, RESPONDENT, false, 0.50",
            "TEACHING_QUALITY, RESPONDENT, false, 0.50",
            "MISCONDUCT, RESPONDENT, false, 1.50",
            "OTHER, RESPONDENT, false, 1.00",
            // Concluded before categories existed.
            ", RESPONDENT, false, 1.00",
            "MISCONDUCT, BOTH, false, 0.75",
            // Sent by the tutor, who was found at fault: the category describes the student.
            "SERIOUS_MISCONDUCT, COMPLAINANT, true, 1.00",
            "SERIOUS_MISCONDUCT, BOTH, true, 0.50"
    })
    void complaintWeightDependsOnCategoryRoleAndSharedFault(
            ComplaintCategory category, ComplaintFaultParty faultParty, boolean sentByTutor, String weight) {
        Tutor tutor = scoredTutor();
        faultComplaints(tutor, 0, List.of(new FaultComplaint(category, faultParty, sentByTutor)));

        Tutor updated = scoreService.recalculateByTutorId(tutor.getId());

        assertThat(updated.getScoreBreakdown().get("complaintWeight").decimalValue()).isEqualByComparingTo(weight);
        // 40 × W / (0 + 5)
        assertThat(updated.getScoreBreakdown().get("complaintPenalty").decimalValue())
                .isEqualByComparingTo(new BigDecimal(weight).multiply(BigDecimal.valueOf(8)));
    }

    /** The inputs of the first test without the suspension: 71.33 before any complaint penalty. */
    private Tutor scoredTutor() {
        UUID tutorId = UUID.randomUUID();
        Tutor tutor = Tutor.builder()
                .user(User.builder().suspensionCount(0).build())
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
        when(certificateRepository.countByTutor_IdAndStatus(tutorId, CertificateStatus.VERIFIED)).thenReturn(2L);
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return tutor;
    }

    private void faultComplaints(Tutor tutor, long contracts, List<FaultComplaint> complaints) {
        when(complaintPenaltyRepository.findFaultComplaints(eq(tutor.getId()), any())).thenReturn(complaints);
        when(complaintPenaltyRepository.countSignedContracts(tutor.getId())).thenReturn(contracts);
    }
}
