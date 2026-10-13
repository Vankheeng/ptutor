package com.ptutor.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.request.SavedSearchRequest;
import com.ptutor.backend.dto.request.SavedSearchUpdateRequest;
import com.ptutor.backend.dto.response.SavedSearchResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.SavedSearchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/saved-searches")
@RequiredArgsConstructor
@Validated
public class SavedSearchController {

    private static final String PATH = "/api/v1/users/me/saved-searches";

    private final SavedSearchService savedSearchService;
    private final ApiResponseFactory responseFactory;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavedSearchResponse>>> findMine() {
        return ResponseEntity.ok(responseFactory.success(
                savedSearchService.findMine(currentUserProvider.getCurrentUserId()), PATH));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavedSearchResponse>> create(@Valid @RequestBody SavedSearchRequest request) {
        return ResponseEntity.status(201).body(responseFactory.success("SAVED_SEARCH_CREATED", "Search saved",
                savedSearchService.create(currentUserProvider.getCurrentUserId(), request), PATH));
    }

    /** Renames the search and / or switches its notifications on or off. */
    @PutMapping("/{savedSearchId}")
    public ResponseEntity<ApiResponse<SavedSearchResponse>> update(
            @PathVariable UUID savedSearchId, @Valid @RequestBody SavedSearchUpdateRequest request) {
        return ResponseEntity.ok(responseFactory.success(
                savedSearchService.update(currentUserProvider.getCurrentUserId(), savedSearchId, request),
                PATH + "/" + savedSearchId));
    }

    @DeleteMapping("/{savedSearchId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID savedSearchId) {
        savedSearchService.delete(currentUserProvider.getCurrentUserId(), savedSearchId);
        return ResponseEntity.ok(responseFactory.success(
                "SAVED_SEARCH_DELETED", "Saved search deleted", null, PATH + "/" + savedSearchId));
    }
}
