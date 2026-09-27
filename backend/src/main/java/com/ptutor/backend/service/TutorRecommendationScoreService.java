package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.CertificateStatus;
import com.ptutor.backend.entity.enums.TutorProfileStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.CertificateRepository;
import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class TutorRecommendationScoreService {

    public static final String FORMULA_VERSION = "v1";

    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO.setScale(2);

    private final TutorRepository tutorRepository;
    private final CertificateRepository certificateRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public Tutor recalculateByUserId(UUID userId) {
        Tutor tutor = tutorRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> tutorNotFound(userId));
        return calculateAndSave(tutor);
    }

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

        BigDecimal score = rating
                .add(completion)
                .add(acceptance)
                .add(response)
                .add(experience)
                .add(verification)
                .subtract(violationPenalty);
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

        tutor.setRecommendationScore(score);
        tutor.setScoreFormulaVersion(FORMULA_VERSION);
        tutor.setScoreUpdatedAt(LocalDateTime.now(clock));
        tutor.setScoreBreakdown(breakdown);
        return tutorRepository.save(tutor);
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

    private ApiException tutorNotFound(UUID userId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_NOT_FOUND", "Tutor not found for user: " + userId);
    }
}
