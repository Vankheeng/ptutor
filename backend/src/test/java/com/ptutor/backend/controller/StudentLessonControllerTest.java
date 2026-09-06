package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.LessonService;

@ExtendWith(MockitoExtension.class)
class StudentLessonControllerTest {

    @Mock LessonService lessonService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentLessonController(
                lessonService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void confirmsLessonForCurrentStudent() throws Exception {
        UUID lessonId = UUID.randomUUID();
        when(lessonService.confirmByStudent(userId, lessonId)).thenReturn(null);

        mockMvc.perform(post("/api/v1/students/me/lessons/{lessonId}/confirm", lessonId))
                .andExpect(status().isOk());

        verify(lessonService).confirmByStudent(userId, lessonId);
    }
}
