package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.ModelWeights;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.RecommendationLearnedWeights;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.event.LearnedWeightsUpdatedEvent;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;
import com.ptutor.backend.repository.RecommendationLearningModeOverrideRepository;

import tools.jackson.databind.JsonNode;

/**
 * The weights the rankers use (design 12, section 12.9). The calculators ask this class instead of reading
 * {@link RecommendationSettings}.
 *
 * <ul>
 *   <li>The active learned version of each model is loaded at start-up and again on
 *       {@link LearnedWeightsUpdatedEvent}, and kept in memory: a search never reads the database for weights.</li>
 *   <li>The mode of a model is the one an administrator set (stored, loaded with the versions), otherwise the
 *       configured one.</li>
 *   <li>A model is served from its active version only in mode {@code ACTIVE}. {@code OFF}, {@code SHADOW}, no
 *       active version, L1 switched off or a failed load all serve the prior: the hand-set weights.</li>
 *   <li>In {@code ACTIVE}, the suitability side of a search (a, a') gets a Thompson sample around μ, the same for
 *       a user all day long (seed = user and day). The willingness side (b, b') and background work (match alerts,
 *       rerank prefetch, relaxation suggestions, see {@link #withMeanWeights}) get μ.</li>
 * </ul>
 */
@Component
public class WeightProvider {

    private static final Logger log = LoggerFactory.getLogger(WeightProvider.class);
    private static final int SAMPLE_CACHE_SIZE = 2_000;

    /** The weights served for one model, and where they come from (written to the search log). */
    public record Served(ModelWeights weights, Integer version, WeightLearningMode mode, boolean sampled) {
    }

    /** An active learned version: its projected μ and the statistics to sample from. */
    private record Active(int version, ModelWeights mean, double[][] statsA, double[] statsC) {
    }

    private final RecommendationSettings settings;
    private final RecommendationLearnedWeightsRepository repository;
    private final RecommendationLearningModeOverrideRepository modeRepository;
    private final Clock clock;
    private final AtomicReference<Map<WeightModel, Active>> active = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<WeightModel, WeightLearningMode>> modeOverrides = new AtomicReference<>(Map.of());
    private final Map<String, ModelWeights> samples = new ConcurrentHashMap<>();
    /** Set while background work runs: see {@link #withMeanWeights}. */
    private static final ThreadLocal<Boolean> MEAN_ONLY = ThreadLocal.withInitial(() -> false);

    @Autowired
    public WeightProvider(RecommendationSettings settings, RecommendationLearnedWeightsRepository repository,
            RecommendationLearningModeOverrideRepository modeRepository, Clock clock) {
        this.settings = settings;
        this.repository = repository;
        this.modeRepository = modeRepository;
        this.clock = clock;
    }

    /** Learned versions, without modes set by an administrator. */
    public WeightProvider(RecommendationSettings settings, RecommendationLearnedWeightsRepository repository,
            Clock clock) {
        this(settings, repository, null, clock);
    }

    /** Without learned weights: always the prior (unit tests of the calculators). */
    public WeightProvider(RecommendationSettings settings) {
        this(settings, null, null, Clock.systemUTC());
    }

    /** μ (or the prior): the willingness side, and anything that is not a user's search. */
    public ModelWeights weights(WeightModel model) {
        return served(model, null).weights();
    }

    /** The weights of a user's search: a Thompson sample when the model is active, μ otherwise. */
    public ModelWeights weightsFor(WeightModel model, UUID userId) {
        return served(model, userId).weights();
    }

    /** @param userId the searcher, or null for μ */
    public Served served(WeightModel model, UUID userId) {
        WeightLearningMode mode = mode(model);
        Active version = mode == WeightLearningMode.ACTIVE ? active.get().get(model) : null;
        if (version == null) {
            return new Served(prior(model), null, mode, false);
        }
        boolean sample = userId != null && !MEAN_ONLY.get() && isSuitability(model)
                && settings.getLearningExplorationScale() > 0;
        if (!sample) {
            return new Served(version.mean(), version.version(), mode, false);
        }
        LocalDate day = LocalDate.now(clock);
        String key = model + "/" + version.version() + "/" + userId + "/" + day;
        if (samples.size() > SAMPLE_CACHE_SIZE) {
            samples.clear();
        }
        ModelWeights sampled = samples.computeIfAbsent(key, ignored -> sample(model, version, userId, day));
        return new Served(sampled, version.version(), mode, true);
    }

    /** The mode in force: OFF when L1 is switched off, else the administrator's choice, else the configuration. */
    public WeightLearningMode mode(WeightModel model) {
        if (!settings.isLearningEnabled()) {
            return WeightLearningMode.OFF;
        }
        return modeOverrides.get().getOrDefault(model, settings.getLearningMode(model));
    }

    /** The mode an administrator set, or null when the configuration decides. */
    public WeightLearningMode modeOverride(WeightModel model) {
        return modeOverrides.get().get(model);
    }

    /**
     * Runs background work (match alerts, rerank prefetch, relaxation suggestions) with μ instead of a Thompson
     * sample, even when it ranks for a user (design 12, 12.4).
     */
    public static <T> T withMeanWeights(Supplier<T> work) {
        boolean before = MEAN_ONLY.get();
        MEAN_ONLY.set(true);
        try {
            return work.get();
        } finally {
            MEAN_ONLY.set(before);
        }
    }

