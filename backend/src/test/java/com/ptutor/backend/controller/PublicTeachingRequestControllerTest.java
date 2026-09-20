package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
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
import com.ptutor.backend.service.TeachingRequestService;
import com.ptutor.backend.dto.enums.UserRole;

@ExtendWith(MockitoExtension.class)
class PublicTeachingRequestControllerTest {

    @Mock TeachingRequestService teachingRequestService;
    @Mock CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PublicTeachingRequestController controller = new PublicTeachingRequestController(
                teachingRequestService,
                new ApiResponseFactory(Clock.systemUTC()),
                currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listVisibleRequestsUsesDefaultPublicLimit() throws Exception {
        when(teachingRequestService.findPublicVisible(20, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/teaching-requests"))
                .andExpect(status().isOk());

        verify(teachingRequestService).findPublicVisible(20, null, null);
    }

    @Test
    void listVisibleRequestsPassesSubjectAndGradeFilters() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        when(teachingRequestService.findPublicVisible(3, subjectId, gradeId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/teaching-requests")
                        .param("limit", "3")
                        .param("subjectId", subjectId.toString())
                        .param("gradeId", gradeId.toString()))
                .andExpect(status().isOk());

        verify(teachingRequestService).findPublicVisible(3, subjectId, gradeId);
    }

    @Test
    void getVisibleRequestUsesRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.STUDENT);
        when(teachingRequestService.findVisibleById(requestId, userId, UserRole.STUDENT)).thenReturn(null);

        mockMvc.perform(get("/api/v1/teaching-requests/{requestId}", requestId))
                .andExpect(status().isOk());

        verify(teachingRequestService).findVisibleById(requestId, userId, UserRole.STUDENT);
    }
}
