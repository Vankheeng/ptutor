package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.ptutor.backend.dto.request.StudyingRequestRequest;
import com.ptutor.backend.dto.request.StudyingRequestStatusRequest;
import com.ptutor.backend.dto.request.StudyingRequestUpdateRequest;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.StudyingRequestService;

@ExtendWith(MockitoExtension.class)
class StudyingRequestControllerTest {

    @Mock StudyingRequestService studyingRequestService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        StudyingRequestController controller = new StudyingRequestController(
                studyingRequestService,
                new ApiResponseFactory(Clock.systemUTC()),
                currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void createUsesCurrentUserFromProvider() throws Exception {
        when(studyingRequestService.create(eq(userId), any(StudyingRequestRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/students/me/studying-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"gradeId\":\"22222222-2222-2222-2222-222222222222\","
                                + "\"quantity\":1,"
                                + "\"title\":\"Find a tutor\",\"learningMode\":\"ONLINE\"}"))
                .andExpect(status().isCreated());

        verify(studyingRequestService).create(eq(userId), any(StudyingRequestRequest.class));
    }

    @Test
    void detailUsesCurrentUserAndRequestIdFromRoute() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(studyingRequestService.findMineById(userId, requestId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/students/me/studying-requests/{requestId}", requestId))
                .andExpect(status().isOk());

        verify(studyingRequestService).findMineById(userId, requestId);
    }

    @Test
    void listUsesCurrentUserStatusAndPagination() throws Exception {
        when(studyingRequestService.findMine(eq(userId), eq(RequestStatus.OPEN), any(Pageable.class)))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/students/me/studying-requests")
                        .param("status", "OPEN")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(studyingRequestService).findMine(eq(userId), eq(RequestStatus.OPEN), any(Pageable.class));
    }

    @Test
    void updateUsesCurrentUserAndRequestIdFromRoute() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(studyingRequestService.update(eq(userId), eq(requestId), any(StudyingRequestUpdateRequest.class)))
                .thenReturn(null);

        mockMvc.perform(patch("/api/v1/students/me/studying-requests/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isOk());

        verify(studyingRequestService).update(eq(userId), eq(requestId), any(StudyingRequestUpdateRequest.class));
    }

    @Test
    void updateStatusUsesCurrentUserAndRequestIdFromRoute() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(studyingRequestService.updateStatus(eq(userId), eq(requestId), eq(RequestStatus.CLOSED)))
                .thenReturn(null);

        mockMvc.perform(patch("/api/v1/students/me/studying-requests/{requestId}/status", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        verify(studyingRequestService).updateStatus(userId, requestId, RequestStatus.CLOSED);
    }

    @Test
    void cancelUsesCurrentUserAndRequestIdFromRoute() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(studyingRequestService.cancel(userId, requestId)).thenReturn(null);

        mockMvc.perform(post("/api/v1/students/me/studying-requests/{requestId}/cancel", requestId))
                .andExpect(status().isOk());

        verify(studyingRequestService).cancel(userId, requestId);
    }
}
