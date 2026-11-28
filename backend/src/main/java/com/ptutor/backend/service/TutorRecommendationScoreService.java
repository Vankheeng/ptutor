package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.ComplaintFaultParty;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.TutorComplaintPenaltyRepository;
import com.ptutor.backend.repository.TutorComplaintPenaltyRepository.FaultComplaint;
import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class TutorRecommendationScoreService {

    /** v2: complaints the tutor was found at fault for lower the score (design 06, section 6.8). */
    public static final String FORMULA_VERSION = "v2";

    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO.setScale(2);

    private final TutorRepository tutorRepository;
    private final CertificateRepository certificateRepository;
    private final TutorComplaintPenaltyRepository complaintPenaltyRepository;
    private final RecommendationSettings settings;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public Tutor recalculateByTutorId(UUID tutorId) {
        Tutor tutor = tutorRepository.findByIdForScoreUpdate(tutorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "TUTOR_NOT_FOUND", "Tutor not found: " + tutorId));
        return calculateAndSave(tutor);
    }

    private Tutor calculateAndSave(Tutor tutor) {
        BigDecimal rating = ratingScore(tutor);
        BigDecimal completion = cappedRatio(tutor.getCompletedContractsCount(), 20, 20);
        BigDecimal acceptance = percentageScore(tutor.getAcceptanceRate(), "7.50", 15);
        BigDecimal response = responseScore(tutor.getAvgResponseTimeHours());
        BigDecimal experience = cappedRatio(tutor.getTotalStudentsTaught(), 20, 10);
        long verifiedCertificates = certificateRepository.countByTutor_IdAndStatus(
                tutor.getId(), CertificateStatus.VERIFIED);
        BigDecimal verification = verificationScore(tutor, verifiedCertificates);
        BigDecimal violationPenalty = BigDecimal.valueOf(Math.min(
                15, Math.max(0, valueOrZero(tutor.getUser().getSuspensionCount())) * 5L));
        ComplaintPenalty complaints = complaintPenalty(tutor.getId());

        BigDecimal score = rating
                .add(completion)
                .add(acceptance)
                .add(response)
                .add(experience)
                .add(verification)
                .subtract(violationPenalty)
                .subtract(complaints.penalty());
        score = score.max(MIN_SCORE).min(MAX_SCORE).setScale(2, RoundingMode.HALF_UP);

        ObjectNode breakdown = objectMapper.createObjectNode();
        breakdown.put("rating", rating);
        breakdown.put("completion", completion);
        breakdown.put("acceptance", acceptance);
        breakdown.put("response", response);
        breakdown.put("experience", experience);
        breakdown.put("verification", verification);
        breakdown.put("violationPenalty", violationPenalty);
        breakdown.put("verifiedCertificates", verifiedCertificates);
        breakdown.put("complaintPenalty", complaints.penalty());
        breakdown.put("faultComplaints", complaints.count());
        breakdown.put("complaintWeight", complaints.weight());

        tutor.setRecommendationScore(score);
        tutor.setScoreFormulaVersion(FORMULA_VERSION);
        tutor.setScoreUpdatedAt(LocalDateTime.now(clock));
        tutor.setScoreBreakdown(breakdown);
        return tutorRepository.save(tutor);
    }

    /**
     * Complaints concluded with the tutor at fault in the window, as a smoothed rate over signed contracts:
     * min(max, scale × W / (n + prior)). W adds up one weight per complaint.
     */
    private ComplaintPenalty complaintPenalty(UUID tutorId) {
        List<FaultComplaint> complaints = complaintPenaltyRepository.findFaultComplaints(
                tutorId, LocalDateTime.now(clock).minusDays(settings.getComplaintWindowDays()));
        double weight = complaints.stream().mapToDouble(this::complaintWeight).sum();
        BigDecimal roundedWeight = BigDecimal.valueOf(weight).setScale(2, RoundingMode.HALF_UP);
        if (complaints.isEmpty()) {
            return new ComplaintPenalty(MIN_SCORE, 0, roundedWeight);
        }
        long contracts = complaintPenaltyRepository.countSignedContracts(tutorId);
        double penalty = Math.min(settings.getComplaintMaxPenalty(),
                settings.getComplaintPenaltyScale() * weight / (contracts + settings.getComplaintPriorContracts()));
        return new ComplaintPenalty(
                BigDecimal.valueOf(penalty).setScale(2, RoundingMode.HALF_UP), complaints.size(), roundedWeight);
    }

    private double complaintWeight(FaultComplaint complaint) {
        double weight = complaint.sentByTutor()
                ? settings.getComplaintComplainantWeight()
                : categoryWeight(complaint);
        return complaint.faultParty() == ComplaintFaultParty.BOTH ? weight * settings.getComplaintBothFactor() : weight;
    }

    private double categoryWeight(FaultComplaint complaint) {
        if (complaint.category() == null) {
            return settings.getComplaintWeightOther();
        }
        return switch (complaint.category()) {
            case ABSENCE -> settings.getComplaintWeightAbsence();
            case LATE_OR_SHORT -> settings.getComplaintWeightLateOrShort();
            case TEACHING_QUALITY -> settings.getComplaintWeightTeachingQuality();
            case MISCONDUCT -> settings.getComplaintWeightMisconduct();
            case SERIOUS_MISCONDUCT -> settings.getComplaintWeightSeriousMisconduct();
            case OTHER -> settings.getComplaintWeightOther();
        };
    }

    private record ComplaintPenalty(BigDecimal penalty, int count, BigDecimal weight) {
    }

    private BigDecimal ratingScore(Tutor tutor) {
        BigDecimal rating = tutor.getAverageRating() == null
                ? BigDecimal.ZERO : tutor.getAverageRating().max(BigDecimal.ZERO).min(BigDecimal.valueOf(5));
        int reviews = Math.max(0, valueOrZero(tutor.getTotalReviews()));
        BigDecimal confidence = BigDecimal.valueOf(reviews)
                .divide(BigDecimal.valueOf(reviews + 5L), 8, RoundingMode.HALF_UP);
        BigDecimal weightedRating = confidence.multiply(rating)
                .add(BigDecimal.ONE.subtract(confidence).multiply(new BigDecimal("3.50")));
        return weightedRating.multiply(BigDecimal.valueOf(7)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal cappedRatio(Integer value, int cap, int maximumPoints) {
        int normalized = Math.min(cap, Math.max(0, valueOrZero(value)));
        return BigDecimal.valueOf(normalized)
                .multiply(BigDecimal.valueOf(maximumPoints))
                .divide(BigDecimal.valueOf(cap), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageScore(BigDecimal percentage, String neutral, int maximumPoints) {
        if (percentage == null) {
            return new BigDecimal(neutral);
        }
        BigDecimal normalized = percentage.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        return normalized.multiply(BigDecimal.valueOf(maximumPoints))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal responseScore(BigDecimal hours) {
        if (hours == null) {
            return new BigDecimal("5.00");
        }
        if (hours.compareTo(BigDecimal.ONE) <= 0) {
            return new BigDecimal("10.00");
        }
        if (hours.compareTo(BigDecimal.valueOf(48)) >= 0) {
            return MIN_SCORE;
        }
        return BigDecimal.valueOf(48).subtract(hours)
                .multiply(BigDecimal.TEN)
                .divide(BigDecimal.valueOf(47), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal verificationScore(Tutor tutor, long verifiedCertificates) {
        int points = tutor.getProfileStatus() == TutorProfileStatus.VERIFIED ? 5 : 0;
        if (verifiedCertificates > 0) {
            points += 5;
        }
        return BigDecimal.valueOf(points).setScale(2);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
