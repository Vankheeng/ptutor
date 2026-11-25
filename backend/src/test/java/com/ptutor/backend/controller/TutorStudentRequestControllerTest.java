package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.request.TutorStudentRequestCreateRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.TutorStudentRequestService;

@ExtendWith(MockitoExtension.class)
class TutorStudentRequestControllerTest {

    @Mock TutorStudentRequestService tutorStudentRequestService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        TutorStudentRequestController controller = new TutorStudentRequestController(
                tutorStudentRequestService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void createUsesCurrentTutorAndStudyingRequestFromRoute() throws Exception {
        UUID studyingRequestId = UUID.randomUUID();
        when(tutorStudentRequestService.create(
                eq(userId), eq(studyingRequestId), any(TutorStudentRequestCreateRequest.class)))
                .thenReturn(null);

        mockMvc.perform(post(
                        "/api/v1/tutors/me/studying-requests/{studyingRequestId}/tutor-student-requests",
                        studyingRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gradeId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"proposedPrice\":150000,\"teachingMode\":\"ONLINE\"}"))
                .andExpect(status().isCreated());

        verify(tutorStudentRequestService).create(
                eq(userId), eq(studyingRequestId), any(TutorStudentRequestCreateRequest.class));
    }

    @Test
    void listUsesCurrentTutorStatusAndPagination() throws Exception {
        when(tutorStudentRequestService.findMine(
                eq(userId), eq(ApplicationStatus.PENDING), any(Pageable.class)))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/tutors/me/tutor-student-requests")
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).findMine(
                eq(userId), eq(ApplicationStatus.PENDING), any(Pageable.class));
    }

    @Test
    void cancelUsesCurrentTutorAndRequestIdFromRoute() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(tutorStudentRequestService.cancel(userId, requestId)).thenReturn(null);

        mockMvc.perform(post("/api/v1/tutors/me/tutor-student-requests/{requestId}/cancel", requestId))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).cancel(userId, requestId);
    }
}
