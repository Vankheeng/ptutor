package com.ptutor.backend.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.AssistantFeedbackRequest;
import com.ptutor.backend.dto.request.AssistantMessageRequest;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AssistantProxyService;
import com.ptutor.backend.service.AssistantRateLimiter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * API trợ lý cho học viên và gia sư (docs/support-chatbot/07-api.md mục 7.2). Phân quyền ở
 * {@code SecurityConstants.ASSISTANT_API}: chỉ {@code STUDENT}, {@code TUTOR}.
 *
 * <p>Controller không xử lý nội dung: kiểm tra nhanh đầu vào, giới hạn tần suất, rồi chuyển tiếp sang
 * assistant-service qua {@link AssistantProxyService} và bọc kết quả trong {@code ApiResponse} như mọi API khác.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Validated
public class AssistantController {

    static final int MAX_MESSAGE_CHARS = 1000;

    private final AssistantProxyService proxyService;
    private final AssistantRateLimiter rateLimiter;
    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<JsonNode>> sendMessage(
            @Valid @RequestBody AssistantMessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE",
                    "Message must contain between 1 and 1000 characters");
        }
        AssistantProxyService.Caller caller = caller(authorization, requestId);
        if (!rateLimiter.tryAcquire(caller.userId())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ASSISTANT_RATE_LIMITED",
                    "Too many messages, please try again in a few minutes");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("conversationId", request.conversationId());
        body.put("message", message);
        body.put("pagePath", request.pagePath() == null ? "" : request.pagePath());
        return ok(proxyService.post(caller, "/v1/chat", body), "/api/v1/assistant/messages");
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<JsonNode>> listConversations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.get(caller(null, requestId), "/v1/conversations", Map.of("page", page, "size", size)),
                "/api/v1/assistant/conversations");
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<JsonNode>> conversationMessages(
            @PathVariable UUID conversationId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.get(caller(null, requestId), "/v1/conversations/" + conversationId + "/messages",
                Map.of()), "/api/v1/assistant/conversations/" + conversationId + "/messages");
    }

    @PostMapping("/conversations/{conversationId}/end")
    public ResponseEntity<ApiResponse<JsonNode>> endConversation(
            @PathVariable UUID conversationId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String path = "/v1/conversations/" + conversationId + "/end";
        return ok(proxyService.post(caller(null, requestId), path, Map.of()),
                "/api/v1/assistant/conversations/" + conversationId + "/end");
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<JsonNode>> deleteConversation(
            @PathVariable UUID conversationId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.delete(caller(null, requestId), "/v1/conversations/" + conversationId),
                "/api/v1/assistant/conversations/" + conversationId);
    }

    @PostMapping("/messages/{messageId}/feedback")
    public ResponseEntity<ApiResponse<JsonNode>> feedback(
            @PathVariable UUID messageId,
            @Valid @RequestBody AssistantFeedbackRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", request.rating());
        body.put("reason", request.reason());
        body.put("comment", request.comment());
        return ok(proxyService.post(caller(null, requestId), "/v1/messages/" + messageId + "/feedback", body),
                "/api/v1/assistant/messages/" + messageId + "/feedback");
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<JsonNode>> suggestions(
            @RequestParam(defaultValue = "") String pagePath,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        AssistantProxyService.Caller caller = caller(null, requestId);
        return ok(proxyService.get(caller, "/v1/suggestions", Map.of("role", caller.role(), "pagePath", pagePath)),
                "/api/v1/assistant/suggestions");
    }

    /** Người hỏi lấy từ JWT đã xác thực; token chỉ chuyển tiếp ở lời gọi hỏi đáp (cho tool tra cứu). */
    private AssistantProxyService.Caller caller(String authorization, String requestId) {
        String traceId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        return new AssistantProxyService.Caller(currentUserProvider.getCurrentUserId(),
                currentUserProvider.getCurrentUserRole().name(), authorization, traceId);
    }

    private ResponseEntity<ApiResponse<JsonNode>> ok(JsonNode data, String path) {
        return ResponseEntity.ok(responseFactory.success(data, path));
    }
}
