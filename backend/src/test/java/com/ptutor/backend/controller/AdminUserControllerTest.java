package com.ptutor.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AccountSuspensionService;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock AccountSuspensionService accountSuspensionService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(
                accountSuspensionService, new ApiResponseFactory(Clock.systemUTC()), currentUserProvider)).build();
    }

    @Test
    void suspendUsesAuthenticatedActor() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(actorId);

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/suspend", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PERMANENT",
                                  "reason": "Repeatedly violated platform rules."
                                }
                                """))
                .andExpect(status().isOk());

        verify(accountSuspensionService).suspend(
                org.mockito.ArgumentMatchers.eq(actorId),
                org.mockito.ArgumentMatchers.eq(targetId),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void temporarySuspensionRequiresValidReason() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/suspend", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TEMPORARY","reason":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
