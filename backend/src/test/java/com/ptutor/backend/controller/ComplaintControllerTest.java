package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
import com.ptutor.backend.dto.request.ComplaintUpdateRequest;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ComplaintService;

@ExtendWith(MockitoExtension.class)
class ComplaintControllerTest {

    @Mock ComplaintService complaintService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ComplaintController(
                complaintService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void createUsesCurrentUser() throws Exception {
        when(complaintService.create(eq(userId), any(ComplaintCreateRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/users/me/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"title\":\"Schedule issue\",\"content\":\"Lesson was cancelled\"}"))
                .andExpect(status().isCreated());

        verify(complaintService).create(eq(userId), any(ComplaintCreateRequest.class));
    }

    @Test
    void listUsesStatusAndPagination() throws Exception {
        when(complaintService.findMine(eq(userId), eq(ComplaintStatus.PENDING), any(Pageable.class))).thenReturn(null);

        mockMvc.perform(get("/api/v1/users/me/complaints")
                        .param("status", "PENDING").param("page", "1").param("size", "10"))
                .andExpect(status().isOk());

        verify(complaintService).findMine(eq(userId), eq(ComplaintStatus.PENDING), any(Pageable.class));
    }

    @Test
    void cancelUsesCurrentUser() throws Exception {
        UUID complaintId = UUID.randomUUID();
        when(complaintService.cancel(userId, complaintId)).thenReturn(null);

        mockMvc.perform(post("/api/v1/users/me/complaints/{complaintId}/cancel", complaintId))
                .andExpect(status().isOk());

        verify(complaintService).cancel(userId, complaintId);
    }

    @Test
    void updateUsesCurrentUser() throws Exception {
        UUID complaintId = UUID.randomUUID();
        when(complaintService.update(eq(userId), eq(complaintId), any(ComplaintUpdateRequest.class))).thenReturn(null);

        mockMvc.perform(put("/api/v1/users/me/complaints/{complaintId}", complaintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated issue\",\"content\":\"Updated content\"}"))
                .andExpect(status().isOk());

        verify(complaintService).update(eq(userId), eq(complaintId), any(ComplaintUpdateRequest.class));
    }
}
