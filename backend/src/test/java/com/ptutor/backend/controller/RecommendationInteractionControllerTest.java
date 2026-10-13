package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.request.RecommendationEventRequest;
import com.ptutor.backend.entity.enums.RecommendationAction;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.PreferenceProfileService;
import com.ptutor.backend.service.RecommendationEventService;
import com.ptutor.backend.service.RefineService;

@ExtendWith(MockitoExtension.class)
class RecommendationInteractionControllerTest {

    @Mock RecommendationEventService eventService;
    @Mock RefineService refineService;
    @Mock PreferenceProfileService profileService;
    @Mock com.ptutor.backend.service.QueryParseService queryParseService;
    @Mock CurrentUserProvider currentUserProvider;

    private final UUID userId = UUID.randomUUID();
    private final UUID logId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationInteractionController(
                eventService, refineService, profileService, queryParseService,
                new ApiResponseFactory(Clock.systemUTC()),
                currentUserProvider)).build();
    }

    @Test
    void recordsAnEventForTheSignedInUser() throws Exception {
        UUID candidateId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(post("/api/v1/recommendations/" + logId + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"FAVORITED\", \"rank\": 3, \"candidateId\": \"" + candidateId + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<RecommendationEventRequest> request = ArgumentCaptor.forClass(RecommendationEventRequest.class);
        verify(eventService).record(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.eq(logId),
                request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().action()).isEqualTo(RecommendationAction.FAVORITED);
        org.assertj.core.api.Assertions.assertThat(request.getValue().candidateId()).isEqualTo(candidateId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"rank\": 3, \"candidateId\": \"7d3a1f2e-0000-4000-8000-000000000001\"}",
        "{\"action\": \"VIEWED\"}",
        "{\"action\": \"SHARED\", \"candidateId\": \"7d3a1f2e-0000-4000-8000-000000000001\"}"
    })
    void rejectsAnEventWithoutAKnownActionOrACandidate(String body) throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/" + logId + "/events").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(eventService);
    }

    @Test
    void refinesFromTheLastRankOnScreen() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(refineService.refine(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("results");

        mockMvc.perform(post("/api/v1/recommendations/" + logId + "/refine").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seenUpToRank\": 8}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recommendations/" + logId + "/refine").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seenUpToRank\": -1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/recommendations/" + logId + "/refine").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(refineService).refine(userId, logId, 8);
    }

    @Test
    void parsesASentenceForTheSignedInUserAndRejectsAnEmptyOne() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(post("/api/v1/recommendations/parse-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Gia sư Toán lớp 9\", \"direction\": \"STUDENT_TO_TUTOR\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recommendations/parse-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"  \", \"direction\": \"STUDENT_TO_TUTOR\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/recommendations/parse-query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Gia sư Toán\"}"))
                .andExpect(status().isBadRequest());

        verify(queryParseService).parse(userId, "Gia sư Toán lớp 9",
                com.ptutor.backend.entity.enums.RecommendationDirection.STUDENT_TO_TUTOR);
    }

    @Test
    void deletesTheHistoryOfTheSignedInUser() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(delete("/api/v1/users/me/recommendation-profile")).andExpect(status().isOk());

        verify(profileService).deleteHistory(userId);
    }
}
