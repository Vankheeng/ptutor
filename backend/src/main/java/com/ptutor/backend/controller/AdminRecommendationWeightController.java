package com.ptutor.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.request.AdminRecommendationWeightModeRequest;
import com.ptutor.backend.dto.response.AdminRecommendationWeightsResponse;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.service.AdminRecommendationWeightService;
import com.ptutor.backend.service.WeightLearningService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Learned ranking weights for administrators (design 08, 8.1; 12, 12.9). ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/recommendations/weights")
@RequiredArgsConstructor
@Validated
public class AdminRecommendationWeightController {

    private static final String PATH = "/api/v1/admin/recommendations/weights";

    private final AdminRecommendationWeightService weightService;
    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;

    /** Prior, mode, active version and version history of every model. */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminRecommendationWeightsResponse>> overview() {
        return ResponseEntity.ok(responseFactory.success(weightService.overview(), PATH));
    }

    /** Runs the nightly update now (409 while a run is going on). */
    @PostMapping("/learn")
    public ResponseEntity<ApiResponse<List<WeightLearningService.Outcome>>> learn() {
        return ResponseEntity.ok(responseFactory.success("WEIGHT_LEARNING_DONE", "Weight learning ran",
                weightService.learnNow(), PATH + "/learn"));
    }

    /** Activates a version, for example to roll back to an older one. */
    @PostMapping("/{model}/activate/{version}")
    public ResponseEntity<ApiResponse<AdminRecommendationWeightsResponse.Model>> activate(
            @PathVariable WeightModel model, @PathVariable int version) {
        String path = PATH + "/" + model + "/activate/" + version;
        return ResponseEntity.ok(responseFactory.success("LEARNED_WEIGHTS_ACTIVATED", "Version activated",
                weightService.activate(model, version), path));
    }

    /** OFF, SHADOW or ACTIVE, over the configured mode. */
    @PutMapping("/{model}/mode")
    public ResponseEntity<ApiResponse<AdminRecommendationWeightsResponse.Model>> setMode(
            @PathVariable WeightModel model, @Valid @RequestBody AdminRecommendationWeightModeRequest request) {
        return ResponseEntity.ok(responseFactory.success("LEARNED_WEIGHTS_MODE_UPDATED", "Mode updated",
                weightService.setMode(model, request.mode(), currentUserProvider.getCurrentUserId()),
                PATH + "/" + model + "/mode"));
    }
}
