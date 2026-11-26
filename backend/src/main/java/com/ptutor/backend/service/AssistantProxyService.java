package com.ptutor.backend.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.ptutor.backend.exception.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cổng duy nhất từ backend sang assistant-service (docs/support-chatbot/02 mục 2.1, 07 mục 7.4; QĐ2).
 *
 * <p>Mỗi lời gọi gắn:
 * <ul>
 *   <li>{@code X-Internal-Token}: chuỗi bí mật dùng chung — assistant-service chỉ nhận lời gọi từ backend;</li>
 *   <li>{@code X-User-Id}, {@code X-User-Role}: người hỏi đã được backend xác thực bằng JWT;</li>
 *   <li>{@code Authorization}: chuyển tiếp nguyên token của người dùng — assistant-service chỉ dùng nó để gọi lại
 *       các API "của tôi" (tool tra cứu), nên không đọc được gì ngoài những gì người dùng tự xem được;</li>
 *   <li>{@code X-Request-Id}: để nối log hai phía.</li>
 * </ul>
 *
 * <p>Lỗi của assistant-service được đổi thành {@link ApiException}: lỗi nghiệp vụ 4xx giữ nguyên mã
 * ({@code CONVERSATION_ENDED}...); tắt / lỗi / quá giờ → {@code 503 ASSISTANT_UNAVAILABLE}, phần còn lại của nền
 * tảng không bị ảnh hưởng.
 */
@Service
public class AssistantProxyService {

    public static final String UNAVAILABLE = "ASSISTANT_UNAVAILABLE";

    private final RestClient restClient;
    private final boolean enabled;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    @Autowired
    public AssistantProxyService(
            @Value("${app.assistant-service.enabled:false}") boolean enabled,
            @Value("${app.assistant-service.base-url:http://127.0.0.1:8100}") String baseUrl,
            @Value("${app.assistant-service.internal-token:}") String internalToken,
            @Value("${app.assistant-service.timeout-ms:30000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory(timeoutMs)), enabled, internalToken,
                objectMapper);
    }

    /** Dùng trong test: truyền builder đã gắn máy chủ giả. */
    AssistantProxyService(RestClient.Builder builder, boolean enabled, String internalToken,
                          ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.enabled = enabled;
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    private static JdkClientHttpRequestFactory requestFactory(long timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // Chờ lâu hơn ngân sách 25 giây của một lượt bên assistant-service (02 mục 2.7: 25 → 30 → 35 giây).
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }

    public record Caller(UUID userId, String role, String authorization, String requestId) {
    }

    public JsonNode get(Caller caller, String path, Map<String, ?> query) {
        return exchange(caller, HttpMethod.GET, path, query, null);
    }

    public JsonNode post(Caller caller, String path, Object body) {
        return exchange(caller, HttpMethod.POST, path, Map.of(), body);
    }

    public JsonNode put(Caller caller, String path, Object body) {
        return exchange(caller, HttpMethod.PUT, path, Map.of(), body);
    }

    public JsonNode delete(Caller caller, String path) {
        return exchange(caller, HttpMethod.DELETE, path, Map.of(), null);
    }

    private JsonNode exchange(Caller caller, HttpMethod method, String path, Map<String, ?> query, Object body) {
        if (!enabled || internalToken.isBlank()) {
            throw unavailable();
        }
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(builder -> withQuery(builder.path(path), query).build())
                    .header("X-Internal-Token", internalToken)
                    .header("X-User-Id", caller.userId().toString())
                    .header("X-User-Role", caller.role())
                    .header("X-Request-Id", caller.requestId())
                    .accept(MediaType.APPLICATION_JSON);
            if (caller.authorization() != null && !caller.authorization().isBlank()) {
                request = request.header("Authorization", caller.authorization());
            }
            if (body != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            JsonNode result = request.retrieve().body(JsonNode.class);
            return result == null ? objectMapper.createObjectNode() : result;
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        }
    }

    private static UriBuilder withQuery(UriBuilder builder, Map<String, ?> query) {
        query.forEach((name, value) -> {
            if (value != null) {
                builder.queryParam(name, value);
            }
        });
        return builder;
    }

    /** 4xx nghiệp vụ → giữ mã do assistant-service trả ({"detail": "CODE"}); còn lại → 503. */
    private ApiException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        boolean businessError = status == 400 || status == 403 || status == 404 || status == 409 || status == 422;
        if (!businessError) {
            return unavailable();  // 401 (sai token nội bộ), 5xx, 503: cấu hình hoặc dịch vụ lỗi
        }
        String code = readCode(exception.getResponseBodyAsString());
        return new ApiException(HttpStatus.valueOf(status), code, "Assistant request rejected: " + code);
    }

    private String readCode(String body) {
        try {
            JsonNode detail = objectMapper.readTree(body).path("detail");
            return detail.isString() ? detail.asString() : "INVALID_REQUEST";
        } catch (RuntimeException exception) {
            return "INVALID_REQUEST";
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "The assistant is temporarily unavailable");
    }
}
