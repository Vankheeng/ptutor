package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.candidate;
import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.RecommendationLearnedWeights;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;

import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class WeightProviderTest {

    private final RecommendationSettings settings = new RecommendationSettings();
    private final WeightProvider provider = new WeightProvider(settings);

    @Test
    void priorOfTheSuitabilityModelsIsTheHandSetWeights() {
        assertThat(provider.weights(WeightModel.A_S2T).values())
                .containsExactly(0.30, 0.20, 0.20, 0.10, 0.10, 0.05, 0.05);
        assertThat(provider.weights(WeightModel.A_T2S).values()).containsExactly(0.30, 0.25, 0.20, 0.10, 0.10, 0.05);
    }

    /** Design 12, section 12.2: b and b' written as one weighted sum. */
    @Test
    void priorOfTheWillingnessModelsIsTheFlattenedFormula() {
        assertThat(provider.weights(WeightModel.B_S2T).values())
                .containsExactly(new double[] { 0.5, 1.0 / 6, 1.0 / 6, 1.0 / 6 }, within(1e-12));
        assertThat(provider.weights(WeightModel.B_T2S).values())
                .containsExactly(new double[] { 0.5, 0.20, 0.15, 0.15 }, within(1e-12));
    }

    @ParameterizedTest
    @EnumSource(WeightModel.class)
    void everyPriorSumsToOneAndHasOneWeightPerFeature(WeightModel model) {
        double[] values = provider.weights(model).values();

        assertThat(values).hasSize(model.features().size());
        assertThat(Arrays.stream(values).sum()).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void weightsAreReadByFeatureName() {
        ModelWeights weights = provider.weights(WeightModel.B_T2S);

        assertThat(weights.get("fitQuality")).isCloseTo(0.20, within(1e-12));
        assertThatThrownBy(() -> weights.get("sem")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelWeights(WeightModel.A_T2S, 1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suitabilityUsesTheWeightsOfTheProvider() {
        // Only the semantic feature counts: a is the semantic score itself.
        FeatureCalculator calculator = new FeatureCalculator(settings,
                serving(new ModelWeights(WeightModel.A_S2T, 1, 0, 0, 0, 0, 0, 0)));

        double a = calculator.calculate(context().build(), candidate().cosine(0.55).build()).suitability();

        assertThat(a).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void willingnessUsesTheWeightsOfTheProvider() {
        // History only: b is b_hist, which for a tutor without decisions is the platform rate.
        WillingnessCalculator calculator = new WillingnessCalculator(settings,
                serving(new ModelWeights(WeightModel.B_S2T, 1, 0, 0, 0)));
        FeatureCalculator features = new FeatureCalculator(settings, provider);
        var context = context().maxPrice("200000").build();
        var posting = candidate().price("150000").mode(TeachingMode.ONLINE).build();

        Willingness willingness = calculator.calculate(context, posting, features.calculate(context, posting), 0.6);

        assertThat(willingness.b()).isCloseTo(0.6, within(1e-9));
    }

    /** A provider that serves the given weights for their model and the prior for the others. */
    private WeightProvider serving(ModelWeights weights) {
        return new WeightProvider(settings) {
            @Override
            public Served served(WeightModel model, UUID userId) {
                return model == weights.model() ? new Served(weights, null, WeightLearningMode.OFF, false)
                        : super.served(model, userId);
            }
        };
    }

    // ---- serving learned weights (design 12, 12.7 and 12.9)

    private static final Clock DAY_ONE = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC);
    private static final double[] LEARNED_A = { 0.25, 0.25, 0.20, 0.10, 0.10, 0.05, 0.05 };
    private static final double[] LEARNED_B = { 0.40, 0.20, 0.20, 0.20 };

    private final RecommendationLearnedWeightsRepository repository = mock(RecommendationLearnedWeightsRepository.class);

    @Test
    void anActiveVersionIsServedOnlyInModeActive() {
        WeightProvider learned = learned(DAY_ONE);

        mode(WeightModel.B_S2T, WeightLearningMode.SHADOW);
        assertThat(learned.weights(WeightModel.B_S2T)).isEqualTo(learned.prior(WeightModel.B_S2T));
        mode(WeightModel.B_S2T, WeightLearningMode.OFF);
        assertThat(learned.weights(WeightModel.B_S2T)).isEqualTo(learned.prior(WeightModel.B_S2T));

        mode(WeightModel.B_S2T, WeightLearningMode.ACTIVE);
        assertThat(learned.weights(WeightModel.B_S2T).values()).containsExactly(LEARNED_B);
        WeightProvider.Served served = learned.served(WeightModel.B_S2T, UUID.randomUUID());
        // The willingness side never samples.
        assertThat(served.sampled()).isFalse();
        assertThat(served.version()).isEqualTo(3);
    }

    @Test
    void switchingLearningOffServesThePriorEverywhere() {
        WeightProvider learned = learned(DAY_ONE);
        mode(WeightModel.B_S2T, WeightLearningMode.ACTIVE);
        ReflectionTestUtils.setField(settings, "learningEnabled", false);

        WeightProvider.Served served = learned.served(WeightModel.B_S2T, null);

        assertThat(served.weights()).isEqualTo(learned.prior(WeightModel.B_S2T));
        assertThat(served.mode()).isEqualTo(WeightLearningMode.OFF);
    }

    @Test
    void anActiveModelWithoutAnActiveVersionServesThePrior() {
        mode(WeightModel.A_S2T, WeightLearningMode.ACTIVE);
        WeightProvider learned = new WeightProvider(settings, repository, DAY_ONE);
        learned.reload();

        assertThat(learned.weightsFor(WeightModel.A_S2T, UUID.randomUUID())).isEqualTo(learned.prior(WeightModel.A_S2T));
        assertThat(learned.served(WeightModel.A_S2T, null).version()).isNull();
    }

    @Test
    void aSearchOfAnActiveSuitabilityModelGetsTheSameThompsonSampleAllDay() {
        mode(WeightModel.A_S2T, WeightLearningMode.ACTIVE);
        WeightProvider learned = learned(DAY_ONE);
        UUID user = UUID.randomUUID();

        WeightProvider.Served first = learned.served(WeightModel.A_S2T, user);
        ModelWeights again = new WeightProvider(settings, repository, DAY_ONE) {
            { reload(); }
        }.weightsFor(WeightModel.A_S2T, user);
        ModelWeights otherUser = learned.weightsFor(WeightModel.A_S2T, UUID.randomUUID());
        ModelWeights nextDay = learned(Clock.offset(DAY_ONE, java.time.Duration.ofDays(1)))
                .weightsFor(WeightModel.A_S2T, user);

        assertThat(first.sampled()).isTrue();
        assertThat(again).isEqualTo(first.weights());
        assertThat(otherUser).isNotEqualTo(first.weights());
        assertThat(nextDay).isNotEqualTo(first.weights());
        // Every sample goes through the safety projection: each weight at most 0.05 from the served one, then
        // normalised by a sum that the 7 steps keep within 1 ± 0.35.
        double[] values = first.weights().values();
        assertThat(Arrays.stream(values).sum()).isCloseTo(1.0, within(1e-12));
        for (int k = 0; k < values.length; k++) {
            assertThat(values[k]).isBetween((LEARNED_A[k] - 0.05) / 1.35, (LEARNED_A[k] + 0.05) / 0.65);
        }
    }

    @Test
    void backgroundWorkAndSearchesWithoutAUserGetTheMean() {
        mode(WeightModel.A_S2T, WeightLearningMode.ACTIVE);
        WeightProvider learned = learned(DAY_ONE);
        UUID user = UUID.randomUUID();

        assertThat(learned.weightsFor(WeightModel.A_S2T, null).values()).containsExactly(LEARNED_A);
        assertThat(WeightProvider.withMeanWeights(() -> learned.weightsFor(WeightModel.A_S2T, user)).values())
                .containsExactly(LEARNED_A);
        assertThat(learned.served(WeightModel.A_S2T, user).sampled()).isTrue();

        ReflectionTestUtils.setField(settings, "learningExplorationScale", 0.0);
        assertThat(learned.weightsFor(WeightModel.A_S2T, user).values()).containsExactly(LEARNED_A);
    }

    @Test
    void aFailedLoadServesThePriorAndAnUpdateLoadsTheNewVersion() {
        mode(WeightModel.B_S2T, WeightLearningMode.ACTIVE);
        when(repository.findByModelKeyAndActiveTrue(WeightModel.B_S2T)).thenThrow(new IllegalStateException("down"));
        WeightProvider learned = new WeightProvider(settings, repository, DAY_ONE);

        learned.reload();
        assertThat(learned.weights(WeightModel.B_S2T)).isEqualTo(learned.prior(WeightModel.B_S2T));

        org.mockito.Mockito.reset(repository);
        when(repository.findByModelKeyAndActiveTrue(WeightModel.B_S2T)).thenReturn(Optional.of(row(WeightModel.B_S2T, 3, LEARNED_B)));
        learned.onWeightsUpdated();
        assertThat(learned.weights(WeightModel.B_S2T).values()).containsExactly(LEARNED_B);
    }

    private WeightProvider learned(Clock clock) {
        when(repository.findByModelKeyAndActiveTrue(WeightModel.A_S2T)).thenReturn(Optional.of(row(WeightModel.A_S2T, 5, LEARNED_A)));
        when(repository.findByModelKeyAndActiveTrue(WeightModel.B_S2T)).thenReturn(Optional.of(row(WeightModel.B_S2T, 3, LEARNED_B)));
        WeightProvider learned = new WeightProvider(settings, repository, clock);
        learned.reload();
        return learned;
    }

    private void mode(WeightModel model, WeightLearningMode mode) {
        ReflectionTestUtils.setField(settings, switch (model) {
            case A_S2T -> "learningModeAS2T";
            case A_T2S -> "learningModeAT2S";
            case B_S2T -> "learningModeBS2T";
            case B_T2S -> "learningModeBT2S";
        }, mode);
    }

    /** A stored version whose statistics come from a few observations, so samples spread around the mean. */
    private static RecommendationLearnedWeights row(WeightModel model, int version, double[] weights) {
        JsonMapper json = JsonMapper.builder().build();
        LinearBayesModel bayes = LinearBayesModel.fromPrior(weights, 20);
        ObjectNode values = json.createObjectNode();
        for (int i = 0; i < weights.length; i++) {
            values.put(model.features().get(i), weights[i]);
        }
        ArrayNode a = json.createArrayNode();
        for (double[] line : bayes.statsA()) {
            ArrayNode row = a.addArray();
            Arrays.stream(line).forEach(row::add);
        }
        ArrayNode c = json.createArrayNode();
        Arrays.stream(bayes.statsC()).forEach(c::add);
        return RecommendationLearnedWeights.builder()
                .modelKey(model).version(version).weights(values).statsA(a).statsC(c).active(true).build();
    }
}
