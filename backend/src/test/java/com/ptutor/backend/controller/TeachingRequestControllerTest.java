package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.TeachingRequestService;

@ExtendWith(MockitoExtension.class)
class TeachingRequestControllerTest {

    @Mock TeachingRequestService teachingRequestService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        TeachingRequestController controller = new TeachingRequestController(
                teachingRequestService,
                new ApiResponseFactory(Clock.systemUTC()),
                currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void listMyTeachingRequestsUsesTutorRouteAndStatusFilter() throws Exception {
        when(teachingRequestService.findMine(userId, RequestStatus.PENDING_REVIEW)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tutors/me/teaching-requests")
                        .param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk());

        verify(teachingRequestService).findMine(userId, RequestStatus.PENDING_REVIEW);
    }

    @Test
    void cancelUsesTutorRoute() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(teachingRequestService.cancel(userId, requestId)).thenReturn(null);

        mockMvc.perform(post("/api/v1/tutors/me/teaching-requests/{requestId}/cancel", requestId))
                .andExpect(status().isOk());

        verify(teachingRequestService).cancel(userId, requestId);
    }
}
