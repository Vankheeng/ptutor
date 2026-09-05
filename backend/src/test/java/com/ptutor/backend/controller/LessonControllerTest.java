package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.ptutor.backend.dto.request.LessonRequest;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.LessonService;

@ExtendWith(MockitoExtension.class)
class LessonControllerTest {

    @Mock LessonService lessonService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;
    private UUID contractId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LessonController(
                lessonService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void createsLessonForCurrentTutor() throws Exception {
        when(lessonService.create(eq(userId), eq(contractId), any(LessonRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/tutors/me/contracts/{contractId}/lessons", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-12\","
                                + "\"startTime\":\"18:00:00\",\"endTime\":\"20:00:00\"}"))
                .andExpect(status().isCreated());

        verify(lessonService).create(eq(userId), eq(contractId), any(LessonRequest.class));
    }

    @Test
    void listsLessonsWithStatusAndPagination() throws Exception {
        when(lessonService.findByContract(eq(userId), eq(contractId), eq(LessonStatus.SCHEDULED), any(Pageable.class)))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/tutors/me/contracts/{contractId}/lessons", contractId)
                        .param("status", "SCHEDULED").param("page", "1").param("size", "10"))
                .andExpect(status().isOk());

        verify(lessonService).findByContract(eq(userId), eq(contractId), eq(LessonStatus.SCHEDULED), any(Pageable.class));
    }

    @Test
    void getsLessonByLessonIdForCurrentTutor() throws Exception {
        UUID lessonId = UUID.randomUUID();
        when(lessonService.findById(userId, lessonId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/tutors/me/lessons/{lessonId}", lessonId))
                .andExpect(status().isOk());

        verify(lessonService).findById(userId, lessonId);
    }

    @Test
    void updatesLessonForCurrentTutor() throws Exception {
        UUID lessonId = UUID.randomUUID();
        when(lessonService.update(eq(userId), eq(lessonId), any(LessonRequest.class))).thenReturn(null);

        mockMvc.perform(put("/api/v1/tutors/me/lessons/{lessonId}", lessonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-12\",\"startTime\":\"18:00:00\","
                                + "\"endTime\":\"20:00:00\"}"))
                .andExpect(status().isOk());

        verify(lessonService).update(eq(userId), eq(lessonId), any(LessonRequest.class));
    }

    @Test
    void updatesLessonStatusForCurrentTutor() throws Exception {
        UUID lessonId = UUID.randomUUID();
        when(lessonService.updateStatus(eq(userId), eq(lessonId), any(LessonRequest.class)))
                .thenReturn(null);

        mockMvc.perform(patch("/api/v1/tutors/me/lessons/{lessonId}/status", lessonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        verify(lessonService).updateStatus(eq(userId), eq(lessonId), any(LessonRequest.class));
    }
}
