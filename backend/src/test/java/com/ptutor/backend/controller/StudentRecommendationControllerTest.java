package com.ptutor.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalTime;
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

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.RecommendationService;

@ExtendWith(MockitoExtension.class)
class StudentRecommendationControllerTest {

    private static final String PATH = "/api/v1/students/me/tutor-recommendations";

    @Mock RecommendationService recommendationService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentRecommendationController(
                recommendationService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
    }

    @Test
    void passesFiltersPagingAndCurrentUserToTheService() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        UUID subjectId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();

        mockMvc.perform(post(PATH).param("page", "1").param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filters": {
                                    "subjectId": "%s",
                                    "learningMode": "ANY",
                                    "area": { "scope": "DISTRICTS", "districtIds": ["%s"] },
                                    "price": { "max": 200000 },
                                    "availabilities": [{ "dayOfWeek": 7, "startTime": "19:00", "endTime": "21:00" }],
                                    "styleTagCodes": ["kien_nhan"]
                                  },
                                  "semanticText": "ôn thi vào 10"
                                }
                                """.formatted(subjectId, districtId)))
                .andExpect(status().isOk());

        ArgumentCaptor<TutorRecommendationRequest> request = ArgumentCaptor.forClass(TutorRecommendationRequest.class);
        verify(recommendationService).recommendTutors(eq(userId), request.capture(), eq(1), eq(10), isNull());
        TutorRecommendationRequest.Filters filters = request.getValue().filters();
        assertThat(filters.subjectId()).isEqualTo(subjectId);
        assertThat(filters.gradeId()).isNull();
        assertThat(filters.learningMode()).isEqualTo(ModePreference.ANY);
        assertThat(filters.area().scope()).isEqualTo(AreaScope.DISTRICTS);
        assertThat(filters.area().districtIds()).containsExactly(districtId);
        assertThat(filters.price().max()).isEqualByComparingTo("200000");
        assertThat(filters.availabilities()).singleElement().satisfies(slot -> {
            assertThat(slot.dayOfWeek()).as("ISO: 7 = Sunday").isEqualTo(7);
            assertThat(slot.startTime()).isEqualTo(LocalTime.of(19, 0));
        });
        assertThat(filters.styleTagCodes()).containsExactly("kien_nhan");
        assertThat(request.getValue().semanticText()).isEqualTo("ôn thi vào 10");
    }

    @Test
    void laterPageNeedsOnlyTheLogId() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        UUID logId = UUID.randomUUID();

        mockMvc.perform(post(PATH).param("page", "2").param("logId", logId.toString()))
                .andExpect(status().isOk());

        verify(recommendationService).recommendTutors(eq(userId), isNull(), eq(2), eq(20), eq(logId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"filters\":{\"availabilities\":[{\"dayOfWeek\":0,\"startTime\":\"19:00\",\"endTime\":\"21:00\"}]}}",
        "{\"filters\":{\"availabilities\":[{\"dayOfWeek\":8,\"startTime\":\"19:00\",\"endTime\":\"21:00\"}]}}",
        "{\"filters\":{\"availabilities\":[{\"dayOfWeek\":2,\"startTime\":\"21:00\",\"endTime\":\"19:00\"}]}}",
        "{\"filters\":{\"availabilities\":[{\"dayOfWeek\":2,\"startTime\":\"19:00\"}]}}",
        "{\"filters\":{\"area\":{\"scope\":\"DISTRICTS\",\"districtIds\":[]}}}",
        "{\"filters\":{\"price\":{\"min\":300000,\"max\":200000}}}",
        "{\"filters\":{\"price\":{\"max\":-1}}}"
    })
    void rejectsInvalidCriteriaBeforeSearching(String body) throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(recommendationService, never()).recommendTutors(any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void rejectsTooLongFreeText() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"semanticText\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
