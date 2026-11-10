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
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudentRecommendationService;

@ExtendWith(MockitoExtension.class)
class TutorStudentRecommendationControllerTest {

    private static final String PATH = "/api/v1/tutors/me/student-recommendations";

    @Mock StudentRecommendationService studentRecommendationService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TutorStudentRecommendationController(
                studentRecommendationService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
    }

    @Test
    void passesFiltersPagingAndCurrentUserToTheService() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        UUID subjectId = UUID.randomUUID();
        UUID firstGrade = UUID.randomUUID();
        UUID secondGrade = UUID.randomUUID();
        UUID ownPosting = UUID.randomUUID();

        mockMvc.perform(post(PATH).param("page", "1").param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filters": {
                                    "subjectId": "%s",
                                    "gradeIds": ["%s", "%s"],
                                    "learningMode": "OFFLINE",
                                    "area": { "scope": "PROVINCE" },
                                    "expectedPrice": 180000,
                                    "availabilities": [{ "dayOfWeek": 1, "startTime": "08:00", "endTime": "10:00" }],
                                    "targetSegmentCodes": ["trung_binh", "kha"]
                                  },
                                  "useTeachingRequestId": "%s"
                                }
                                """.formatted(subjectId, firstGrade, secondGrade, ownPosting)))
                .andExpect(status().isOk());

        ArgumentCaptor<StudentRecommendationRequest> request =
                ArgumentCaptor.forClass(StudentRecommendationRequest.class);
        verify(studentRecommendationService).recommendStudents(eq(userId), request.capture(), eq(1), eq(10), isNull());
        StudentRecommendationRequest.Filters filters = request.getValue().filters();
        assertThat(filters.subjectId()).isEqualTo(subjectId);
        assertThat(filters.gradeIds()).containsExactly(firstGrade, secondGrade);
        assertThat(filters.area().scope()).isEqualTo(AreaScope.PROVINCE);
        assertThat(filters.expectedPrice()).isEqualByComparingTo("180000");
        assertThat(filters.availabilities()).hasSize(1);
        assertThat(filters.targetSegmentCodes()).containsExactly("trung_binh", "kha");
        assertThat(request.getValue().useTeachingRequestId()).isEqualTo(ownPosting);
    }

    @Test
    void laterPageNeedsOnlyTheLogId() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        UUID logId = UUID.randomUUID();

        mockMvc.perform(post(PATH).param("page", "2").param("logId", logId.toString())).andExpect(status().isOk());

        verify(studentRecommendationService).recommendStudents(eq(userId), isNull(), eq(2), eq(20), eq(logId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"filters\":{\"expectedPrice\":-1}}",
        "{\"filters\":{\"availabilities\":[{\"dayOfWeek\":8,\"startTime\":\"19:00\",\"endTime\":\"21:00\"}]}}",
        "{\"filters\":{\"area\":{\"scope\":\"DISTRICTS\"}}}",
        "{\"filters\":{\"gradeIds\":[null]}}"
    })
    void rejectsInvalidCriteriaBeforeSearching(String body) throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(studentRecommendationService, never()).recommendStudents(any(), any(), anyInt(), anyInt(), any());
    }
}
