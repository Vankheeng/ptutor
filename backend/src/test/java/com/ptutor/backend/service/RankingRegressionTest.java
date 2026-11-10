package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate.PastProposal;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.PastDecision;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.enums.CongestionFormula;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.ReciprocalMode;
import com.ptutor.backend.entity.enums.TeachingMode;

/**
 * Scores of a fixed, seeded set of pairs in both directions, compared with the scores the ranking produced
 * before the weights moved behind {@code WeightProvider} (design 12, section 12.2). With the prior weights the
 * results must not change. The expected values were written by the code before that change: run with
 * {@code -Dranking.golden.write=true} only when a change of the scores is intended.
 */
class RankingRegressionTest {

    private static final String GOLDEN = "recommendation/ranking-golden.csv";
    private static final int CASES = 300;
    private static final double P0 = 0.6;
    private static final double TOLERANCE = 1e-9;
    private static final String[] LEVELS = { "mat_goc", "can_kem_cap", "trung_binh", "kha", "gioi" };
    private static final String[] STYLES = { "kien_nhan", "nghiem_khac", "vui_ve", "tu_duy" };

    private final RecommendationSettings settings = oldFormulas();
    private final FeatureCalculator featureCalculator = new FeatureCalculator(settings, new WeightProvider(settings));
    private final Ranker ranker = new Ranker(featureCalculator, new WillingnessCalculator(settings, new WeightProvider(settings)), settings);
    private final TutorSideRanker tutorSideRanker = new TutorSideRanker(featureCalculator, settings, new WeightProvider(settings));

    @Test
    void scoresWithThePriorWeightsAreTheScoresOfBefore() throws IOException {
        List<String> actual = computeAll();
        if (Boolean.getBoolean("ranking.golden.write")) {
            Path file = Path.of("src/test/resources", GOLDEN);
            Files.createDirectories(file.getParent());
            Files.write(file, actual, StandardCharsets.UTF_8);
        }

        List<String> expected = readGolden();
        assertThat(actual).hasSameSizeAs(expected);
        for (int line = 0; line < expected.size(); line++) {
            String[] want = expected.get(line).split(",");
            String[] got = actual.get(line).split(",");
            assertThat(got[0]).as("line %d", line).isEqualTo(want[0]);
            for (int column = 1; column < want.length; column++) {
                double wanted = Double.parseDouble(want[column]);
                double value = Double.parseDouble(got[column]);
                if (Double.isNaN(wanted)) {
                    assertThat(value).as("line %d column %d", line, column).isNaN();
                } else {
                    assertThat(value).as("line %d column %d", line, column).isCloseTo(wanted, within(TOLERANCE));
                }
            }
        }
    }

