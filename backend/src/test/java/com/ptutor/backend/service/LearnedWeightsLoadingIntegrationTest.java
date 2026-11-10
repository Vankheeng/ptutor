package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;

/**
 * Reads a stored active version from PostgreSQL. A provider of its own is used, so the shared one keeps serving
 * whatever it loaded; the row is rolled back.
 */
@SpringBootTest
@Transactional
class LearnedWeightsLoadingIntegrationTest {

    @Autowired RecommendationLearnedWeightsRepository repository;
    @Autowired RecommendationSettings settings;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void restoreSettings() {
        ReflectionTestUtils.setField(settings, "learningModeBT2S", WeightLearningMode.SHADOW);
        ReflectionTestUtils.setField(settings, "learningModeAT2S", WeightLearningMode.SHADOW);
    }

    @Test
    void servesTheActiveVersionStoredInTheDatabase() {
        jdbc.update("DELETE FROM recommendation_learned_weights WHERE model_key IN ('B_T2S', 'A_T2S')");
        jdbc.update("""
                INSERT INTO recommendation_learned_weights
                    (model_key, version, feature_names, weights, posterior_mean, stats_a, stats_c, prior_weights,
                     sample_count, trained_until, is_active)
                VALUES ('B_T2S', 7, '["intercept","hist","fitQuality","fitStyle","fitPrice"]',
                        '{"hist":0.45,"fitQuality":0.25,"fitStyle":0.15,"fitPrice":0.15}', '[0,0.45,0.25,0.15,0.15]',
                        '[[200,0,0,0,0],[0,200,0,0,0],[0,0,200,0,0],[0,0,0,200,0],[0,0,0,0,200]]',
                        '[0,90,50,30,30]', '{"hist":0.5,"fitQuality":0.2,"fitStyle":0.15,"fitPrice":0.15}',
                        420, CURRENT_TIMESTAMP, TRUE)
                """);
        ReflectionTestUtils.setField(settings, "learningModeBT2S", WeightLearningMode.ACTIVE);
        ReflectionTestUtils.setField(settings, "learningModeAT2S", WeightLearningMode.ACTIVE);
        WeightProvider provider = new WeightProvider(settings, repository, Clock.systemUTC());

        provider.reload();

        WeightProvider.Served served = provider.served(WeightModel.B_T2S, null);
        assertThat(served.version()).isEqualTo(7);
        assertThat(served.weights().values()).containsExactly(0.45, 0.25, 0.15, 0.15);
        // No active version of A_T2S: its prior, even in mode ACTIVE.
        assertThat(provider.weightsFor(WeightModel.A_T2S, UUID.randomUUID()))
                .isEqualTo(provider.prior(WeightModel.A_T2S));
    }
}
