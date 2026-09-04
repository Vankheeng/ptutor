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

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.TeachingRequestService;

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
    void listVisibleRequestsPassesCurrentRoleToService() throws Exception {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(teachingRequestService.findVisible(UserRole.ADMIN)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/teaching-requests"))
                .andExpect(status().isOk());

        verify(teachingRequestService).findVisible(UserRole.ADMIN);
    }

    @Test
    void getVisibleRequestUsesRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.STUDENT);
        when(teachingRequestService.findVisibleById(requestId, UserRole.STUDENT)).thenReturn(null);

        mockMvc.perform(get("/api/v1/teaching-requests/{requestId}", requestId))
                .andExpect(status().isOk());

        verify(teachingRequestService).findVisibleById(requestId, UserRole.STUDENT);
    }
}
