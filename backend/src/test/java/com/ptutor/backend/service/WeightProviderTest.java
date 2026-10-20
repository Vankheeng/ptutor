package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.candidate;
import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.enums.TeachingMode;

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
            public ModelWeights weights(WeightModel model) {
                return model == weights.model() ? weights : super.weights(model);
            }
        };
    }
}
