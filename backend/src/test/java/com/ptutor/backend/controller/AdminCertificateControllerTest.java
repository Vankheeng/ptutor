package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminCertificateService;

@ExtendWith(MockitoExtension.class)
class AdminCertificateControllerTest {

    @Mock AdminCertificateService adminCertificateService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCertificateController(
                adminCertificateService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
        userId = UUID.randomUUID();
    }

    @Test
    void listDefaultsToPendingAndOldestFirst() throws Exception {
        when(adminCertificateService.findAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageResponse<>(java.util.List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/admin/certificates"))
                .andExpect(status().isOk());

        verify(adminCertificateService).findAll(
                org.mockito.ArgumentMatchers.eq(com.ptutor.backend.entity.enums.CertificateStatus.PENDING),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 20
                        && pageable.getSort().getOrderFor("createdAt").isAscending()));
    }

    @Test
    void rejectRequiresReason() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/certificates/{certificateId}/reject", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveUsesAuthenticatedUser() throws Exception {
        UUID certificateId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        mockMvc.perform(patch("/api/v1/admin/certificates/{certificateId}/approve", certificateId))
                .andExpect(status().isOk());

        verify(adminCertificateService).approve(userId, certificateId);
    }
}
