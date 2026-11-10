package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.response.RecommendationTagResponse;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.RecommendationTagService;

import lombok.RequiredArgsConstructor;

/** The tags a signed-in user can pick as filter chips when searching. */
@RestController
@RequestMapping("/api/v1/recommendation-tags")
@RequiredArgsConstructor
public class RecommendationTagController {

    private final RecommendationTagService tagService;
    private final ApiResponseFactory responseFactory;

    /**
     * Active tags, optionally of one group. Topics are only returned together with the
     * {@code subjectId} they belong to; style, goal and level tags apply to every subject.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationTagResponse>>> findSelectable(
            @RequestParam(required = false) RecommendationTagGroup group,
            @RequestParam(required = false) UUID subjectId) {
        return ResponseEntity.ok(responseFactory.success(
                tagService.findSelectable(group, subjectId), "/api/v1/recommendation-tags"));
    }
}
