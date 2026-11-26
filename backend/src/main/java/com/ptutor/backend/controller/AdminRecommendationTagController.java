package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.RecommendationTagCreateRequest;
import com.ptutor.backend.dto.request.RecommendationTagStatusRequest;
import com.ptutor.backend.dto.request.RecommendationTagSuggestionRequest;
import com.ptutor.backend.dto.request.RecommendationTagUpdateRequest;
import com.ptutor.backend.dto.response.RecommendationTagResponse;
import com.ptutor.backend.dto.response.RecommendationTagSuggestionResponse;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.RecommendationTagService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/recommendation-tags")
@RequiredArgsConstructor
@Validated
public class AdminRecommendationTagController {

    private static final String PATH = "/api/v1/admin/recommendation-tags";

    private final RecommendationTagService tagService;
    private final ApiResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationTagResponse>>> findAll(
            @RequestParam(required = false) RecommendationTagGroup group,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) RecommendationTagStatus status) {
        return ResponseEntity.ok(responseFactory.success(tagService.findForAdmin(group, subjectId, status), PATH));
    }

    /** Creates tags typed by the administrator and / or kept from the suggestions. */
    @PostMapping
    public ResponseEntity<ApiResponse<List<RecommendationTagResponse>>> create(
            @Valid @RequestBody RecommendationTagCreateRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success(
                "RECOMMENDATION_TAGS_CREATED", "Tags created", tagService.create(request), PATH));
    }

    @PutMapping("/{tagId}")
    public ResponseEntity<ApiResponse<RecommendationTagResponse>> update(
            @PathVariable UUID tagId, @Valid @RequestBody RecommendationTagUpdateRequest request) {
        return ResponseEntity.ok(responseFactory.success(tagService.update(tagId, request), PATH + "/" + tagId));
    }

    @PatchMapping("/{tagId}/status")
    public ResponseEntity<ApiResponse<RecommendationTagResponse>> changeStatus(
            @PathVariable UUID tagId, @Valid @RequestBody RecommendationTagStatusRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                tagService.changeStatus(tagId, request.status()), PATH + "/" + tagId + "/status"));
    }

    /** Topic ideas from the LLM for one subject. Nothing is saved by this call. */
    @PostMapping("/suggestions")
    public ResponseEntity<ApiResponse<RecommendationTagSuggestionResponse>> suggest(
            @Valid @RequestBody RecommendationTagSuggestionRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                tagService.suggestTopics(request.subjectId(), request.maxTags()), PATH + "/suggestions"));
    }
}
