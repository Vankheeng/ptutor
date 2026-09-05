package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.TutorStudentRequestService;

@ExtendWith(MockitoExtension.class)
class StudentTutorOfferControllerTest {

    @Mock TutorStudentRequestService tutorStudentRequestService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;
    private UUID studyingRequestId;
    private UUID tutorRequestId;

    @BeforeEach
    void setUp() {
        StudentTutorOfferController controller = new StudentTutorOfferController(
                tutorStudentRequestService,
                new ApiResponseFactory(Clock.systemUTC()),
                currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
        studyingRequestId = UUID.randomUUID();
        tutorRequestId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void listUsesStudentFromJwtAndRouteIds() throws Exception {
        when(tutorStudentRequestService.findMine(
                eq(userId), eq(studyingRequestId), eq(ApplicationStatus.PENDING), any(Pageable.class)))
                .thenReturn(null);

        mockMvc.perform(get(
                        "/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests",
                        studyingRequestId)
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).findMine(
                eq(userId), eq(studyingRequestId), eq(ApplicationStatus.PENDING), any(Pageable.class));
    }

    @Test
    void detailUsesRouteIdsAndJwtUser() throws Exception {
        when(tutorStudentRequestService.findMineById(userId, studyingRequestId, tutorRequestId))
                .thenReturn(null);

        mockMvc.perform(get(
                        "/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}",
                        studyingRequestId, tutorRequestId))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).findMineById(userId, studyingRequestId, tutorRequestId);
    }

    @Test
    void acceptUsesPatchAndRequiresNoRequestBody() throws Exception {
        when(tutorStudentRequestService.accept(userId, studyingRequestId, tutorRequestId)).thenReturn(null);

        mockMvc.perform(patch(
                        "/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}/accept",
                        studyingRequestId, tutorRequestId))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).accept(userId, studyingRequestId, tutorRequestId);
    }

    @Test
    void rejectUsesPatchAndRequiresNoRequestBody() throws Exception {
        when(tutorStudentRequestService.reject(userId, studyingRequestId, tutorRequestId)).thenReturn(null);

        mockMvc.perform(patch(
                        "/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}/reject",
                        studyingRequestId, tutorRequestId))
                .andExpect(status().isOk());

        verify(tutorStudentRequestService).reject(userId, studyingRequestId, tutorRequestId);
    }
}
