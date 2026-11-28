package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.exception.GlobalExceptionHandler;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AssistantProxyService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AdminAssistantControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-000000000004");
    private static final UUID DOCUMENT_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    @Mock AssistantProxyService proxyService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        ApiResponseFactory responseFactory = new ApiResponseFactory(Clock.systemUTC());
        AdminAssistantController controller = new AdminAssistantController(proxyService, currentUserProvider,
                responseFactory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(responseFactory)).build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
    }

    @Test
    void forwardsDraftSaveWithAdminIdentityAndWithoutUserToken() throws Exception {
        when(proxyService.put(any(), eq("/v1/admin/documents/" + DOCUMENT_ID + "/draft"), any()))
                .thenReturn(jsonMapper.readTree("{\"id\":\"" + DOCUMENT_ID + "\"}"));

        mockMvc.perform(put("/api/v1/admin/assistant/documents/" + DOCUMENT_ID + "/draft")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Nạp ví\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/api/v1/admin/assistant/documents/" + DOCUMENT_ID + "/draft"));

        verify(proxyService).put(
                argThat(caller -> caller.userId().equals(ADMIN_ID) && caller.role().equals("ADMIN")
                        && caller.authorization() == null),
                eq("/v1/admin/documents/" + DOCUMENT_ID + "/draft"),
                argThat(body -> "Nạp ví".equals(((tools.jackson.databind.JsonNode) body).path("title").asString())));
    }

    @Test
    void publishConflictKeepsAssistantErrorCode() throws Exception {
        when(proxyService.post(any(), eq("/v1/admin/documents/" + DOCUMENT_ID + "/publish"), anyMap()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "DOCUMENT_INVALID", "Assistant request rejected"));

        mockMvc.perform(post("/api/v1/admin/assistant/documents/" + DOCUMENT_ID + "/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INVALID"));
    }

    @Test
    void listPassesFilters() throws Exception {
        when(proxyService.get(any(), eq("/v1/admin/documents"), anyMap()))
                .thenReturn(jsonMapper.readTree("{\"content\":[],\"totalElements\":0}"));

        mockMvc.perform(get("/api/v1/admin/assistant/documents").param("feature", "wallet").param("q", "nạp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(proxyService).get(any(), eq("/v1/admin/documents"),
                argThat(query -> "wallet".equals(query.get("feature")) && "nạp".equals(query.get("q"))));
    }
}
