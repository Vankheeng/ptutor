package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.candidate;
import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.entity.enums.TeachingMode;

class RankerTest {

    /** Semantic and area features perfect, nothing else known: 0.30 + 0.05 + (1 - 0.35) x 0.5. */
    private static final double A_SEM_AND_AREA_PERFECT = 0.675;

    private static final double P0 = 0.6;

    private final RecommendationSettings settings = new RecommendationSettings();
    private final Ranker ranker = new Ranker(
            new FeatureCalculator(settings, new WeightProvider(settings)), new WillingnessCalculator(settings, new WeightProvider(settings)), settings);

    /** The tests of the one-sided score (V2-a) switch the reciprocal part off. */
    @BeforeEach
    void oneSidedByDefault() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", false);
    }

    @Test
    void scoreIsSuitabilityTimesQualityTimesGate() {
        // a: sem = 1.0 (w 0.30) and area = 1.0 (w 0.05) are known, the other 0.65 counts as neutral 0.5
        ScoredCandidate scored = ranker.score(context().build(),
                candidate().cosine(0.80).mode(TeachingMode.ONLINE).tutorScore("82.00").build(), P0);

        assertThat(scored.a()).isCloseTo(A_SEM_AND_AREA_PERFECT, within(1e-9));
        assertThat(scored.q()).isCloseTo(0.82, within(1e-9));
        assertThat(scored.g()).isEqualTo(1.0);
        assertThat(scored.score()).isCloseTo(A_SEM_AND_AREA_PERFECT * (0.7 + 0.3 * 0.82), within(1e-9));
    }

    @Test
    void qualityOnlyShiftsTheScoreByAboutFifteenPercent() {
        QueryContext context = context().build();
        double best = ranker.score(context, online(0.80, "100.00"), P0).score();
        double worst = ranker.score(context, online(0.80, "0.00"), P0).score();
        double unknown = ranker.score(context, online(0.80, null), P0).score();

        assertThat(best).isCloseTo(A_SEM_AND_AREA_PERFECT * 1.0, within(1e-9));
        assertThat(worst).isCloseTo(A_SEM_AND_AREA_PERFECT * 0.7, within(1e-9));
        assertThat(unknown).isCloseTo(A_SEM_AND_AREA_PERFECT * 0.85, within(1e-9));
    }

    @Test
    void scheduleThatNeverOverlapsMultipliesTheScoreByTheGate() {
        QueryContext context = context()
                .availabilities(List.of(new TimeSlot(2, LocalTime.of(8, 0), LocalTime.of(10, 0)))).build();
        TeachingCandidate clashing = candidate().cosine(0.80).mode(TeachingMode.ONLINE).tutorScore("100.00")
                .availabilities(List.of(new TimeSlot(3, LocalTime.of(8, 0), LocalTime.of(10, 0)))).build();

        ScoredCandidate scored = ranker.score(context, clashing, P0);

        // a = 0.30 * 1 + 0.10 * 0 + 0.05 * 1 + (1 - 0.45) * 0.5
        assertThat(scored.g()).isEqualTo(0.4);
        assertThat(scored.score()).isCloseTo((0.35 + 0.55 * 0.5) * 1.0 * 0.4, within(1e-9));
    }

    @Test
    void recentRejectionHalvesTheScoreAndCombinesWithTheScheduleGate() {
        QueryContext context = context()
                .availabilities(List.of(new TimeSlot(2, LocalTime.of(8, 0), LocalTime.of(10, 0)))).build();

        ScoredCandidate rejected = ranker.score(context().build(),
                candidate().cosine(0.80).mode(TeachingMode.ONLINE).rejected(true).build(), P0);
        ScoredCandidate both = ranker.score(context, candidate().cosine(0.80).mode(TeachingMode.ONLINE)
                .rejected(true)
                .availabilities(List.of(new TimeSlot(3, LocalTime.of(8, 0), LocalTime.of(10, 0)))).build(), P0);

        assertThat(rejected.g()).isEqualTo(0.5);
        assertThat(both.g()).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void ranksBestScoreFirst() {
        TeachingCandidate strong = online(0.80, "90.00");
        TeachingCandidate medium = online(0.60, "90.00");
        TeachingCandidate weak = online(0.40, "90.00");

        List<ScoredCandidate> ranked = ranker.rank(context().build(), List.of(weak, strong, medium), P0);

        assertThat(ranked).extracting(ScoredCandidate::candidate).containsExactly(strong, medium, weak);
        assertThat(ranked).extracting(ScoredCandidate::score).isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    void aGoodSemanticMatchCannotCompensateForARejectionGate() {
        TeachingCandidate greatButRejected = candidate().cosine(0.80).mode(TeachingMode.ONLINE)
                .tutorScore("90.00").rejected(true).build();
        TeachingCandidate decent = online(0.62, "90.00");

        List<ScoredCandidate> ranked = ranker.rank(context().build(), List.of(greatButRejected, decent), P0);

        assertThat(ranked.get(0).candidate()).isSameAs(decent);
    }

    // ---- reciprocal (V2-R / V2-RC)

    @ParameterizedTest
    @CsvSource({ "0.9, 0.2, 0.3273", "0.5, 0.5, 0.5", "0.871, 0.341, 0.4901", "0.75, 0.80, 0.7742", "0, 0, 0",
        "1, 0, 0" })
    void reciprocalIsTheHarmonicMeanPulledTowardsTheSmallerSide(double a, double b, double expected) {
        assertThat(Ranker.reciprocal(a, b)).isCloseTo(expected, within(1e-4));
    }

    @Test
    void reciprocalScoreUsesBothSides() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);

        ScoredCandidate scored = ranker.score(context().build(),
                candidate().cosine(0.80).mode(TeachingMode.ONLINE).tutorScore("82.00").capacity(1, 3).build(), P0);

        // b = (0.5 * 0.6 + 0.5 * 0.6) * 1 / (1 + 0.5 * 3) = 0.24
        assertThat(scored.willingness().b()).isCloseTo(0.24, within(1e-9));
        assertThat(scored.r()).isCloseTo(2 * A_SEM_AND_AREA_PERFECT * 0.24 / (A_SEM_AND_AREA_PERFECT + 0.24),
                within(1e-9));
        assertThat(scored.score()).isCloseTo(scored.r() * (0.7 + 0.3 * 0.82), within(1e-9));
    }

    @Test
    void aCrowdedPostingFallsBelowAnEquallySuitableOneThatIsFree() {
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", true);
        TeachingCandidate crowded = candidate().cosine(0.80).mode(TeachingMode.ONLINE).tutorScore("90.00")
                .capacity(1, 6).build();
        TeachingCandidate free = candidate().cosine(0.70).mode(TeachingMode.ONLINE).tutorScore("90.00")
                .capacity(1, 0).build();

        List<ScoredCandidate> reciprocal = ranker.rank(context().build(), List.of(crowded, free), P0);
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", false);
        List<ScoredCandidate> oneSided = ranker.rank(context().build(), List.of(crowded, free), P0);

        assertThat(reciprocal.get(0).candidate()).isSameAs(free);
        assertThat(oneSided.get(0).candidate()).isSameAs(crowded);
        assertThat(oneSided.get(0).willingness()).isNull();
        assertThat(oneSided.get(0).r()).isEqualTo(oneSided.get(0).a());
    }

    @Test
    void rerankScoresTheTopGroupAgainAndLeavesEverythingBelowInPlace() {
        ReflectionTestUtils.setField(settings, "rerankTopK", 2);
        QueryContext context = context().build();
        TeachingCandidate first = online(0.80, "70.00");
        TeachingCandidate second = online(0.70, "70.00");
        TeachingCandidate third = online(0.60, "70.00");
        List<ScoredCandidate> ranked = ranker.rank(context, List.of(third, first, second), P0);
        assertThat(ranked).extracting(ScoredCandidate::candidate).containsExactly(first, second, third);

        List<ScoredCandidate> reranked = ranker.applyRerank(context, ranked, Map.of(
                first.teachingRequestId(), 0.0, second.teachingRequestId(), 1.0, third.teachingRequestId(), 1.0),
                P0);

        assertThat(reranked).extracting(ScoredCandidate::candidate).containsExactly(second, first, third);
        // second: s = (0.70 - 0.30) / 0.50 = 0.8, f_sem = 0.3 * 0.8 + 0.7 * 1.0
        assertThat(reranked.get(0).features().sem()).isCloseTo(0.94, within(1e-9));
        assertThat(reranked.get(0).features().rerank()).isEqualTo(1.0);
        assertThat(reranked.get(0).score()).isGreaterThan(ranked.get(1).score());
        // first: s = 1.0, f_sem = 0.3 * 1.0 + 0.7 * 0.0
        assertThat(reranked.get(1).features().sem()).isCloseTo(0.30, within(1e-9));
        assertThat(reranked.get(2)).as("below the top group nothing changes, score or no score")
                .isSameAs(ranked.get(2));
        assertThat(ranked.get(0).features().rerank()).isNull();
    }

    @Test
    void rerankWithoutScoresKeepsTheRankingAndPostingsWithoutAScoreKeepTheirs() {
        QueryContext context = context().build();
        TeachingCandidate first = online(0.80, "70.00");
        TeachingCandidate second = online(0.70, "70.00");
        List<ScoredCandidate> ranked = ranker.rank(context, List.of(first, second), P0);

        assertThat(ranker.applyRerank(context, ranked, Map.of(), P0)).isSameAs(ranked);

        List<ScoredCandidate> partly = ranker.applyRerank(
                context, ranked, Map.of(second.teachingRequestId(), 1.0), P0);

        assertThat(partly).extracting(ScoredCandidate::candidate).containsExactly(first, second);
        assertThat(partly.get(0)).isSameAs(ranked.get(0));
        assertThat(partly.get(1).features().sem()).isCloseTo(0.94, within(1e-9));
    }

    private TeachingCandidate online(double cosine, String tutorScore) {
        return candidate().cosine(cosine).mode(TeachingMode.ONLINE).tutorScore(tutorScore).build();
    }
}