    /** The hand-set weights, which are also the starting point of learning. */
    public ModelWeights prior(WeightModel model) {
        return switch (model) {
            case A_S2T -> new ModelWeights(model, settings.getWeightSem(), settings.getWeightNeed(),
                    settings.getWeightPrice(), settings.getWeightStyle(), settings.getWeightSched(),
                    settings.getWeightArea(), settings.getWeightSegment());
            case A_T2S -> new ModelWeights(model, settings.getTutorWeightSem(), settings.getTutorWeightPrice(),
                    settings.getTutorWeightNeed(), settings.getTutorWeightSegment(), settings.getTutorWeightSched(),
                    settings.getTutorWeightArea());
            case B_S2T -> flattened(model, settings.getWillingnessHistoryShare(), settings.getWillingnessFitSegment(),
                    settings.getWillingnessFitSched(), settings.getWillingnessFitPrice());
            case B_T2S -> flattened(model, settings.getTutorWillingnessHistoryShare(),
                    settings.getTutorWillingnessFitQuality(), settings.getTutorWillingnessFitStyle(),
                    settings.getTutorWillingnessFitPrice());
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAtStartUp() {
        reload();
    }

    @EventListener(LearnedWeightsUpdatedEvent.class)
    public void onWeightsUpdated() {
        reload();
    }

    /**
     * Loads the active version and the administrator's mode of every model. On failure every model serves its prior
     * until the next load.
     */
    public void reload() {
        if (modeRepository != null) {
            try {
                Map<WeightModel, WeightLearningMode> modes = new EnumMap<>(WeightModel.class);
                modeRepository.findAll().forEach(row -> modes.put(row.getModelKey(), row.getMode()));
                modeOverrides.set(Map.copyOf(modes));
            } catch (RuntimeException exception) {
                log.warn("Unable to load the modes set for the learned weights; using the configured ones", exception);
                modeOverrides.set(Map.of());
            }
        }
        if (repository == null) {
            return;
        }
        Map<WeightModel, Active> loaded = new EnumMap<>(WeightModel.class);
        try {
            for (WeightModel model : WeightModel.values()) {
                repository.findByModelKeyAndActiveTrue(model).ifPresent(row -> loaded.put(model, active(model, row)));
            }
            active.set(Map.copyOf(loaded));
        } catch (RuntimeException exception) {
            log.warn("Unable to load the learned ranking weights; serving the prior weights", exception);
            active.set(Map.of());
        }
        samples.clear();
    }

    private ModelWeights sample(WeightModel model, Active version, UUID userId, LocalDate day) {
        double[] prior = prior(model).values();
        LinearBayesModel bayes = LinearBayesModel.fromStatistics(prior, settings.getLearningPriorStrength(),
                version.statsA(), version.statsC());
        double[] theta = bayes.sample(new Random(seed(model, userId, day)), settings.getLearningExplorationScale(),
                settings.getLearningNoise());
        return new ModelWeights(model, LinearBayesModel.project(theta, prior, version.mean().values(),
                settings.getLearningMinRatio(), settings.getLearningMaxRatio(), settings.getLearningMaxStep()));
    }

    /** Same user, day and model: same sample. */
    static long seed(WeightModel model, UUID userId, LocalDate day) {
        return userId.getMostSignificantBits() ^ Long.rotateLeft(userId.getLeastSignificantBits(), 21)
                ^ (day.toEpochDay() * 0x9E3779B97F4A7C15L) ^ ((long) model.ordinal() << 56);
    }

    private static Active active(WeightModel model, RecommendationLearnedWeights row) {
        double[] weights = new double[model.features().size()];
        for (int i = 0; i < weights.length; i++) {
            JsonNode value = row.getWeights().path(model.features().get(i));
            if (!value.isNumber()) {
                throw new IllegalStateException(model + " version " + row.getVersion() + " has no weight for "
                        + model.features().get(i));
            }
            weights[i] = value.asDouble();
        }
        JsonNode statsA = row.getStatsA();
        double[][] a = new double[statsA.size()][];
        for (int i = 0; i < a.length; i++) {
            a[i] = numbers(statsA.get(i));
        }
        return new Active(row.getVersion(), new ModelWeights(model, weights), a, numbers(row.getStatsC()));
    }

    private static double[] numbers(JsonNode array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).asDouble();
        }
        return values;
    }

    private static boolean isSuitability(WeightModel model) {
        return model == WeightModel.A_S2T || model == WeightModel.A_T2S;
    }

    /**
     * {@code share × hist + (1 - share) × weighted mean of the fit parts} written as one weighted sum
     * (design 12, section 12.2): each fit part gets {@code (1 - share) × w_i / Σ w}.
     */
    private static ModelWeights flattened(WeightModel model, double historyShare, double... fitWeights) {
        double total = 0;
        for (double weight : fitWeights) {
            total += weight;
        }
        double[] values = new double[fitWeights.length + 1];
        values[0] = historyShare;
        for (int i = 0; i < fitWeights.length; i++) {
            values[i + 1] = (1 - historyShare) * fitWeights[i] / total;
        }
        return new ModelWeights(model, values);
    }
}
