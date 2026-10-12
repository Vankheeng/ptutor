package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.candidate;
import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.entity.enums.TeachingMode;

class RankerTest {

    /** Semantic and area features perfect, nothing else known: 0.30 + 0.05 + (1 - 0.35) x 0.5. */
    private static final double A_SEM_AND_AREA_PERFECT = 0.675;

    private final RecommendationSettings settings = new RecommendationSettings();
    private final Ranker ranker = new Ranker(new FeatureCalculator(settings), settings);

    @Test
    void scoreIsSuitabilityTimesQualityTimesGate() {
        // a: sem = 1.0 (w 0.30) and area = 1.0 (w 0.05) are known, the other 0.65 counts as neutral 0.5
        ScoredCandidate scored = ranker.score(context().build(),
                candidate().cosine(0.80).mode(TeachingMode.ONLINE).tutorScore("82.00").build());

        assertThat(scored.a()).isCloseTo(A_SEM_AND_AREA_PERFECT, within(1e-9));
        assertThat(scored.q()).isCloseTo(0.82, within(1e-9));
        assertThat(scored.g()).isEqualTo(1.0);
        assertThat(scored.score()).isCloseTo(A_SEM_AND_AREA_PERFECT * (0.7 + 0.3 * 0.82), within(1e-9));
    }

    @Test
    void qualityOnlyShiftsTheScoreByAboutFifteenPercent() {
        QueryContext context = context().build();
        double best = ranker.score(context, online(0.80, "100.00")).score();
        double worst = ranker.score(context, online(0.80, "0.00")).score();
        double unknown = ranker.score(context, online(0.80, null)).score();

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

        ScoredCandidate scored = ranker.score(context, clashing);

        // a = 0.30 * 1 + 0.10 * 0 + 0.05 * 1 + (1 - 0.45) * 0.5
        assertThat(scored.g()).isEqualTo(0.4);
        assertThat(scored.score()).isCloseTo((0.35 + 0.55 * 0.5) * 1.0 * 0.4, within(1e-9));
    }

    @Test
    void recentRejectionHalvesTheScoreAndCombinesWithTheScheduleGate() {
        QueryContext context = context()
                .availabilities(List.of(new TimeSlot(2, LocalTime.of(8, 0), LocalTime.of(10, 0)))).build();

        ScoredCandidate rejected = ranker.score(context().build(),
                candidate().cosine(0.80).mode(TeachingMode.ONLINE).rejected(true).build());
        ScoredCandidate both = ranker.score(context, candidate().cosine(0.80).mode(TeachingMode.ONLINE)
                .rejected(true)
                .availabilities(List.of(new TimeSlot(3, LocalTime.of(8, 0), LocalTime.of(10, 0)))).build());

        assertThat(rejected.g()).isEqualTo(0.5);
        assertThat(both.g()).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void ranksBestScoreFirst() {
        TeachingCandidate strong = online(0.80, "90.00");
        TeachingCandidate medium = online(0.60, "90.00");
        TeachingCandidate weak = online(0.40, "90.00");

        List<ScoredCandidate> ranked = ranker.rank(context().build(), List.of(weak, strong, medium));

        assertThat(ranked).extracting(ScoredCandidate::candidate).containsExactly(strong, medium, weak);
        assertThat(ranked).extracting(ScoredCandidate::score).isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    void aGoodSemanticMatchCannotCompensateForARejectionGate() {
        TeachingCandidate greatButRejected = candidate().cosine(0.80).mode(TeachingMode.ONLINE)
                .tutorScore("90.00").rejected(true).build();
        TeachingCandidate decent = online(0.62, "90.00");

        List<ScoredCandidate> ranked = ranker.rank(context().build(), List.of(greatButRejected, decent));

        assertThat(ranked.get(0).candidate()).isSameAs(decent);
    }

    private TeachingCandidate online(double cosine, String tutorScore) {
        return candidate().cosine(cosine).mode(TeachingMode.ONLINE).tutorScore(tutorScore).build();
    }
}
