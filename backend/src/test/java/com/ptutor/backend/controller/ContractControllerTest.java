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

import com.ptutor.backend.dto.request.ContractTermsRequest;
import com.ptutor.backend.dto.request.ContractUpdateRequest;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.ContractService;

@ExtendWith(MockitoExtension.class)
class ContractControllerTest {

    @Mock ContractService contractService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractController(
                contractService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void listUsesCurrentUserStatusAndPagination() throws Exception {
        when(contractService.findMine(eq(userId), eq(ContractStatus.PENDING), any(Pageable.class))).thenReturn(null);

        mockMvc.perform(get("/api/v1/users/me/contracts")
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(contractService).findMine(eq(userId), eq(ContractStatus.PENDING), any(Pageable.class));
    }

    @Test
    void updateUsesCurrentUserAndContractId() throws Exception {
        UUID contractId = UUID.randomUUID();
        when(contractService.update(eq(userId), eq(contractId), any(ContractUpdateRequest.class))).thenReturn(null);

        mockMvc.perform(patch("/api/v1/users/me/contracts/{contractId}", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":250000,\"preferredSchedule\":\"Weekend mornings\"}"))
                .andExpect(status().isOk());

        verify(contractService).update(eq(userId), eq(contractId), any(ContractUpdateRequest.class));
    }

    @Test
    void signRejectAndCancelUseCurrentUser() throws Exception {
        UUID contractId = UUID.randomUUID();
        when(contractService.sign(userId, contractId)).thenReturn(null);
        when(contractService.reject(userId, contractId)).thenReturn(null);
        when(contractService.cancel(userId, contractId)).thenReturn(null);

        mockMvc.perform(patch("/api/v1/users/me/contracts/{contractId}/sign", contractId))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/users/me/contracts/{contractId}/reject", contractId))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/users/me/contracts/{contractId}/cancel", contractId))
                .andExpect(status().isOk());

        verify(contractService).sign(userId, contractId);
        verify(contractService).reject(userId, contractId);
        verify(contractService).cancel(userId, contractId);
    }

    @Test
    void renewalUsesPostAndCurrentUser() throws Exception {
        UUID contractId = UUID.randomUUID();
        when(contractService.renew(eq(userId), eq(contractId), any(ContractTermsRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/users/me/contracts/{contractId}/renewals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(termsJson()))
                .andExpect(status().isCreated());

        verify(contractService).renew(eq(userId), eq(contractId), any(ContractTermsRequest.class));
    }

    private String termsJson() {
        return "{\"price\":200000,\"paymentPeriod\":\"MONTHLY\",\"totalLessons\":16,"
                + "\"preferredSchedule\":\"Monday evening\",\"startDate\":\"2026-10-01\","
                + "\"endDate\":\"2026-12-01\"}";
    }
}
