package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminComplaintService;

@ExtendWith(MockitoExtension.class)
class AdminComplaintControllerTest {

    @Mock AdminComplaintService service;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID reviewerUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminComplaintController(
                service, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        reviewerUserId = UUID.randomUUID();
    }

    @Test
    void listUsesFiltersAndOldestFirst() throws Exception {
        UUID contractId = UUID.randomUUID();
        when(service.findAll(eq(ComplaintStatus.PENDING), eq(contractId), eq("schedule"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/admin/complaints")
                        .param("status", "PENDING")
                        .param("contractId", contractId.toString())
                        .param("keyword", "schedule"))
                .andExpect(status().isOk());

        verify(service).findAll(eq(ComplaintStatus.PENDING), eq(contractId), eq("schedule"),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getSort().getOrderFor("createdAt").isAscending()));
    }

    @Test
    void listAllowsEmptyFilters() throws Exception {
        when(service.findAll(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/admin/complaints"))
                .andExpect(status().isOk());
    }

    @Test
    void startReviewUsesAuthenticatedEmployee() throws Exception {
        UUID complaintId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(reviewerUserId);

        mockMvc.perform(patch("/api/v1/admin/complaints/{id}/start-review", complaintId))
                .andExpect(status().isOk());

        verify(service).startReview(reviewerUserId, complaintId);
    }

    @Test
    void acceptRequiresResolution() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/complaints/{id}/accept", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestEvidencePassesResolution() throws Exception {
        UUID complaintId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(reviewerUserId);

        mockMvc.perform(patch("/api/v1/admin/complaints/{id}/request-evidence", complaintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Provide the payment receipt\"}"))
                .andExpect(status().isOk());

        verify(service).requestEvidence(reviewerUserId, complaintId, "Provide the payment receipt");
    }

    @Test
    void reassignUsesAuthenticatedAdminAndTargetEmployee() throws Exception {
        UUID complaintId = UUID.randomUUID();
        UUID targetEmployeeId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(reviewerUserId);

        mockMvc.perform(patch("/api/v1/admin/complaints/{id}/reassign", complaintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"" + targetEmployeeId + "\"}"))
                .andExpect(status().isOk());

        verify(service).reassign(reviewerUserId, complaintId, targetEmployeeId);
    }

    @Test
    void reassignRequiresEmployeeId() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/complaints/{id}/reassign", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
