package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
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

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminTeachingRequestService;

@ExtendWith(MockitoExtension.class)
class AdminTeachingRequestControllerTest {

    @Mock AdminTeachingRequestService service;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminTeachingRequestController(
                service, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
    }

    @Test
    void listDefaultsToPendingReviewAndOldestFirst() throws Exception {
        when(service.findAll(eq(RequestStatus.PENDING_REVIEW), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/admin/teaching-requests"))
                .andExpect(status().isOk());

        verify(service).findAll(eq(RequestStatus.PENDING_REVIEW), isNull(),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getSort().getOrderFor("createdAt").isAscending()));
    }

    @Test
    void rejectRequiresReason() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/teaching-requests/{id}/reject", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveUsesAuthenticatedUser() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(patch("/api/v1/admin/teaching-requests/{id}/approve", requestId))
                .andExpect(status().isOk());

        verify(service).approve(userId, requestId, null);
    }

    @Test
    void approveAcceptsCreateSubjectResolution() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(patch("/api/v1/admin/teaching-requests/{id}/approve", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectResolution": {
                                    "action": "CREATE",
                                    "name": "Advanced Robotics",
                                    "description": "Robotics fundamentals"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        verify(service).approve(eq(userId), eq(requestId), argThat(request ->
                request.subjectResolution().action().name().equals("CREATE")
                        && request.subjectResolution().name().equals("Advanced Robotics")));
    }
}
