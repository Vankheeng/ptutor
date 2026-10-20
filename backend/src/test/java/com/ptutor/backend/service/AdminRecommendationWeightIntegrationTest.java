package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.response.AdminRecommendationWeightsResponse;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.exception.ApiException;

/**
 * Administrators look at the learned weights, roll back to an older version and set a model's mode at runtime.
 * Rows are rolled back; the shared weight provider loads again afterwards.
 */
@SpringBootTest
@Transactional
class AdminRecommendationWeightIntegrationTest {

    private static final String PATH = "/api/v1/admin/recommendations/weights";

    @Autowired AdminRecommendationWeightService weightService;
    @Autowired WeightProvider weightProvider;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM recommendation_learned_weights");
        jdbc.update("DELETE FROM recommendation_learning_modes");
        adminUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, citizen_id, email, password, status) VALUES (?, ?, ?, 'x', 'ACTIVE')",
                adminUserId, "cid-" + adminUserId, adminUserId + "@example.com");
        version(1, 0.40, false);
        version(2, 0.60, true);
    }

    @AfterTransaction
    void reloadTheSharedProvider() {
        weightProvider.reload();
    }

    @Test
    void showsThePriorTheModeAndTheVersionsNewestFirst() {
        AdminRecommendationWeightsResponse.Model model = model(weightService.overview(), WeightModel.B_T2S);

        assertThat(model.prior()).containsEntry("hist", 0.5).containsKeys("fitQuality", "fitStyle", "fitPrice");
        assertThat(model.mode()).isEqualTo(WeightLearningMode.SHADOW);
        assertThat(model.overrideMode()).isNull();
        assertThat(model.activeVersion()).isEqualTo(2);
        assertThat(model.versions()).extracting(AdminRecommendationWeightsResponse.Version::version).containsExactly(2, 1);
    }

    @Test
    void activatingAnOlderVersionRollsBackAndTheProviderServesIt() {
        weightService.setMode(WeightModel.B_T2S, WeightLearningMode.ACTIVE, adminUserId);
        assertThat(weightProvider.weights(WeightModel.B_T2S).get("hist")).isEqualTo(0.60);

        AdminRecommendationWeightsResponse.Model model = weightService.activate(WeightModel.B_T2S, 1);

        assertThat(model.activeVersion()).isEqualTo(1);
        assertThat(model.versions()).filteredOn(AdminRecommendationWeightsResponse.Version::active).hasSize(1);
        assertThat(weightProvider.weights(WeightModel.B_T2S).get("hist")).isEqualTo(0.40);
        assertThatThrownBy(() -> weightService.activate(WeightModel.B_T2S, 9))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LEARNED_WEIGHTS_VERSION_NOT_FOUND"));
    }

    @Test
    void theModeSetByAnAdministratorWinsOverTheConfigurationAndIsStored() {
        AdminRecommendationWeightsResponse.Model model =
                weightService.setMode(WeightModel.B_T2S, WeightLearningMode.ACTIVE, adminUserId);

        assertThat(model.configuredMode()).isEqualTo(WeightLearningMode.SHADOW);
        assertThat(model.overrideMode()).isEqualTo(WeightLearningMode.ACTIVE);
        assertThat(model.mode()).isEqualTo(WeightLearningMode.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM recommendation_learning_modes WHERE model_key = 'B_T2S'",
                UUID.class)).isEqualTo(adminUserId);

        // Back to OFF: the prior at once.
        weightService.setMode(WeightModel.B_T2S, WeightLearningMode.OFF, adminUserId);
        assertThat(weightProvider.weights(WeightModel.B_T2S)).isEqualTo(weightProvider.prior(WeightModel.B_T2S));
    }

    @Test
    void onlyAdministratorsMayUseTheWeightApi() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // The token's subject must be an active user (AccountStatusFilter); only the role changes.

        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(adminUserId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(adminUserId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(adminUserId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    private void version(int version, double hist, boolean active) {
        double fit = (1 - hist) / 3;
        jdbc.update("""
                INSERT INTO recommendation_learned_weights
                    (model_key, version, feature_names, weights, posterior_mean, stats_a, stats_c, prior_weights,
                     sample_count, trained_until, is_active)
                VALUES ('B_T2S', ?, '["intercept","hist","fitQuality","fitStyle","fitPrice"]', CAST(? AS jsonb),
                        '[0,0,0,0,0]', '[[200,0,0,0,0],[0,200,0,0,0],[0,0,200,0,0],[0,0,0,200,0],[0,0,0,0,200]]',
                        '[0,100,40,30,30]', '{"hist":0.5}', 300, CURRENT_TIMESTAMP, ?)
                """, version, "{\"hist\":" + hist + ",\"fitQuality\":" + fit + ",\"fitStyle\":" + fit
                        + ",\"fitPrice\":" + fit + "}", active);
    }

    private static AdminRecommendationWeightsResponse.Model model(AdminRecommendationWeightsResponse response,
            WeightModel model) {
        return response.models().stream().filter(candidate -> candidate.model() == model).findFirst().orElseThrow();
    }
}
