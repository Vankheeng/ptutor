package com.ptutor.backend.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AssistantProxyService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * API quản trị kho tri thức của trợ lý (docs/support-chatbot/07-api.md mục 7.3). Chỉ {@code ADMIN}
 * ({@code SecurityConstants.ADMIN_ASSISTANT_API}); assistant-service kiểm lại vai trò lần nữa (07 mục 7.4).
 *
 * <p>Nội dung tài liệu được chuyển tiếp nguyên JSON: kiểm tra hợp lệ (trường bắt buộc, route, todo, độ dài đoạn) do
 * assistant-service làm, để chỉ có một nơi giữ quy tắc của kho tri thức.
 */
@RestController
@RequestMapping("/api/v1/admin/assistant")
@RequiredArgsConstructor
public class AdminAssistantController {

    private static final String BASE = "/api/v1/admin/assistant";

    private final AssistantProxyService proxyService;
    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<JsonNode>> listDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String audience,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Map<String, Object> query = new HashMap<>();
        query.put("status", status);
        query.put("type", type);
        query.put("feature", feature);
        query.put("audience", audience);
        query.put("q", q);
        query.put("page", page);
        query.put("size", size);
        return ok(proxyService.get(caller(requestId), "/v1/admin/documents", query), BASE + "/documents");
    }

    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<JsonNode>> createDocument(
            @RequestBody JsonNode body, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.success(
                proxyService.post(caller(requestId), "/v1/admin/documents", body), BASE + "/documents"));
    }

    @PostMapping("/documents/preview")
    public ResponseEntity<ApiResponse<JsonNode>> preview(
            @RequestBody JsonNode body, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.post(caller(requestId), "/v1/admin/documents/preview", body),
                BASE + "/documents/preview");
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<JsonNode>> documentDetail(
            @PathVariable UUID documentId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.get(caller(requestId), document(documentId), Map.of()), publicPath(document(documentId)));
    }

    @PutMapping("/documents/{documentId}/draft")
    public ResponseEntity<ApiResponse<JsonNode>> saveDraft(
            @PathVariable UUID documentId, @RequestBody JsonNode body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String path = document(documentId) + "/draft";
        return ok(proxyService.put(caller(requestId), path, body), publicPath(path));
    }

    @DeleteMapping("/documents/{documentId}/draft")
    public ResponseEntity<ApiResponse<JsonNode>> discardDraft(
            @PathVariable UUID documentId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String path = document(documentId) + "/draft";
        return ok(proxyService.delete(caller(requestId), path), publicPath(path));
    }

    @PostMapping("/documents/{documentId}/draft/validate")
    public ResponseEntity<ApiResponse<JsonNode>> validateDraft(
            @PathVariable UUID documentId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return action(documentId, "/draft/validate", requestId);
    }

    @PostMapping("/documents/{documentId}/publish")
    public ResponseEntity<ApiResponse<JsonNode>> publish(
            @PathVariable UUID documentId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return action(documentId, "/publish", requestId);
    }

    @PostMapping("/documents/{documentId}/archive")
    public ResponseEntity<ApiResponse<JsonNode>> archive(
            @PathVariable UUID documentId, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return action(documentId, "/archive", requestId);
    }

    @GetMapping("/documents/{documentId}/versions/{version}")
    public ResponseEntity<ApiResponse<JsonNode>> versionDetail(
            @PathVariable UUID documentId, @PathVariable int version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        String path = document(documentId) + "/versions/" + version;
        return ok(proxyService.get(caller(requestId), path, Map.of()), publicPath(path));
    }

    @PostMapping("/documents/{documentId}/versions/{version}/restore")
    public ResponseEntity<ApiResponse<JsonNode>> restore(
            @PathVariable UUID documentId, @PathVariable int version,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return action(documentId, "/versions/" + version + "/restore", requestId);
    }

    @GetMapping("/unanswered")
    public ResponseEntity<ApiResponse<JsonNode>> unanswered(
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.get(caller(requestId), "/v1/admin/unanswered", Map.of("days", days)),
                BASE + "/unanswered");
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<JsonNode>> stats(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Map<String, Object> query = new HashMap<>();
        query.put("from", from);
        query.put("to", to);
        return ok(proxyService.get(caller(requestId), "/v1/admin/stats", query), BASE + "/stats");
    }

    @GetMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<JsonNode>> getSetting(
            @PathVariable String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.get(caller(requestId), "/v1/admin/settings/" + key, Map.of()),
                BASE + "/settings/" + key);
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<JsonNode>> putSetting(
            @PathVariable String key, @RequestBody JsonNode body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.put(caller(requestId), "/v1/admin/settings/" + key, body), BASE + "/settings/" + key);
    }

    @PostMapping("/scope-exemplars")
    public ResponseEntity<ApiResponse<JsonNode>> addScopeExemplar(
            @RequestBody JsonNode body, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ok(proxyService.post(caller(requestId), "/v1/admin/scope-exemplars", body), BASE + "/scope-exemplars");
    }

    private ResponseEntity<ApiResponse<JsonNode>> action(UUID documentId, String suffix, String requestId) {
        String path = document(documentId) + suffix;
        return ok(proxyService.post(caller(requestId), path, Map.of()), publicPath(path));
    }

    /** "/v1/admin/documents/…" của assistant-service → "/api/v1/admin/assistant/documents/…" mà frontend gọi. */
    private static String publicPath(String internalPath) {
        return BASE + internalPath.substring("/v1/admin".length());
    }

    private static String document(UUID documentId) {
        return "/v1/admin/documents/" + documentId;
    }

    /** Admin không cần chuyển tiếp token: API quản trị không gọi tool tra cứu nào. */
    private AssistantProxyService.Caller caller(String requestId) {
        String traceId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        return new AssistantProxyService.Caller(currentUserProvider.getCurrentUserId(),
                currentUserProvider.getCurrentUserRole().name(), null, traceId);
    }

    private ResponseEntity<ApiResponse<JsonNode>> ok(JsonNode data, String path) {
        return ResponseEntity.ok(responseFactory.success(data, path));
    }
}
