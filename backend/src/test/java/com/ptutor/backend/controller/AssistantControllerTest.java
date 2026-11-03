package com.ptutor.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.exception.GlobalExceptionHandler;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AssistantProxyService;
import com.ptutor.backend.service.AssistantRateLimiter;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AssistantControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-000000000001");

    @Mock AssistantProxyService proxyService;
    @Mock CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        ApiResponseFactory responseFactory = new ApiResponseFactory(Clock.systemUTC());
        AssistantRateLimiter limiter = new AssistantRateLimiter(Clock.systemUTC(), 2, 300);
        AssistantController controller = new AssistantController(proxyService, limiter, currentUserProvider,
                responseFactory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(responseFactory))
                .build();
    }

    private void loggedInStudent() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.STUDENT);
    }

    @Test
    void forwardsMessageWithUserTokenAndWrapsAnswer() throws Exception {
        loggedInStudent();
        when(proxyService.post(any(), eq("/v1/chat"), anyMap()))
                .thenReturn(jsonMapper.readTree("{\"answer\":\"Tối thiểu 10.000 đ [1]\",\"grounded\":\"FULL\"}"));

        mockMvc.perform(post("/api/v1/assistant/messages")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  Nạp ví tối thiểu?  \",\"pagePath\":\"/student/wallet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grounded").value("FULL"));

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(proxyService).post(
                argThat(caller -> caller.userId().equals(USER_ID) && caller.role().equals("STUDENT")
                        && "Bearer user-token".equals(caller.authorization()) && !caller.requestId().isBlank()),
                eq("/v1/chat"), body.capture());
        Map<?, ?> sent = (Map<?, ?>) body.getValue();
        assertThat(sent.get("message")).isEqualTo("Nạp ví tối thiểu?");  // đã bỏ khoảng trắng hai đầu
        assertThat(sent.get("pagePath")).isEqualTo("/student/wallet");
    }

    @Test
    void rejectsEmptyAndTooLongMessagesWithoutCallingAssistant() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MESSAGE"));
        String tooLong = "a".repeat(1001);
        mockMvc.perform(post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verify(proxyService, never()).post(any(), any(), any());
    }

    @Test
    void rateLimitReturns429AfterLimit() throws Exception {
        loggedInStudent();
        when(proxyService.post(any(), eq("/v1/chat"), anyMap())).thenReturn(jsonMapper.readTree("{}"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"xin chào\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"xin chào\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ASSISTANT_RATE_LIMITED"));
    }

    @Test
    void suggestionsPassRoleAndPage() throws Exception {
        loggedInStudent();
        when(proxyService.get(any(), eq("/v1/suggestions"), anyMap())).thenReturn(jsonMapper.readTree("[]"));

        mockMvc.perform(get("/api/v1/assistant/suggestions").param("pagePath", "/student/wallet"))
                .andExpect(status().isOk());

        verify(proxyService).get(any(), eq("/v1/suggestions"),
                eq(Map.of("role", "STUDENT", "pagePath", "/student/wallet")));
    }

    @Test
    void invalidFeedbackRatingIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages/" + UUID.randomUUID() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isBadRequest());

        verify(proxyService, never()).post(any(), any(), any());
    }
}
