package com.ptutor.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.RecommendationLearnedWeights;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.event.LearnedWeightsUpdatedEvent;
import com.ptutor.backend.repository.LearningDataRepository;
import com.ptutor.backend.repository.LearningDataRepository.CandidateFeedback;
import com.ptutor.backend.repository.LearningDataRepository.DecisionSample;
import com.ptutor.backend.repository.LearningDataRepository.SearchFeedback;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One nightly update of the learned weights (design 12, section 12.7), for every model whose mode is not OFF:
 *
 * <ol>
 *   <li>start from the latest version, or the prior;</li>
 *   <li>read the new data: suitability up to the reward window ago (so rewards are final), willingness up to now;
 *       the most recent {@code evaluation-days} of it are held out;</li>
 *   <li>update A and c (forgetting first, if enabled);</li>
 *   <li>μ → project → new weights; compare them with the prior on the held-out data;</li>
 *   <li>insert a new version, never overwrite;</li>
 *   <li>activate it when it has enough data and is not worse than the prior;</li>
 *   <li>announce {@link LearnedWeightsUpdatedEvent} so the weight provider loads again.</li>
 * </ol>
 *
 * Each model runs in its own transaction: one failure does not stop the others.
 */
@Service
public class WeightLearningService {

    private static final Logger log = LoggerFactory.getLogger(WeightLearningService.class);

    /** Learning starts from here when a model has no version yet. */
    static final LocalDateTime BEGINNING = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final int NDCG_DEPTH = 10;
    private static final double PROBABILITY_FLOOR = 1e-6;

    /** What one model's update did. */
    public record Outcome(WeightModel model, Integer version, int newSamples, boolean activated, String note) {
    }

    /** One training or evaluation observation of a model. */
    private record Observation(double[] features, double reward, double weight) {
    }

    /** The data of a period: the observations and, for the suitability side, the searches they belong to. */
    private record Data(List<Observation> observations, List<SearchFeedback> searches, List<DecisionSample> decisions) {

        int searchCount() {
            return searches.size();
        }
    }

    /** Held-out comparison of the new weights with the prior (NDCG@10 or log-loss). */
    private record Evaluation(String metric, double learned, double prior, int n) {

        boolean notWorse() {
            return n > 0 && (metric.equals("ndcg10") ? learned >= prior - 1e-9 : learned <= prior + 1e-9);
        }
    }

    private final LearningDataRepository dataRepository;
    private final RecommendationLearnedWeightsRepository weightsRepository;
    private final WeightProvider weightProvider;
    private final RecommendationSettings settings;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transaction;

