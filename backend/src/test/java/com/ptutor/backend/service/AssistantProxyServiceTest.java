package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ptutor.backend.exception.ApiException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AssistantProxyServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-000000000001");
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private MockRestServiceServer server;
    private AssistantProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://assistant");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AssistantProxyService(builder, true, "internal-secret", objectMapper);
    }

    private AssistantProxyService.Caller caller() {
        return new AssistantProxyService.Caller(USER_ID, "STUDENT", "Bearer user-token", "req-1");
    }

    @Test
    void forwardsInternalTokenUserHeadersAndAuthorization() {
        server.expect(requestTo("http://assistant/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "internal-secret"))
                .andExpect(header("X-User-Id", USER_ID.toString()))
                .andExpect(header("X-User-Role", "STUDENT"))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andExpect(header("X-Request-Id", "req-1"))
                .andExpect(content().json("{\"message\":\"Nạp ví tối thiểu?\"}"))
                .andRespond(withSuccess("{\"answer\":\"10.000 đ\"}", MediaType.APPLICATION_JSON));

        var result = service.post(caller(), "/v1/chat", Map.of("message", "Nạp ví tối thiểu?"));

        assertThat(result.path("answer").asString()).isEqualTo("10.000 đ");
        server.verify();
    }

    @Test
    void keepsBusinessErrorCodeFromAssistant() {
        server.expect(requestTo("http://assistant/v1/chat"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"CONVERSATION_ENDED\"}"));

        assertThatThrownBy(() -> service.post(caller(), "/v1/chat", Map.of()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("CONVERSATION_ENDED");
                });
    }

    @Test
    void serverErrorBecomesUnavailable() {
        server.expect(requestTo("http://assistant/v1/chat")).andRespond(withServerError());

        assertThatThrownBy(() -> service.post(caller(), "/v1/chat", Map.of()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo(AssistantProxyService.UNAVAILABLE);
                });
    }

    @Test
    void wrongInternalTokenIsReportedAsUnavailableNotAsUserError() {
        server.expect(requestTo("http://assistant/v1/chat")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.post(caller(), "/v1/chat", Map.of()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(AssistantProxyService.UNAVAILABLE));
    }

    @Test
    void disabledAssistantNeverCallsTheService() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://assistant");
        MockRestServiceServer untouched = MockRestServiceServer.bindTo(builder).build();
        AssistantProxyService disabled = new AssistantProxyService(builder, false, "internal-secret", objectMapper);

        assertThatThrownBy(() -> disabled.get(caller(), "/v1/conversations", Map.of()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        untouched.verify();
    }

    @Test
    void omitsAuthorizationWhenNotNeeded() {
        server.expect(requestTo("http://assistant/v1/conversations?page=0&size=20"))
                .andExpect(request -> assertThat(request.getHeaders().containsHeader("Authorization")).isFalse())
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        Map<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("page", 0);
        query.put("size", 20);
        service.get(new AssistantProxyService.Caller(USER_ID, "TUTOR", null, "req-2"), "/v1/conversations", query);

        server.verify();
    }
}
