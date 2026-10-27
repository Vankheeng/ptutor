package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.response.AdminRecommendationWeightsResponse;
import com.ptutor.backend.dto.response.AdminRecommendationWeightsResponse.Model;
import com.ptutor.backend.dto.response.AdminRecommendationWeightsResponse.Version;
import com.ptutor.backend.entity.RecommendationLearnedWeights;
import com.ptutor.backend.entity.RecommendationLearningModeOverride;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.event.LearnedWeightsUpdatedEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;
import com.ptutor.backend.repository.RecommendationLearningModeOverrideRepository;

/**
 * What administrators can do with the learned weights (design 12, 12.9): look at them, activate an older version
 * (roll back), and set the mode of a model at runtime. Every change is announced after its transaction committed,
 * so the weight provider loads what is really stored.
 */
@Service
public class AdminRecommendationWeightService {

    private final RecommendationLearnedWeightsRepository weightsRepository;
    private final RecommendationLearningModeOverrideRepository modeRepository;
    private final WeightProvider weightProvider;
    private final RecommendationSettings settings;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final WeightLearningService learningService;

    public AdminRecommendationWeightService(RecommendationLearnedWeightsRepository weightsRepository,
            RecommendationLearningModeOverrideRepository modeRepository, WeightProvider weightProvider,
            RecommendationSettings settings, ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager, Clock clock, WeightLearningService learningService) {
        this.learningService = learningService;
        this.weightsRepository = weightsRepository;
        this.modeRepository = modeRepository;
        this.weightProvider = weightProvider;
        this.settings = settings;
        this.eventPublisher = eventPublisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Runs the nightly update now, with the same lock as the job (design 08, 8.1; 13, 13.7). Refused while a run
     * is going on.
     */
    public List<WeightLearningService.Outcome> learnNow() {
        return learningService.learnAllIfIdle(LocalDateTime.now(clock)).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "WEIGHT_LEARNING_RUNNING", "Weight learning is already running"));
    }

    public AdminRecommendationWeightsResponse overview() {
        List<Model> models = new ArrayList<>();
        for (WeightModel model : WeightModel.values()) {
            models.add(model(model));
        }
        return new AdminRecommendationWeightsResponse(models);
    }

    /** Makes {@code version} the active one of {@code model}; the previous active version is kept, inactive. */
    public Model activate(WeightModel model, int version) {
        transaction.executeWithoutResult(status -> {
            RecommendationLearnedWeights chosen = weightsRepository.findByModelKeyAndVersion(model, version)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEARNED_WEIGHTS_VERSION_NOT_FOUND",
                            "Version " + version + " of " + model + " does not exist"));
            weightsRepository.findByModelKeyAndActiveTrue(model)
                    .filter(current -> !current.getId().equals(chosen.getId()))
                    .ifPresent(current -> {
                        // One active version per model: switch the old one off before the new one is written.
                        current.setActive(false);
                        weightsRepository.saveAndFlush(current);
                    });
            chosen.setActive(true);
            weightsRepository.saveAndFlush(chosen);
        });
        eventPublisher.publishEvent(new LearnedWeightsUpdatedEvent());
        return model(model);
    }

    /** Sets the mode of {@code model} over the configured one, until changed again. */
    public Model setMode(WeightModel model, WeightLearningMode mode, UUID adminUserId) {
        transaction.executeWithoutResult(status -> modeRepository.saveAndFlush(RecommendationLearningModeOverride.builder()
                .modelKey(model)
                .mode(mode)
                .updatedBy(adminUserId)
                .updatedAt(LocalDateTime.now(clock))
                .build()));
        eventPublisher.publishEvent(new LearnedWeightsUpdatedEvent());
        return model(model);
    }

    private Model model(WeightModel model) {
        ModelWeights prior = weightProvider.prior(model);
        Map<String, Double> priorByFeature = new LinkedHashMap<>();
        for (int i = 0; i < model.features().size(); i++) {
            priorByFeature.put(model.features().get(i), prior.get(i));
        }
        List<Version> versions = weightsRepository.findAllByModelKeyOrderByVersionDesc(model).stream()
                .map(row -> new Version(row.getVersion(), Boolean.TRUE.equals(row.getActive()), row.getSampleCount(),
                        row.getTrainedUntil(), row.getCreatedAt(), row.getWeights(), row.getEvaluation()))
                .toList();
        Integer activeVersion = versions.stream().filter(Version::active).map(Version::version).findFirst().orElse(null);
        return new Model(model, model.features(), priorByFeature, settings.getLearningMode(model),
                weightProvider.modeOverride(model), weightProvider.mode(model), activeVersion, versions);
    }
}
