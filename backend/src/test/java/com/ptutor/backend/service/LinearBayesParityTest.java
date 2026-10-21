package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The learning experiment of the evaluation runs a Python port of {@link LinearBayesModel} (design 09, 9.9.3).
 * This replays the cases written by {@code scripts/recommendation_eval/linear_bayes.py --fixture}: the same
 * observations must give the same μ and the same projected weights here, so the mechanism shown in the
 * experiment is the one in the product.
 */
class LinearBayesParityTest {

    private static final String FIXTURE = "/recommendation/linear-bayes-parity.json";

    static List<JsonNode> cases() throws IOException {
        try (InputStream input = LinearBayesParityTest.class.getResourceAsStream(FIXTURE)) {
            List<JsonNode> cases = new ArrayList<>();
            new ObjectMapper().readTree(input).path("cases").forEach(cases::add);
            return cases;
        }
    }

    @ParameterizedTest(name = "{index}")
    @MethodSource("cases")
    void javaGivesTheSameMeanAndWeightsAsThePythonPort(JsonNode fixture) {
        double[] prior = doubles(fixture.path("prior"));
        JsonNode observations = fixture.path("observations");
        double[][] x = new double[observations.size()][];
        double[] r = new double[x.length];
        double[] s = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = doubles(observations.get(i).path("x"));
            r[i] = observations.get(i).path("r").asDouble();
            s[i] = observations.get(i).path("s").asDouble();
        }
        double scale = LinearBayesModel.priorScale(prior, x, r, s);
        assertThat(scale).isCloseTo(fixture.path("expectedPriorScale").asDouble(), within(1e-9));

        LinearBayesModel model = LinearBayesModel.fromPrior(prior, fixture.path("kappa").asDouble(),
                fixture.path("interceptKappa").asDouble(), scale);
        for (int i = 0; i < x.length; i++) {
            model.update(x[i], r[i], s[i]);
        }
        model.forget(fixture.path("forgetting").asDouble());

        double[] mean = model.mean();
        double[] projected = LinearBayesModel.project(mean, prior, doubles(fixture.path("previous")),
                fixture.path("minRatio").asDouble(), fixture.path("maxRatio").asDouble(),
                fixture.path("maxStep").asDouble());

        assertThat(mean).containsExactly(doubles(fixture.path("expectedMean")), within(1e-9));
        assertThat(projected).containsExactly(doubles(fixture.path("expectedProjected")), within(1e-9));
    }

    private static double[] doubles(JsonNode array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).asDouble();
        }
        return values;
    }
}
