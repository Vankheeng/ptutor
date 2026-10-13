package com.ptutor.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.RecommendationEventRequest;
import com.ptutor.backend.dto.request.RecommendationRefineRequest;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.PreferenceProfileService;
import com.ptutor.backend.service.RecommendationEventService;
import com.ptutor.backend.service.RefineService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** What a user does with the results of their own searches, for students and tutors alike. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class RecommendationInteractionController {

    private final RecommendationEventService eventService;
    private final RefineService refineService;
    private final PreferenceProfileService profileService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    /** Opened, liked, contacted or dismissed one of the results. */
    @PostMapping("/recommendations/{logId}/events")
    public ResponseEntity<ApiResponse<Void>> recordEvent(
            @PathVariable UUID logId, @Valid @RequestBody RecommendationEventRequest request) {
        eventService.record(currentUserProvider.getCurrentUserId(), logId, request);
        return ResponseEntity.ok(responseFactory.success(
                "RECOMMENDATION_EVENT_RECORDED", "Event recorded", null,
                "/api/v1/recommendations/" + logId + "/events"));
    }

    /** Scores again the results the user has not seen yet, using what they did with the ones they saw. */
    @PostMapping("/recommendations/{logId}/refine")
    public ResponseEntity<ApiResponse<Object>> refine(
            @PathVariable UUID logId, @Valid @RequestBody RecommendationRefineRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                refineService.refine(currentUserProvider.getCurrentUserId(), logId, request.seenUpToRank()),
                "/api/v1/recommendations/" + logId + "/refine"));
    }

    /** "Delete my recommendation history": results stop being influenced by past behaviour. */
    @DeleteMapping("/users/me/recommendation-profile")
    public ResponseEntity<ApiResponse<Void>> deleteHistory() {
        profileService.deleteHistory(currentUserProvider.getCurrentUserId());
        return ResponseEntity.ok(responseFactory.success(
                "RECOMMENDATION_HISTORY_DELETED", "Recommendation history deleted", null,
                "/api/v1/users/me/recommendation-profile"));
    }
}
