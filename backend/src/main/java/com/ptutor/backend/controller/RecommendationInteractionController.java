package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.QueryParseRequest;
import com.ptutor.backend.dto.request.RecommendationEventRequest;
import com.ptutor.backend.dto.request.RecommendationRefineRequest;
import com.ptutor.backend.dto.response.QueryParseResponse;
import com.ptutor.backend.dto.response.TeachingRequestResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.PreferenceProfileService;
import com.ptutor.backend.service.QueryParseService;
import com.ptutor.backend.service.RecommendationEventService;
import com.ptutor.backend.service.RefineService;
import com.ptutor.backend.service.TeachingRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** What a user does with the results of their own searches, for students and tutors alike. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class RecommendationInteractionController {

    private final RecommendationEventService eventService;
    private final TeachingRequestService teachingRequestService;
    private final RefineService refineService;
    private final PreferenceProfileService profileService;
    private final QueryParseService queryParseService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Turns a typed sentence into values for the filter form. Nothing is searched: the client fills
     * the form, the user reviews it and then searches.
     */
    @PostMapping("/recommendations/parse-query")
    public ResponseEntity<ApiResponse<QueryParseResponse>> parseQuery(@Valid @RequestBody QueryParseRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                queryParseService.parse(currentUserProvider.getCurrentUserId(), request.text(), request.direction()),
                "/api/v1/recommendations/parse-query"));
    }

    /** Opened, liked, contacted or dismissed one of the results. */
    @PostMapping("/recommendations/{logId}/events")
    public ResponseEntity<ApiResponse<Void>> recordEvent(
            @PathVariable UUID logId, @Valid @RequestBody RecommendationEventRequest request) {
        eventService.record(currentUserProvider.getCurrentUserId(), logId, request);
        return ResponseEntity.ok(responseFactory.success(
                "RECOMMENDATION_EVENT_RECORDED", "Event recorded", null,
                "/api/v1/recommendations/" + logId + "/events"));
    }

    @GetMapping("/students/me/favorite-tutors")
    public ResponseEntity<ApiResponse<List<UUID>>> favoriteTutors() {
        return ResponseEntity.ok(responseFactory.success(
                eventService.favoriteTutorUserIds(currentUserProvider.getCurrentUserId()),
                "/api/v1/students/me/favorite-tutors"));
    }

    @GetMapping("/students/me/favorite-tutors/teaching-request-ids")
    public ResponseEntity<ApiResponse<List<UUID>>> favoriteTeachingRequestIds() {
        return ResponseEntity.ok(responseFactory.success(
                eventService.favoriteTeachingRequestIds(currentUserProvider.getCurrentUserId()),
                "/api/v1/students/me/favorite-tutors/teaching-request-ids"));
    }

    @GetMapping("/students/me/favorite-tutors/teaching-requests")
    public ResponseEntity<ApiResponse<List<TeachingRequestResponse>>> favoriteTutorRequests() {
        return ResponseEntity.ok(responseFactory.success(
                teachingRequestService.findFavoritesForStudent(currentUserProvider.getCurrentUserId()),
                "/api/v1/students/me/favorite-tutors/teaching-requests"));
    }

    @PostMapping("/students/me/favorite-tutors/teaching-requests/{requestId}")
    public ResponseEntity<ApiResponse<Void>> favoriteTutorFromOrdinarySearch(@PathVariable UUID requestId) {
        eventService.favoriteTutorFromOrdinarySearch(currentUserProvider.getCurrentUserId(), requestId);
        return ResponseEntity.ok(responseFactory.success(
                "FAVORITE_TUTOR_ADDED", "Favorite tutor added", null,
                "/api/v1/students/me/favorite-tutors/teaching-requests/" + requestId));
    }

    @DeleteMapping("/students/me/favorite-tutors/{tutorId}")
    public ResponseEntity<ApiResponse<Void>> removeFavoriteTutor(@PathVariable UUID tutorId) {
        eventService.removeFavoriteTutor(currentUserProvider.getCurrentUserId(), tutorId);
        return ResponseEntity.ok(responseFactory.success(
                "FAVORITE_TUTOR_REMOVED", "Favorite tutor removed", null,
                "/api/v1/students/me/favorite-tutors/" + tutorId));
    }

    @DeleteMapping("/students/me/favorite-tutors/teaching-requests/{requestId}")
    public ResponseEntity<ApiResponse<Void>> removeFavoriteTeachingRequest(@PathVariable UUID requestId) {
        eventService.removeFavoriteTeachingRequest(currentUserProvider.getCurrentUserId(), requestId);
        return ResponseEntity.ok(responseFactory.success(
                "FAVORITE_TEACHING_REQUEST_REMOVED", "Favorite teaching request removed", null,
                "/api/v1/students/me/favorite-tutors/teaching-requests/" + requestId));
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
