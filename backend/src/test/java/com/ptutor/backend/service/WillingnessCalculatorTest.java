package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.candidate;
import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.PastDecision;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;

class WillingnessCalculatorTest {

    private static final double P0 = 0.6;

    private final RecommendationSettings settings = new RecommendationSettings();
    private final FeatureCalculator features = new FeatureCalculator(settings);
    private final WillingnessCalculator calculator = new WillingnessCalculator(settings);

    // ---- b_cap

    @ParameterizedTest
    @CsvSource({ "1, 0, 1.00", "1, 2, 0.50", "1, 6, 0.25", "3, 2, 0.75", "0, 2, 0.50" })
    void capacityDropsAsApplicationsQueueForTheRemainingPlaces(int remaining, int pending, double expected) {
        assertThat(calculator.capacity(remaining, pending)).isCloseTo(expected, within(1e-9));
    }

    // ---- b_hist

    @Test
    void tutorWithoutHistoryGetsThePlatformRate() {
        assertThat(calculator.history(context().build(), candidate().build(), P0)).isCloseTo(P0, within(1e-9));
    }

    @Test
    void historyWeighsPastDecisionsByHowSimilarTheStudentWas() {
        // Student: grade level 9, budget 200k, wants OFFLINE.
        QueryContext context = context().gradeLevel(9).maxPrice("200000").mode(ModePreference.OFFLINE).build();
        List<PastDecision> decisions = List.of(
                // same grade, same price, same mode -> k = 0.5 + 0.3 + 0.2 = 1.0, accepted
                decision(9, "200000", LearningMode.OFFLINE, true),
                // one grade apart, price 250k (|200-250|/250 = 0.2 -> 0.8), other mode -> 0.25 + 0.24 + 0 = 0.49, rejected
                decision(8, "250000", LearningMode.ONLINE, false),
                // far grade, price 100k (|200-100|/100 = 1 -> 0), same mode -> 0 + 0 + 0.2 = 0.2, accepted
                decision(5, "100000", LearningMode.OFFLINE, true));

        double history = calculator.history(context, candidate().decisions(decisions).build(), P0);

        assertThat(history).isCloseTo((1.0 + 0.2 + 2 * P0) / (1.0 + 0.49 + 0.2 + 2), within(1e-9));
    }

    @Test
    void historyOfATutorWhoRejectsSimilarStudentsIsLowAndOfOneWhoAcceptsThemIsHigh() {
        QueryContext context = context().gradeLevel(9).maxPrice("200000").build();
        List<PastDecision> rejects = List.of(decision(9, "200000", LearningMode.ONLINE, false),
                decision(9, "200000", LearningMode.ONLINE, false), decision(9, "200000", LearningMode.ONLINE, false),
                decision(9, "200000", LearningMode.ONLINE, false));
        List<PastDecision> accepts = rejects.stream()
                .map(d -> new PastDecision(d.gradeLevel(), d.proposedPrice(), d.learningMode(), true)).toList();

        assertThat(calculator.history(context, candidate().decisions(rejects).build(), P0))
                .isCloseTo((0 + 1.2) / (4 + 2), within(1e-9));
        assertThat(calculator.history(context, candidate().decisions(accepts).build(), P0))
                .isCloseTo((4 + 1.2) / (4 + 2), within(1e-9));
    }

    @Test
    void historyUsesThePostingPriceWhenTheStudentGaveNoBudgetAndIsNeutralOnUnknownValues() {
        // No budget -> the posting price (150k) is compared; unknown grade and unknown past price -> 0.5 each.
        QueryContext context = context().gradeLevel(null).build();
        TeachingCandidate candidate = candidate().price("150000").decisions(List.of(
                decision(9, "150000", LearningMode.ONLINE, true),     // 0.5*0.5 + 0.3*1 + 0.2*1 = 0.75
                decision(9, null, LearningMode.ONLINE, false))).build(); // 0.25 + 0.15 + 0.2 = 0.60

        assertThat(calculator.history(context, candidate, P0))
                .isCloseTo((0.75 + 2 * P0) / (0.75 + 0.60 + 2), within(1e-9));
    }

    // ---- b_fit

    @ParameterizedTest
    @CsvSource({ "200000, 180000, 1.0", "180000, 180000, 1.0", "153000, 180000, 0.5", "126000, 180000, 0.0",
        "100000, 180000, 0.0" })
    void tutorAcceptsABudgetAtOrAboveTheirPriceAndRarelyMuchBelowIt(String budget, String price, double expected) {
        assertThat(calculator.priceFromTutorView(new BigDecimal(budget), new BigDecimal(price)))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void fitIsTheMeanOfLevelScheduleAndPriceWhenKnownAndThePlatformRateOtherwise() {
        QueryContext context = context().maxPrice("153000").level("trung_binh")
                .availabilities(List.of(new TimeSlot(2, LocalTime.of(19, 0), LocalTime.of(21, 0)))).build();
        TeachingCandidate candidate = candidate().price("180000").mode(TeachingMode.ONLINE)
                .segments(Set.of("kha"))
                .availabilities(List.of(new TimeSlot(2, LocalTime.of(19, 0), LocalTime.of(21, 0)))).build();
        FeatureVector known = features.calculate(context, candidate);
        TeachingCandidate bare = candidate().build();

        // level adjacent 0.5, schedule 1.0, price from the tutor's side 0.5
        assertThat(calculator.fit(context, candidate, known, P0)).isCloseTo((0.5 + 1.0 + 0.5) / 3, within(1e-9));
        assertThat(calculator.fit(context().build(), bare, features.calculate(context().build(), bare), P0))
                .isEqualTo(P0);
    }

    // ---- b

    @Test
    void willingnessCombinesHistoryAndFitThenAppliesCapacity() {
        QueryContext context = context().build();
        TeachingCandidate candidate = candidate().capacity(1, 3).build();

        Willingness willingness = calculator.calculate(context, candidate, features.calculate(context, candidate), P0);

        assertThat(willingness.history()).isCloseTo(P0, within(1e-9));
        assertThat(willingness.fit()).isCloseTo(P0, within(1e-9));
        assertThat(willingness.capacity()).isCloseTo(0.4, within(1e-9));
        assertThat(willingness.b()).isCloseTo((0.5 * P0 + 0.5 * P0) * 0.4, within(1e-9));
        assertThat(willingness.invited()).isFalse();
    }

    @Test
    void tutorWhoAlreadyInvitedTheStudentIsCertainToAccept() {
        QueryContext context = context().build();
        TeachingCandidate candidate = candidate().capacity(1, 9).invited(true).build();

        Willingness willingness = calculator.calculate(context, candidate, features.calculate(context, candidate), P0);

        assertThat(willingness.b()).isEqualTo(1.0);
        assertThat(willingness.invited()).isTrue();
    }

    @Test
    void congestionCanBeSwitchedOffForAblation() {
        ReflectionTestUtils.setField(settings, "congestionEnabled", false);
        QueryContext context = context().build();
        TeachingCandidate crowded = candidate().capacity(1, 9).build();

        Willingness willingness = calculator.calculate(context, crowded, features.calculate(context, crowded), P0);

        assertThat(willingness.capacity()).isEqualTo(1.0);
        assertThat(willingness.b()).isCloseTo(P0, within(1e-9));
    }

    private static PastDecision decision(Integer level, String price, LearningMode mode, boolean accepted) {
        return new PastDecision(level, price == null ? null : new BigDecimal(price), mode, accepted);
    }
}