    public WeightLearningService(LearningDataRepository dataRepository,
            RecommendationLearnedWeightsRepository weightsRepository, WeightProvider weightProvider,
            RecommendationSettings settings, ObjectMapper objectMapper, JdbcTemplate jdbc,
            ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.dataRepository = dataRepository;
        this.weightsRepository = weightsRepository;
        this.weightProvider = weightProvider;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Updates every model that is not OFF; the provider loads again when a version was activated. */
    public List<Outcome> learnAll(LocalDateTime now) {
        List<Outcome> outcomes = new ArrayList<>();
        if (!settings.isLearningEnabled()) {
            log.info("Weight learning is switched off");
            return outcomes;
        }
        for (WeightModel model : WeightModel.values()) {
            if (settings.getLearningMode(model) == WeightLearningMode.OFF) {
                continue;
            }
            try {
                outcomes.add(transaction.execute(status -> learn(model, now)));
            } catch (RuntimeException exception) {
                log.warn("Weight learning failed for {}", model, exception);
                outcomes.add(new Outcome(model, null, 0, false, "failed: " + exception.getMessage()));
            }
        }
        if (outcomes.stream().anyMatch(Outcome::activated)) {
            eventPublisher.publishEvent(new LearnedWeightsUpdatedEvent());
        }
        return outcomes;
    }

    /** One model's update, inside the caller's transaction. */
    Outcome learn(WeightModel model, LocalDateTime now) {
        boolean suitability = model == WeightModel.A_S2T || model == WeightModel.A_T2S;
        Optional<RecommendationLearnedWeights> latest = weightsRepository.findFirstByModelKeyOrderByVersionDesc(model);
        double[] prior = weightProvider.prior(model).values();
        double kappa = settings.getLearningPriorStrength();
        LinearBayesModel bayes = latest
                .map(row -> LinearBayesModel.fromStatistics(prior, kappa, matrix(row.getStatsA()), vector(row.getStatsC())))
                .orElseGet(() -> LinearBayesModel.fromPrior(prior, kappa));
        LocalDateTime trainedUntil = latest.map(RecommendationLearnedWeights::getTrainedUntil).orElse(BEGINNING);
        // Suitability rewards are final only once the reward window has passed.
        LocalDateTime available = suitability ? now.minusDays(settings.getLearningRewardWindowDays()) : now;
        LocalDateTime trainingEnd = available.minusDays(settings.getLearningEvaluationDays());
        if (!trainingEnd.isAfter(trainedUntil)) {
            return new Outcome(model, null, 0, false, "nothing new since " + trainedUntil);
        }

        Data training = data(model, trainedUntil, trainingEnd);
        if (training.observations().isEmpty()) {
            log.info("Weight learning {}: no new observation in ({}, {}]", model, trainedUntil, trainingEnd);
            return new Outcome(model, null, 0, false, "no new observation");
        }
        if (settings.getLearningForgetting() < 1) {
            bayes.forget(settings.getLearningForgetting());
        }
        for (Observation observation : training.observations()) {
            bayes.update(observation.features(), observation.reward(), observation.weight());
        }

        double[] mean = bayes.mean();
        double[] previous = latest.map(row -> weights(model, row.getWeights())).orElse(prior);
        double[] weights = LinearBayesModel.project(mean, prior, previous, settings.getLearningMinRatio(),
                settings.getLearningMaxRatio(), settings.getLearningMaxStep());
        List<String> clamped = clamped(model, mean, prior);
        Evaluation evaluation = evaluate(model, data(model, trainingEnd, available), weights, prior);

        int samples = latest.map(RecommendationLearnedWeights::getSampleCount).orElse(0) + training.observations().size();
        int searches = latest.map(row -> row.getEvaluation() == null ? 0 : row.getEvaluation().path("searches").asInt())
                .orElse(0) + training.searchCount();
        int version = latest.map(row -> row.getVersion() + 1).orElse(1);
        boolean enoughData = suitability
                ? samples >= settings.getLearningMinSamplesA() && searches >= settings.getLearningMinSearchesA()
                : samples >= settings.getLearningMinSamplesB();
        boolean activate = settings.isLearningAutoActivate() && enoughData && evaluation.notWorse();

        ObjectNode evaluationJson = objectMapper.createObjectNode()
                .put("metric", evaluation.metric())
                .put("learned", evaluation.learned())
                .put("prior", evaluation.prior())
                .put("n", evaluation.n())
                .put("newSamples", training.observations().size())
                .put("searches", searches)
                .put("enoughData", enoughData);
        ArrayNode clampedJson = evaluationJson.putArray("clamped");
        clamped.forEach(clampedJson::add);

        if (activate) {
            // The partial unique index allows one active version per model: switch the old one off first.
            jdbc.update("UPDATE recommendation_learned_weights SET is_active = FALSE WHERE model_key = ? AND is_active",
                    model.name());
        }
        weightsRepository.saveAndFlush(RecommendationLearnedWeights.builder()
                .modelKey(model)
                .version(version)
                .featureNames(featureNames(model))
                .weights(named(model, weights))
                .posteriorMean(array(mean))
                .statsA(matrixJson(bayes.statsA()))
                .statsC(array(bayes.statsC()))
                .priorWeights(named(model, prior))
                .sampleCount(samples)
                .trainedUntil(trainingEnd)
                .evaluation(evaluationJson)
                .active(activate)
                .build());

        log.info("Weight learning {} v{}: {} new samples ({} total), weights {} -> {}, distance to prior {}, "
                        + "{} learned {} prior {} on {} held-out, {}",
                model, version, training.observations().size(), samples, round(previous), round(weights),
                String.format("%.4f", distance(weights, prior)), evaluation.metric(),
                String.format("%.4f", evaluation.learned()), String.format("%.4f", evaluation.prior()), evaluation.n(),
                activate ? "activated" : reasonNotActivated(enoughData, evaluation));
        warnAboutLongClamping(model, clamped);
        return new Outcome(model, version, training.observations().size(), activate, activate ? "activated" : "kept");
    }

    private String reasonNotActivated(boolean enoughData, Evaluation evaluation) {
        if (!settings.isLearningAutoActivate()) {
            return "not activated: automatic activation is off";
        }
        if (!enoughData) {
            return "not activated: too little data";
        }
        return evaluation.n() == 0 ? "not activated: no held-out data to compare with the prior"
                : "not activated: worse than the prior";
    }

    /** The observations of {@code model} in {@code (from, to]}. */
    private Data data(WeightModel model, LocalDateTime from, LocalDateTime to) {
        List<Observation> observations = new ArrayList<>();
        if (model == WeightModel.A_S2T || model == WeightModel.A_T2S) {
            List<SearchFeedback> searches = dataRepository.searchFeedback(model, from, to);
            for (SearchFeedback search : searches) {
                for (CandidateFeedback candidate : search.candidates()) {
                    observations.add(new Observation(candidate.features(), candidate.reward(), candidate.weight()));
                }
            }
            return new Data(observations, searches, List.of());
        }
        List<DecisionSample> decisions = dataRepository.decisionSamples(model, from, to);
        for (DecisionSample sample : decisions) {
            observations.add(new Observation(sample.features(), sample.label(), sample.weight()));
        }
        return new Data(observations, List.of(), decisions);
    }

    /**
     * Suitability: mean NDCG@10 over the held-out searches with a positive reward, ranking by Σ w·x.
     * Willingness: weighted log-loss of the predicted acceptance Σ w·x (b before b_cap).
     */
    private Evaluation evaluate(WeightModel model, Data heldOut, double[] learned, double[] prior) {
        if (model == WeightModel.A_S2T || model == WeightModel.A_T2S) {
            double learnedSum = 0;
            double priorSum = 0;
            int n = 0;
            for (SearchFeedback search : heldOut.searches()) {
                if (search.candidates().stream().noneMatch(candidate -> candidate.reward() > 0)) {
                    continue;
                }
                learnedSum += ndcg(search.candidates(), learned);
                priorSum += ndcg(search.candidates(), prior);
                n++;
            }
            return new Evaluation("ndcg10", n == 0 ? 0 : learnedSum / n, n == 0 ? 0 : priorSum / n, n);
        }
        double learnedLoss = 0;
        double priorLoss = 0;
        double total = 0;
        for (DecisionSample sample : heldOut.decisions()) {
            learnedLoss += sample.weight() * logLoss(dot(learned, sample.features()), sample.label());
            priorLoss += sample.weight() * logLoss(dot(prior, sample.features()), sample.label());
            total += sample.weight();
        }
        int n = heldOut.decisions().size();
        return new Evaluation("logloss", total == 0 ? 0 : learnedLoss / total, total == 0 ? 0 : priorLoss / total, n);
    }

    static double ndcg(List<CandidateFeedback> candidates, double[] weights) {
        List<CandidateFeedback> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((CandidateFeedback candidate) -> dot(weights, candidate.features()))
                .reversed().thenComparingInt(CandidateFeedback::rank));
        double[] ideal = candidates.stream().mapToDouble(candidate -> Math.max(0, candidate.reward()))
                .boxed().sorted(Comparator.reverseOrder()).mapToDouble(Double::doubleValue).toArray();
        double dcg = 0;
        double best = 0;
        for (int i = 0; i < Math.min(NDCG_DEPTH, ranked.size()); i++) {
            double discount = Math.log(i + 2) / Math.log(2);
            dcg += Math.max(0, ranked.get(i).reward()) / discount;
            best += ideal[i] / discount;
        }
        return best == 0 ? 0 : dcg / best;
    }

