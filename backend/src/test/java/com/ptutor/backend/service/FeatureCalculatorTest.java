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

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.entity.enums.TeachingMode;

class FeatureCalculatorTest {

    private static final UUID HOME_DISTRICT = UUID.randomUUID();
    private static final UUID HOME_PROVINCE = UUID.randomUUID();
    private static final UUID OTHER_DISTRICT_SAME_PROVINCE = UUID.randomUUID();
    private static final UUID OTHER_PROVINCE = UUID.randomUUID();

    private final FeatureCalculator calculator = new FeatureCalculator(new RecommendationSettings());

    // ---- f_sem

    @ParameterizedTest
    @CsvSource({ "0.30, 0.0", "0.10, 0.0", "0.55, 0.5", "0.80, 1.0", "0.95, 1.0" })
    void semanticMapsCosineBetweenFixedThresholds(double cosine, double expected) {
        assertThat(calculator.semantic(cosine)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void semanticIsMissingWithoutCosine() {
        assertThat(calculator.semantic(null)).isNull();
    }

    // ---- f_need

    @Test
    void needWeighsExactAndParentMatchesByConfidence() {
        UUID geometry = UUID.randomUUID();
        UUID planeGeometry = UUID.randomUUID();
        UUID algebra = UUID.randomUUID();
        UUID examGoal = UUID.randomUUID();
        List<WeightedTag> need = List.of(
                new WeightedTag(examGoal, "thi_vao_10", null, 1.0),          // exact match -> 1.0
                new WeightedTag(planeGeometry, "hinh_hoc_phang", geometry, 1.0), // tutor has the parent -> 0.6
                new WeightedTag(algebra, "dai_so", null, 0.5));              // not covered -> 0
        Set<CandidateTag> strengths = Set.of(new CandidateTag(examGoal, null), new CandidateTag(geometry, null));

        assertThat(calculator.need(need, strengths)).isCloseTo((1.0 + 0.6 + 0) / 2.5, within(1e-9));
    }

    @Test
    void needCountsAStudentParentTagCoveredByATutorChildTag() {
        UUID geometry = UUID.randomUUID();
        UUID planeGeometry = UUID.randomUUID();

        assertThat(calculator.need(
                List.of(new WeightedTag(geometry, "hinh_hoc", null, 1.0)),
                Set.of(new CandidateTag(planeGeometry, geometry))))
                .isCloseTo(0.6, within(1e-9));
    }

    @Test
    void needIsMissingWithoutStudentTagsAndNeutralWithoutTutorTags() {
        assertThat(calculator.need(List.of(), Set.of(new CandidateTag(UUID.randomUUID(), null)))).isNull();
        assertThat(calculator.need(List.of(new WeightedTag(UUID.randomUUID(), "x", null, 1.0)), Set.of()))
                .isEqualTo(0.4);
    }

    // ---- f_style

    @Test
    void styleIsTheShareOfWantedStylesTheTutorOffers() {
        List<WeightedTag> wanted = List.of(tag("kien_nhan"), tag("ky_tinh"), tag("vui_ve"));

        assertThat(calculator.style(wanted, Set.of("kien_nhan", "vui_ve", "nghiem_khac")))
                .isCloseTo(2 / 3.0, within(1e-9));
        assertThat(calculator.style(wanted, Set.of())).isEqualTo(0.5);
        assertThat(calculator.style(List.of(), Set.of("kien_nhan"))).isNull();
    }

    // ---- f_segment

    @ParameterizedTest
    @CsvSource({
        "trung_binh, trung_binh, 1.0",
        "trung_binh, kha, 0.5",
        "mat_goc, gioi, 0.1",
        "can_kem_cap, mat_goc, 1.0",
        "kha, 'mat_goc,gioi', 0.5"
    })
    void segmentComparesStudentLevelWithTutorTargets(String level, String targets, double expected) {
        assertThat(calculator.segment(level, Set.of(targets.split(",")))).isEqualTo(expected);
    }

    @Test
    void segmentIsMissingWithoutStudentLevelAndNeutralWithoutTargets() {
        assertThat(calculator.segment(null, Set.of("kha"))).isNull();
        assertThat(calculator.segment("khong_ro", Set.of("kha"))).isNull();
        assertThat(calculator.segment("kha", Set.of())).isEqualTo(0.5);
    }

    // ---- f_price

    @ParameterizedTest
    @CsvSource({ "150000, 1.0", "200000, 1.0", "250000, 0.5", "300000, 0.0", "400000, 0.0" })
    void priceIsFullWithinBudgetThenDropsLinearlyToZeroAtOneAndAHalfTimes(String price, double expected) {
        assertThat(calculator.price(new BigDecimal(price), new BigDecimal("200000")))
                .isCloseTo(expected, within(1e-9));
    }

    @Test
    void priceIsMissingWhenEitherSideIsUnknown() {
        assertThat(calculator.price(null, new BigDecimal("200000"))).isNull();
        assertThat(calculator.price(new BigDecimal("200000"), null)).isNull();
    }

    // ---- f_sched

    @Test
    void scheduleOverlapIsCountedPerDayInMinutes() {
        List<TimeSlot> student = List.of(slot(2, "19:00", "21:00"), slot(4, "19:00", "21:00"));
        List<TimeSlot> tutor = List.of(slot(2, "20:00", "22:00"), slot(4, "18:00", "19:30"), slot(6, "19:00", "21:00"));

        assertThat(calculator.overlapMinutes(student, tutor)).isEqualTo(60 + 30);
    }

    @Test
    void scheduleDoesNotDoubleCountOverlappingSlotsOfTheSamePerson() {
        List<TimeSlot> student = List.of(slot(1, "08:00", "10:00"), slot(1, "09:00", "11:00"));

        assertThat(calculator.overlapMinutes(student, List.of(slot(1, "08:00", "11:00")))).isEqualTo(180);
    }

    @Test
    void scheduleFeatureIsOverlapOverNeedCappedAtThreeHours() {
        // The student is free 4 h a week, so the need is capped at 180 min; 90 min overlap -> 0.5.
        FeatureVector half = calculator.calculate(
                context().availabilities(List.of(slot(2, "19:00", "21:00"), slot(4, "19:00", "21:00"))).build(),
                candidate().availabilities(List.of(slot(2, "20:00", "22:00"), slot(4, "18:00", "19:30"))).build());
        // Free only 1 h a week and that hour is covered -> 1.0.
        FeatureVector full = calculator.calculate(
                context().availabilities(List.of(slot(2, "19:00", "20:00"))).build(),
                candidate().availabilities(List.of(slot(2, "18:00", "22:00"))).build());

        assertThat(half.sched()).isCloseTo(0.5, within(1e-9));
        assertThat(half.noScheduleOverlap()).isFalse();
        assertThat(full.sched()).isEqualTo(1.0);
    }

    @Test
    void scheduleWithoutAnyOverlapScoresZeroAndRaisesTheGateFlag() {
        FeatureVector features = calculator.calculate(
                context().availabilities(List.of(slot(2, "08:00", "10:00"))).build(),
                candidate().availabilities(List.of(slot(3, "08:00", "10:00"))).build());

        assertThat(features.sched()).isZero();
        assertThat(features.noScheduleOverlap()).isTrue();
    }

    @Test
    void scheduleIsMissingAndGateStaysOpenWhenEitherSideGaveNoSchedule() {
        FeatureVector features = calculator.calculate(
                context().availabilities(List.of(slot(2, "08:00", "10:00"))).build(), candidate().build());

        assertThat(features.sched()).isNull();
        assertThat(features.noScheduleOverlap()).isFalse();
    }

    // ---- f_area

    @Test
    void areaIsFullForOnlinePostings() {
        assertThat(calculator.area(context().build(), candidate().mode(TeachingMode.ONLINE).build())).isEqualTo(1.0);
    }

    @Test
    void areaPrefersChosenDistrictsThenSameProvinceThenElsewhere() {
        QueryContext anywhere = context().scope(AreaScope.ANY).build();

        assertThat(calculator.area(anywhere, offlineIn(HOME_DISTRICT, HOME_PROVINCE))).isEqualTo(1.0);
        assertThat(calculator.area(anywhere, offlineIn(OTHER_DISTRICT_SAME_PROVINCE, HOME_PROVINCE))).isEqualTo(0.6);
        assertThat(calculator.area(anywhere, offlineIn(UUID.randomUUID(), OTHER_PROVINCE))).isEqualTo(0.2);
    }

    @Test
    void areaUsesTheChosenDistrictsInsteadOfHomeWhenTheSearcherPickedSome() {
        QueryContext chosen = context().scope(AreaScope.DISTRICTS)
                .districts(List.of(OTHER_DISTRICT_SAME_PROVINCE)).build();

        assertThat(calculator.area(chosen, offlineIn(OTHER_DISTRICT_SAME_PROVINCE, HOME_PROVINCE))).isEqualTo(1.0);
        assertThat(calculator.area(chosen, offlineIn(HOME_DISTRICT, HOME_PROVINCE))).isEqualTo(0.6);
    }

    @Test
    void areaIsMissingWhenSearchIsUnlimitedAndHomeIsUnknown() {
        QueryContext noHome = context().scope(AreaScope.ANY).home(null, null).build();

        assertThat(calculator.area(noHome, offlineIn(HOME_DISTRICT, HOME_PROVINCE))).isNull();
    }

    // ---- a and the missing-data rule

    @Test
    void suitabilityCountsMissingFeaturesAsNeutral() {
        // sem = (0.55 - 0.30) / 0.50 = 0.5 (weight 0.30); price = 0.5 (weight 0.20); area = 1.0 (weight 0.05).
        FeatureVector features = calculator.calculate(
                context().maxPrice("200000").build(),
                candidate().cosine(0.55).price("250000").mode(TeachingMode.ONLINE).build());

        assertThat(features.need()).isNull();
        assertThat(features.style()).isNull();
        assertThat(features.segment()).isNull();
        assertThat(features.sched()).isNull();
        assertThat(features.confidence()).isCloseTo(0.55, within(1e-9));
        double meanOfKnown = (0.30 * 0.5 + 0.20 * 0.5 + 0.05 * 1.0) / 0.55;
        assertThat(features.suitability()).isCloseTo(0.55 * meanOfKnown + 0.45 * 0.5, within(1e-9));
    }

    @Test
    void suitabilityUsesAllSevenWeightsWhenEverythingIsKnown() {
        UUID goal = UUID.randomUUID();
        FeatureVector features = calculator.calculate(
                context().maxPrice("200000")
                        .availabilities(List.of(slot(2, "19:00", "21:00")))
                        .need(List.of(new WeightedTag(goal, "thi_vao_10", null, 1.0)))
                        .style(List.of(tag("kien_nhan"), tag("ky_tinh")))
                        .level("trung_binh").build(),
                candidate().cosine(0.80).price("180000").mode(TeachingMode.ONLINE)
                        .availabilities(List.of(slot(2, "20:00", "21:00")))
                        .strengths(Set.of(new CandidateTag(goal, null)))
                        .styles(Set.of("kien_nhan")).segments(Set.of("kha")).build());

        // sem 1.0, need 1.0, price 1.0, style 0.5, sched 60/120 = 0.5, area 1.0, segment 0.5
        double expected = 0.30 * 1.0 + 0.20 * 1.0 + 0.20 * 1.0 + 0.10 * 0.5 + 0.10 * 0.5 + 0.05 * 1.0 + 0.05 * 0.5;
        assertThat(features.confidence()).isCloseTo(1.0, within(1e-9));
        assertThat(features.suitability()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void suitabilityIsNeutralWithZeroConfidenceWhenNothingIsKnown() {
        FeatureVector features = calculator.calculate(
                context().scope(AreaScope.ANY).home(null, null).build(), candidate().build());

        assertThat(features.confidence()).isZero();
        assertThat(features.suitability()).isEqualTo(0.5);
    }

    @Test
    void aPostingKnownOnlyByOnePerfectFeatureDoesNotOutrankAWellKnownGoodOne() {
        // Online posting without embedding: only the area feature (1.0, weight 0.05) is known.
        FeatureVector barelyKnown = calculator.calculate(
                context().build(), candidate().mode(TeachingMode.ONLINE).build());
        FeatureVector wellKnown = calculator.calculate(
                context().maxPrice("200000").build(),
                candidate().cosine(0.70).price("190000").mode(TeachingMode.ONLINE).build());

        assertThat(barelyKnown.suitability()).isCloseTo(0.05 * 1.0 + 0.95 * 0.5, within(1e-9));
        assertThat(wellKnown.suitability()).isGreaterThan(barelyKnown.suitability());
    }

    // ---- helpers

    private static WeightedTag tag(String code) {
        return new WeightedTag(UUID.nameUUIDFromBytes(code.getBytes()), code, null, 1.0);
    }

    private static TimeSlot slot(int day, String start, String end) {
        return new TimeSlot(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static TeachingCandidate offlineIn(UUID districtId, UUID provinceId) {
        return candidate().districts(List.of(new District(districtId, "Quận", provinceId))).build();
    }

    static ContextBuilder context() {
        return new ContextBuilder();
    }

    static CandidateBuilder candidate() {
        return new CandidateBuilder();
    }

    static final class ContextBuilder {
        private AreaScope scope = AreaScope.ANY;
        private List<UUID> districts = List.of();
        private UUID homeDistrict = HOME_DISTRICT;
        private UUID homeProvince = HOME_PROVINCE;
        private BigDecimal maxPrice;
        private List<TimeSlot> availabilities = List.of();
        private List<WeightedTag> need = List.of();
        private List<WeightedTag> style = List.of();
        private String level;

        ContextBuilder scope(AreaScope value) {
            scope = value;
            return this;
        }

        ContextBuilder districts(List<UUID> value) {
            districts = value;
            return this;
        }

        ContextBuilder home(UUID district, UUID province) {
            homeDistrict = district;
            homeProvince = province;
            return this;
        }

        ContextBuilder maxPrice(String value) {
            maxPrice = new BigDecimal(value);
            return this;
        }

        ContextBuilder availabilities(List<TimeSlot> value) {
            availabilities = value;
            return this;
        }

        ContextBuilder need(List<WeightedTag> value) {
            need = value;
            return this;
        }

        ContextBuilder style(List<WeightedTag> value) {
            style = value;
            return this;
        }

        ContextBuilder level(String value) {
            level = value;
            return this;
        }

        QueryContext build() {
            return new QueryContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    ModePreference.ANY, scope, districts, null, homeDistrict, homeProvince, null, maxPrice,
                    availabilities, need, style, level, null, "", null, Map.of(), List.of());
        }
    }

    static final class CandidateBuilder {
        private Double cosine;
        private BigDecimal price;
        private BigDecimal tutorScore;
        private TeachingMode mode = TeachingMode.OFFLINE;
        private List<District> districts = List.of();
        private List<TimeSlot> availabilities = List.of();
        private Set<CandidateTag> strengths = Set.of();
        private Set<String> styles = Set.of();
        private Set<String> segments = Set.of();
        private boolean rejected;

        CandidateBuilder cosine(Double value) {
            cosine = value;
            return this;
        }

        CandidateBuilder price(String value) {
            price = new BigDecimal(value);
            return this;
        }

        CandidateBuilder tutorScore(String value) {
            tutorScore = value == null ? null : new BigDecimal(value);
            return this;
        }

        CandidateBuilder mode(TeachingMode value) {
            mode = value;
            return this;
        }

        CandidateBuilder districts(List<District> value) {
            districts = value;
            return this;
        }

        CandidateBuilder availabilities(List<TimeSlot> value) {
            availabilities = value;
            return this;
        }

        CandidateBuilder strengths(Set<CandidateTag> value) {
            strengths = value;
            return this;
        }

        CandidateBuilder styles(Set<String> value) {
            styles = value;
            return this;
        }

        CandidateBuilder segments(Set<String> value) {
            segments = value;
            return this;
        }

        CandidateBuilder rejected(boolean value) {
            rejected = value;
            return this;
        }

        TeachingCandidate build() {
            return new TeachingCandidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Tin dạy", price,
                    mode, null, cosine, tutorScore, "Gia sư", null, null, 0, districts, availabilities, strengths,
                    styles, segments, rejected);
        }
    }
}