    private List<String> computeAll() {
        Random random = new Random(20261020L);
        Fixtures fixtures = new Fixtures(random);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < CASES; i++) {
            Double rerank = random.nextInt(3) == 0 ? random.nextDouble() : null;
            Double hist = random.nextInt(3) == 0 ? random.nextDouble() : null;
            double histWeight = random.nextDouble() * 0.15;
            ScoredCandidate scored = ranker.score(fixtures.queryContext(), fixtures.teachingCandidate(), P0,
                    rerank, hist, histWeight);
            lines.add(line("S2T-" + i, scored.a(), scored.willingness(), scored.r(), scored.score(),
                    scored.features().confidence()));
        }
        for (int i = 0; i < CASES; i++) {
            Double rerank = random.nextInt(3) == 0 ? random.nextDouble() : null;
            Double hist = random.nextInt(3) == 0 ? random.nextDouble() : null;
            double histWeight = random.nextDouble() * 0.15;
            ScoredStudyingCandidate scored = tutorSideRanker.score(fixtures.supplyContext(),
                    fixtures.studyingCandidate(), P0, rerank, hist, histWeight);
            lines.add(line("T2S-" + i, scored.a(), scored.willingness(), scored.r(), scored.score(),
                    scored.features().confidence()));
        }
        return lines;
    }

    private static String line(String key, double a, Willingness w, double r, double score, double confidence) {
        return String.join(",", key, Double.toString(a), Double.toString(w.b()), Double.toString(w.history()),
                Double.toString(w.fit()), Double.toString(w.capacity()), Double.toString(r), Double.toString(score),
                Double.toString(confidence));
    }

    private static List<String> readGolden() throws IOException {
        try (InputStream in = RankingRegressionTest.class.getClassLoader().getResourceAsStream(GOLDEN)) {
            assertThat(in).as("golden file %s", GOLDEN).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }

    /** Random inputs that cover present and missing values of every feature. */
    private static final class Fixtures {

        private final Random random;
        private final List<CandidateTag> tags = new ArrayList<>();
        private final List<District> districts = new ArrayList<>();
        private final UUID[] provinces;

        Fixtures(Random random) {
            this.random = random;
            provinces = new UUID[] { uuid(), uuid() };
            for (int i = 0; i < 4; i++) {
                districts.add(new District(uuid(), "Quận " + i, provinces[i % 2]));
            }
            UUID parent = uuid();
            tags.add(new CandidateTag(parent, null));
            for (int i = 0; i < 5; i++) {
                tags.add(new CandidateTag(uuid(), i < 3 ? parent : null));
            }
        }

        QueryContext queryContext() {
            AreaScope scope = pick(AreaScope.values());
            return new QueryContext(uuid(), uuid(), uuid(), uuid(), maybe(() -> 1 + random.nextInt(12)),
                    pick(ModePreference.values()), scope,
                    scope == AreaScope.DISTRICTS ? districtIds() : List.of(),
                    scope == AreaScope.PROVINCE ? pick(provinces) : null,
                    maybe(() -> pick(districts).id()), maybe(() -> pick(provinces)), null, maybe(this::price),
                    slots(), weightedTags(), styleTags(), maybe(() -> pick(LEVELS)), null, "", null, Map.of(),
                    List.of());
        }

        TeachingCandidate teachingCandidate() {
            List<PastDecision> decisions = new ArrayList<>();
            for (int i = random.nextInt(7); i > 0; i--) {
                decisions.add(new PastDecision(maybe(() -> 1 + random.nextInt(12)), maybe(this::price),
                        maybe(() -> pick(LearningMode.values())), random.nextBoolean()));
            }
            return new TeachingCandidate(uuid(), uuid(), uuid(), "Tin dạy", maybe(this::price),
                    pick(TeachingMode.values()), LocalDateTime.of(2026, 10, 1, 8, 0),
                    maybe(random::nextDouble), maybe(() -> BigDecimal.valueOf(random.nextInt(101))), "Gia sư", null,
                    null, null, subset(districts), slots(), new HashSet<>(subset(tags)), new HashSet<>(styles()),
                    new HashSet<>(levels()), random.nextInt(5) == 0, random.nextInt(4), random.nextInt(6),
                    decisions, random.nextInt(10) == 0);
        }

        SupplyContext supplyContext() {
            AreaScope scope = pick(AreaScope.values());
            return new SupplyContext(uuid(), uuid(), uuid(), List.of(uuid()), pick(ModePreference.values()), scope,
                    scope == AreaScope.DISTRICTS ? districtIds() : List.of(),
                    scope == AreaScope.PROVINCE ? pick(provinces) : null,
                    maybe(() -> pick(districts).id()), maybe(() -> pick(provinces)), maybe(this::price), slots(),
                    new HashSet<>(subset(tags)), new HashSet<>(styles()), new HashSet<>(levels()),
                    random.nextDouble(), "", null, Map.of(), List.of());
        }

        StudyingCandidate studyingCandidate() {
            List<PastProposal> decisions = new ArrayList<>();
            for (int i = random.nextInt(7); i > 0; i--) {
                decisions.add(new PastProposal(maybe(this::price), maybe(random::nextDouble), random.nextBoolean()));
            }
            return new StudyingCandidate(uuid(), uuid(), uuid(), "Tin học", null, null, maybe(this::price),
                    pick(LearningMode.values()), "Lớp 9", maybe(() -> pick(districts)),
                    LocalDateTime.of(2026, 10, 1, 8, 0), maybe(random::nextDouble), slots(), weightedTags(),
                    new HashSet<>(styles()), maybe(() -> pick(LEVELS)), random.nextInt(5) == 0, 1,
                    random.nextInt(6), decisions, random.nextInt(10) == 0);
        }

        private List<UUID> districtIds() {
            List<UUID> ids = subset(districts).stream().map(District::id).toList();
            return ids.isEmpty() ? List.of(districts.get(0).id()) : ids;
        }

        private List<WeightedTag> weightedTags() {
            return subset(tags).stream()
                    .map(tag -> new WeightedTag(tag.tagId(), "tag-" + tag.tagId(), tag.parentId(),
                            0.5 + random.nextDouble() / 2))
                    .toList();
        }

        private List<WeightedTag> styleTags() {
            return styles().stream().map(code -> new WeightedTag(null, code, null, 1.0)).toList();
        }

        private List<String> styles() {
            return subset(List.of(STYLES));
        }

        private List<String> levels() {
            return subset(List.of(LEVELS));
        }

        private List<TimeSlot> slots() {
            List<TimeSlot> slots = new ArrayList<>();
            for (int i = random.nextInt(4); i > 0; i--) {
                int start = 7 + random.nextInt(13);
                slots.add(new TimeSlot(1 + random.nextInt(7), LocalTime.of(start, 0),
                        LocalTime.of(start + 1 + random.nextInt(3), random.nextBoolean() ? 30 : 0)));
            }
            return slots;
        }

        private BigDecimal price() {
            return BigDecimal.valueOf(50_000L + 10_000L * random.nextInt(36));
        }

        private <T> List<T> subset(List<T> values) {
            List<T> picked = new ArrayList<>();
            for (T value : values) {
                if (random.nextInt(3) == 0) {
                    picked.add(value);
                }
            }
            return picked;
        }

        private <T> T maybe(java.util.function.Supplier<T> value) {
            return random.nextInt(4) == 0 ? null : value.get();
        }

        private <T> T pick(T[] values) {
            return values[random.nextInt(values.length)];
        }

        private <T> T pick(List<T> values) {
            return values.get(random.nextInt(values.size()));
        }

        private UUID uuid() {
            return new UUID(random.nextLong(), random.nextLong());
        }
    }

    /**
     * The golden scores were written with the congestion and reciprocal formulas of that time; this test checks the
     * weights, so it keeps them (the new formulas of design 13, 13.4-13.5 have tests of their own).
     */
    private static RecommendationSettings oldFormulas() {
        RecommendationSettings settings = new RecommendationSettings();
        ReflectionTestUtils.setField(settings, "congestionFormula", CongestionFormula.EVERY_APPLICATION);
        ReflectionTestUtils.setField(settings, "reciprocalMode", ReciprocalMode.HARMONIC);
        return settings;
    }
}