    static double logLoss(double predicted, double label) {
        double p = Math.min(1 - PROBABILITY_FLOOR, Math.max(PROBABILITY_FLOOR, predicted));
        return -(label * Math.log(p) + (1 - label) * Math.log(1 - p));
    }

    /** Features whose μ fell outside the clamp around the prior (design 12, 12.3). */
    private List<String> clamped(WeightModel model, double[] mean, double[] prior) {
        List<String> clamped = new ArrayList<>();
        for (int k = 0; k < prior.length; k++) {
            double low = Math.max(0.01, settings.getLearningMinRatio() * prior[k]);
            double high = Math.max(low, settings.getLearningMaxRatio() * prior[k]);
            if (mean[k + 1] < low || mean[k + 1] > high) {
                clamped.add(model.features().get(k));
            }
        }
        return clamped;
    }

    /** WARN when a weight was clamped this night and the nights before (design 10, 10.4). */
    private void warnAboutLongClamping(WeightModel model, List<String> clampedNow) {
        int nights = settings.getLearningClampWarningNights();
        if (clampedNow.isEmpty() || nights <= 1) {
            return;
        }
        List<RecommendationLearnedWeights> recent = weightsRepository.findAllByModelKeyOrderByVersionDesc(model);
        if (recent.size() < nights) {
            return;
        }
        for (String feature : clampedNow) {
            boolean everyNight = recent.subList(0, nights).stream().allMatch(row -> row.getEvaluation() != null
                    && contains(row.getEvaluation().path("clamped"), feature));
            if (everyNight) {
                log.warn("Weight learning {}: '{}' was clamped {} nights in a row; the prior may be far off or the "
                        + "data unusual", model, feature, nights);
            }
        }
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.asString())) {
                return true;
            }
        }
        return false;
    }

    private ArrayNode featureNames(WeightModel model) {
        ArrayNode names = objectMapper.createArrayNode().add("intercept");
        model.features().forEach(names::add);
        return names;
    }

    private ObjectNode named(WeightModel model, double[] values) {
        ObjectNode json = objectMapper.createObjectNode();
        for (int i = 0; i < values.length; i++) {
            json.put(model.features().get(i), values[i]);
        }
        return json;
    }

    private ArrayNode array(double[] values) {
        ArrayNode json = objectMapper.createArrayNode();
        Arrays.stream(values).forEach(json::add);
        return json;
    }

    private ArrayNode matrixJson(double[][] matrix) {
        ArrayNode json = objectMapper.createArrayNode();
        for (double[] row : matrix) {
            json.add(array(row));
        }
        return json;
    }

    private static double[] weights(WeightModel model, JsonNode named) {
        double[] values = new double[model.features().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = named.path(model.features().get(i)).asDouble();
        }
        return values;
    }

    private static double[][] matrix(JsonNode json) {
        double[][] matrix = new double[json.size()][];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = vector(json.get(i));
        }
        return matrix;
    }

    private static double[] vector(JsonNode json) {
        double[] values = new double[json.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = json.get(i).asDouble();
        }
        return values;
    }

    private static double dot(double[] weights, double[] features) {
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i] * features[i];
        }
        return sum;
    }

    private static double distance(double[] weights, double[] prior) {
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Math.abs(weights[i] - prior[i]);
        }
        return sum;
    }

    private static String round(double[] values) {
        return Arrays.toString(Arrays.stream(values).map(value -> Math.round(value * 1000) / 1000.0).toArray());
    }
}
