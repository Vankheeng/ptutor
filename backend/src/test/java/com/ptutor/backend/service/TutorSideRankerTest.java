package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate.PastProposal;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.enums.LearningMode;

class TutorSideRankerTest {

    private static final double P0 = 0.6;
    private static final UUID HOME_DISTRICT = UUID.randomUUID();
    private static final UUID HOME_PROVINCE = UUID.randomUUID();

    private final RecommendationSettings settings = new RecommendationSettings();
    private final TutorSideRanker ranker = new TutorSideRanker(new FeatureCalculator(settings), settings);

    // ---- a'

    @ParameterizedTest
    @CsvSource({ "200000, 180000, 1.0", "180000, 180000, 1.0", "153000, 180000, 0.5", "126000, 180000, 0.0" })
    void priceFromTheTutorsViewIsFullWhenTheBudgetCoversTheHopedPrice(String budget, String hoped, double expected) {
        assertThat(ranker.priceFromTutorView(new BigDecimal(budget), new BigDecimal(hoped)))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void suitabilityUsesTheSixTutorSideWeightsAndTreatsMissingOnesAsNeutral() {
        UUID geometry = UUID.randomUUID();
        Context tutor = new Context();
        tutor.expectedPrice = new BigDecimal("180000");
        tutor.strengths = Set.of(new CandidateTag(geometry, null));
        tutor.segments = Set.of("trung_binh");
        tutor.slots = List.of(slot(2, "19:00", "21:00"));
        Candidate student = new Candidate();
        student.cosine = 0.80;
        student.maxPrice = new BigDecimal("153000");
        student.need = List.of(new WeightedTag(geometry, "hinh_hoc", null, 1.0));
        student.level = "kha";
        student.slots = List.of(slot(2, "20:00", "21:00"));
        student.mode = LearningMode.ONLINE;

        FeatureVector all = ranker.features(tutor.build(), student.build());
        FeatureVector little = ranker.features(new Context().build(), new Candidate().build());

        // sem 1.0, price 0.5, need 1.0, segment 0.5 (adjacent), sched 60 / 120 = 0.5, area 1.0 (online)
        assertThat(all.confidence()).isCloseTo(1.0, within(1e-9));
        assertThat(all.suitability()).isCloseTo(
                0.30 * 1.0 + 0.25 * 0.5 + 0.20 * 1.0 + 0.10 * 0.5 + 0.10 * 0.5 + 0.05 * 1.0, within(1e-9));
        assertThat(all.style()).as("style belongs to the student's side").isNull();
        // Only the semantic feature (cos 0.55 -> 0.5) is known: 0.30 * 0.5 + 0.70 * 0.5
        assertThat(little.confidence()).isCloseTo(0.30, within(1e-9));
        assertThat(little.suitability()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void areaComparesWhereTheStudentWantsToLearnWithWhereTheTutorCanTeach() {
        Context tutor = new Context();
        Candidate atHome = new Candidate();
        atHome.district = new District(HOME_DISTRICT, "Nhà", HOME_PROVINCE);
        Candidate sameProvince = new Candidate();
        sameProvince.district = new District(UUID.randomUUID(), "Gần", HOME_PROVINCE);
        Candidate elsewhere = new Candidate();
        elsewhere.district = new District(UUID.randomUUID(), "Xa", UUID.randomUUID());
        Candidate online = new Candidate();
        online.mode = LearningMode.ONLINE;

        assertThat(ranker.features(tutor.build(), atHome.build()).area()).isEqualTo(1.0);
        assertThat(ranker.features(tutor.build(), sameProvince.build()).area()).isEqualTo(0.6);
        assertThat(ranker.features(tutor.build(), elsewhere.build()).area()).isEqualTo(0.2);
        assertThat(ranker.features(tutor.build(), online.build()).area()).isEqualTo(1.0);
        assertThat(ranker.features(tutor.build(), new Candidate().build()).area()).as("no district given").isNull();
    }

    // ---- b'

    @Test
    void fitIsWhatTheStudentSeesQualityStyleAndPrice() {
        Context tutor = new Context();
        tutor.quality = 0.8;
        tutor.styles = Set.of("kien_nhan");
        tutor.expectedPrice = new BigDecimal("180000");
        Candidate student = new Candidate();
        student.styles = Set.of("kien_nhan", "ky_tinh");
        student.maxPrice = new BigDecimal("200000");

        assertThat(ranker.fit(tutor.build(), student.build()))
                .isCloseTo(0.4 * 0.8 + 0.3 * 0.5 + 0.3 * 1.0, within(1e-9));
        // Nothing known about style or price: both count as neutral.
        Context plain = new Context();
        plain.quality = 0.8;
        assertThat(ranker.fit(plain.build(), new Candidate().build()))
                .isCloseTo(0.4 * 0.8 + 0.3 * 0.5 + 0.3 * 0.5, within(1e-9));
    }

    @ParameterizedTest
    @CsvSource({ "180000, 200000, 1.0", "250000, 200000, 0.5", "300000, 200000, 0.0" })
    void priceFromTheStudentsViewDropsAboveTheirBudget(String hoped, String budget, double expected) {
        assertThat(ranker.priceFromStudentView(new BigDecimal(hoped), new BigDecimal(budget)))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void historyWeighsTheStudentsPastDecisionsByHowSimilarTheTutorWas() {
        Context tutor = new Context();
        tutor.quality = 0.8;
        tutor.expectedPrice = new BigDecimal("200000");
        Candidate student = new Candidate();
        student.decisions = List.of(
                // same price, same quality -> k = 1.0, accepted
                new PastProposal(new BigDecimal("200000"), 0.8, true),
                // price 250k (0.8), quality 0.4 (0.6) -> k = 0.7, rejected
                new PastProposal(new BigDecimal("250000"), 0.4, false),
                // unknown price and quality -> k = 0.5, accepted
                new PastProposal(null, null, true));

        assertThat(ranker.history(tutor.build(), student.build(), P0))
                .isCloseTo((1.0 + 0.5 + 2 * P0) / (1.0 + 0.7 + 0.5 + 2), within(1e-9));
        assertThat(ranker.history(tutor.build(), new Candidate().build(), P0)).isCloseTo(P0, within(1e-9));
    }

    @Test
    void willingnessAppliesCongestionAndIsCertainWhenTheStudentAlreadyApplied() {
        Context tutor = new Context();
        tutor.quality = 0.5;
        Candidate crowded = new Candidate();
        crowded.remaining = 1;
        crowded.pending = 2;
        Candidate applied = new Candidate();
        applied.pending = 9;
        applied.applied = true;

        Willingness busy = ranker.willingness(tutor.build(), crowded.build(), P0);
        Willingness sure = ranker.willingness(tutor.build(), applied.build(), P0);

        // fit = 0.4 * 0.5 + 0.3 * 0.5 + 0.3 * 0.5 = 0.5; history = p0
        assertThat(busy.capacity()).isCloseTo(0.5, within(1e-9));
        assertThat(busy.b()).isCloseTo((0.5 * P0 + 0.5 * 0.5) * 0.5, within(1e-9));
        assertThat(sure.b()).isEqualTo(1.0);
        assertThat(sure.invited()).isTrue();
    }

    // ---- score'

    @Test
    void scoreIsTheHarmonicMeanTimesTheGateWithoutAnyQualityFactorForStudents() {
        Context tutor = new Context();
        tutor.slots = List.of(slot(2, "08:00", "10:00"));
        Candidate student = new Candidate();
        student.cosine = 0.80;
        student.slots = List.of(slot(3, "08:00", "10:00"));
        student.rejectedTutor = true;

        ScoredStudyingCandidate scored = ranker.score(tutor.build(), student.build(), P0);

        // a' = 0.30 * 1 + 0.10 * 0 + 0.60 * 0.5 = 0.6; b' = 0.5 * 0.6 + 0.5 * 0.5 = 0.55; G' = 0.4 * 0.5
        assertThat(scored.a()).isCloseTo(0.6, within(1e-9));
        assertThat(scored.willingness().b()).isCloseTo(0.55, within(1e-9));
        assertThat(scored.r()).isCloseTo(2 * 0.6 * 0.55 / 1.15, within(1e-9));
        assertThat(scored.g()).isCloseTo(0.2, within(1e-9));
        assertThat(scored.score()).isCloseTo(scored.r() * 0.2, within(1e-9));
    }

    @Test
    void ranksBestFirstAndCanRunOneSided() {
        Candidate strong = new Candidate();
        strong.cosine = 0.80;
        strong.pending = 8;
        Candidate medium = new Candidate();
        medium.cosine = 0.65;
        StudyingCandidate crowdedStrong = strong.build();
        StudyingCandidate freeMedium = medium.build();

        List<ScoredStudyingCandidate> reciprocal = ranker.rank(new Context().build(),
                List.of(crowdedStrong, freeMedium), P0);
        ReflectionTestUtils.setField(settings, "reciprocalEnabled", false);
        List<ScoredStudyingCandidate> oneSided = ranker.rank(new Context().build(),
                List.of(crowdedStrong, freeMedium), P0);

        assertThat(reciprocal.get(0).candidate()).isSameAs(freeMedium);
        assertThat(oneSided.get(0).candidate()).isSameAs(crowdedStrong);
        assertThat(oneSided.get(0).willingness()).isNull();
    }

    // ---- helpers

    private static TimeSlot slot(int day, String start, String end) {
        return new TimeSlot(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static final class Context {
        private BigDecimal expectedPrice;
        private List<TimeSlot> slots = List.of();
        private Set<CandidateTag> strengths = Set.of();
        private Set<String> styles = Set.of();
        private Set<String> segments = Set.of();
        private double quality = 0.5;

        SupplyContext build() {
            return new SupplyContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    List.of(UUID.randomUUID()), ModePreference.ANY, AreaScope.ANY, List.of(), null, HOME_DISTRICT,
                    HOME_PROVINCE, expectedPrice, slots, strengths, styles, segments, quality, "", null, Map.of(),
                    List.of());
        }
    }

    private static final class Candidate {
        private Double cosine = 0.55;
        private BigDecimal maxPrice;
        private LearningMode mode = LearningMode.OFFLINE;
        private District district;
        private List<TimeSlot> slots = List.of();
        private List<WeightedTag> need = List.of();
        private Set<String> styles = Set.of();
        private String level;
        private boolean rejectedTutor;
        private int remaining = 1;
        private int pending;
        private List<PastProposal> decisions = List.of();
        private boolean applied;

        StudyingCandidate build() {
            return new StudyingCandidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Tin học", null,
                    null, maxPrice, mode, "Lớp 9", district, null, cosine, slots, need, styles, level, rejectedTutor,
                    remaining, pending, decisions, applied);
        }
    }
}
