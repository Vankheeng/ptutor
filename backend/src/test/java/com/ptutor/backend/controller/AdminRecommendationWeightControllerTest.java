package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.response.AdminRecommendationWeightsResponse;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminRecommendationWeightService;

@ExtendWith(MockitoExtension.class)
class AdminRecommendationWeightControllerTest {

    private static final String PATH = "/api/v1/admin/recommendations/weights";

    @Mock AdminRecommendationWeightService weightService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminRecommendationWeightController(
                weightService, currentUserProvider, new ApiResponseFactory(Clock.systemUTC()))).build();
    }

    @Test
    void showsEveryModel() throws Exception {
        when(weightService.overview()).thenReturn(new AdminRecommendationWeightsResponse(List.of(model())));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.models[0].model").value("B_S2T"))
                .andExpect(jsonPath("$.data.models[0].mode").value("SHADOW"));
    }

    @Test
    void activatesAVersion() throws Exception {
        when(weightService.activate(WeightModel.B_S2T, 3)).thenReturn(model());

        mockMvc.perform(post(PATH + "/B_S2T/activate/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LEARNED_WEIGHTS_ACTIVATED"));

        verify(weightService).activate(WeightModel.B_S2T, 3);
    }

    @Test
    void setsTheModeAsTheCurrentAdministrator() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(adminUserId);
        when(weightService.setMode(WeightModel.A_S2T, WeightLearningMode.ACTIVE, adminUserId)).thenReturn(model());

        mockMvc.perform(put(PATH + "/A_S2T/mode").contentType(MediaType.APPLICATION_JSON).content("""
                        {"mode": "ACTIVE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LEARNED_WEIGHTS_MODE_UPDATED"));

        verify(weightService).setMode(WeightModel.A_S2T, WeightLearningMode.ACTIVE, adminUserId);
    }

    @Test
    void rejectsAnUnknownModelOrAMissingMode() throws Exception {
        mockMvc.perform(post(PATH + "/C_X/activate/1")).andExpect(status().isBadRequest());
        mockMvc.perform(put(PATH + "/A_S2T/mode").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(weightService);
    }

    private static AdminRecommendationWeightsResponse.Model model() {
        return new AdminRecommendationWeightsResponse.Model(WeightModel.B_S2T, WeightModel.B_S2T.features(),
                Map.of("hist", 0.5), WeightLearningMode.SHADOW, null, WeightLearningMode.SHADOW, null, List.of());
    }
}
