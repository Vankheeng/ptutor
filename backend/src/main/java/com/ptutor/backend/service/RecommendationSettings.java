package com.ptutor.backend.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.CongestionFormula;
import com.ptutor.backend.entity.enums.ReciprocalMode;
import com.ptutor.backend.entity.enums.RerankMode;
import com.ptutor.backend.entity.enums.WeightLearningMode;

import lombok.Getter;

/**
 * Tunable parameters of the ranking ({@code app.recommendation.*}). The defaults are the
 * cold-start values of the design; they are calibrated on the evaluation dataset later.
 */
@Component
@Getter
public class RecommendationSettings {

    @Value("${app.recommendation.algorithm-version:v2.2-rcx}")
    private String algorithmVersion = "v2.2-rcx";

    /** Off: rank by the searcher's side only (V2-a). On: harmonic mean with the other side (V2-R). */
    @Value("${app.recommendation.reciprocal.enabled:true}")
    private boolean reciprocalEnabled = true;

    /** Off: crowded postings are not penalised (V2-R). On: they are (V2-RC). */
    @Value("${app.recommendation.willingness.congestion-enabled:true}")
    private boolean congestionEnabled = true;

    /** Strength of the platform-wide prior in the tutor's acceptance history. */
    @Value("${app.recommendation.willingness.alpha:2}")
    private double willingnessAlpha = 2;

    /** Platform acceptance rate assumed while there are too few decisions to measure it. */
    @Value("${app.recommendation.willingness.p0-default:0.6}")
    private double willingnessDefaultRate = 0.6;

    @Value("${app.recommendation.willingness.p0-min-decisions:20}")
    private int willingnessMinDecisions = 20;

    @Value("${app.recommendation.willingness.history-days:180}")
    private int willingnessHistoryDays = 180;

    @Value("${app.recommendation.willingness.congestion-lambda:0.5}")
    private double congestionLambda = 0.5;

    /**
     * ON_SHORTAGE (since 26/10/2026, λ 0.5): b_cap only drops once the applications waiting reach the places left;
     * the old EVERY_APPLICATION formula dropped from the first one, and on a market with places to spare pushed good
     * postings down (design 13, 13.4). On the crowded scenario both prevent rejections as well; the base comparison
     * is still to be run (step 40).
     */
    @Value("${app.recommendation.willingness.congestion-formula:ON_SHORTAGE}")
    private CongestionFormula congestionFormula = CongestionFormula.ON_SHORTAGE;

    /**
     * GEOMETRIC (since 26/10/2026): R = a^α · b^(1−α); b is a rough estimate, so it should weigh less than a
     * (design 13, 13.5). HARMONIC: the old 2ab / (a + b), kept to compare.
     */
    @Value("${app.recommendation.reciprocal.mode:GEOMETRIC}")
    private ReciprocalMode reciprocalMode = ReciprocalMode.GEOMETRIC;

    /**
     * 0.65, chosen on the crowded scenario (13.5): the list fits better than the old mean (NDCG +0.026) while the
     * rejections do not rise significantly; 0.8 made them rise, 0.5 did not improve the order.
     */
    @Value("${app.recommendation.reciprocal.alpha:0.65}")
    private double reciprocalAlpha = 0.65;

    @Value("${app.recommendation.candidate-limit:200}")
    private int candidateLimit = 200;

    /** Of the limit, how many places are kept for the newest postings that have no embedding yet. */
    @Value("${app.recommendation.candidate-unembedded-quota:20}")
    private int candidateUnembeddedQuota = 20;

    @Value("${app.recommendation.log-candidate-limit:50}")
    private int logCandidateLimit = 50;

    @Value("${app.recommendation.semantic.cos-low:0.30}")
    private double cosLow = 0.30;

    @Value("${app.recommendation.semantic.cos-high:0.80}")
    private double cosHigh = 0.80;

    /** Share of the cross-encoder score in f_sem when a posting has one; the rest is the cosine part. */
    @Value("${app.recommendation.semantic.rerank-weight:0.7}")
    private double rerankWeight = 0.7;

    /** Off: the order of phase 3 is final (V2-RC), whatever {@code rerank.mode} says. On: {@code rerank.mode}. */
    @Value("${app.recommendation.rerank.enabled:true}")
    private boolean rerankEnabled = true;

    /**
     * How a search gets its rerank scores (design 13, 13.2). ONLINE: waits for the model within
     * {@code rerank.timeout-ms}. ASYNC: uses the cached scores only and queues the missing pairs for the background
     * worker, so a search never waits. OFF (the default since 26/10/2026): no reranking, no queue, no prefetch —
     * with every pair scored, reranking did not improve the order in the evaluation; ASYNC is the mode to switch on
     * once real data can show a gain.
     */
    @Value("${app.recommendation.rerank.mode:OFF}")
    private RerankMode rerankMode = RerankMode.OFF;

    /** Pairs waiting for the background worker at most; past that, new pairs are dropped until it catches up. */
    @Value("${app.recommendation.rerank.queue-capacity:2000}")
    private int rerankQueueCapacity = 2000;

    /**
     * Pairs per call of the background worker: one pair takes ~0.9 s on a CPU, so a call stays short and a search
     * that needs the AI service for its query vector never waits long behind it.
     */
    @Value("${app.recommendation.rerank.worker-chunk-size:2}")
    private int rerankWorkerChunkSize = 2;

    /**
     * The background worker scores only when no search is running and none has ended for this long: on one machine
     * the reranking model slows the query embedding a search waits for (design 13, 13.2).
     */
    @Value("${app.recommendation.rerank.worker-quiet-ms:2000}")
    private long rerankWorkerQuietMs = 2000;

    /** A pair that has waited this long is scored anyway, one at a time, so that steady traffic cannot starve it. */
    @Value("${app.recommendation.rerank.worker-max-wait-ms:600000}")
    private long rerankWorkerMaxWaitMs = 600_000;

    /** 10 since 21/10/2026 (was 20): the evaluation found no quality gain worth a wait of ~4 s. */
    @Value("${app.recommendation.rerank.top-k:10}")
    private int rerankTopK = 10;

    /**
     * How long a search may wait for pairs that are not cached yet; what is scored by then is used. 1 500 ms since
     * 21/10/2026 (was 4 000): pairs not scored in time are scored by later searches and the nightly prefetch.
     */
    @Value("${app.recommendation.rerank.timeout-ms:1500}")
    private long rerankTimeoutMs = 1500;

    /** Pairs per call to the AI service: small, so that little work is lost when time runs out. */
    @Value("${app.recommendation.rerank.chunk-size:4}")
    private int rerankChunkSize = 4;

    /** Must match the version the AI service reports, otherwise cached scores are never found. */
    @Value("${app.recommendation.rerank.model-version:bge-reranker-v2-m3-int8}")
    private String rerankModelVersion = "bge-reranker-v2-m3-int8";

    @Value("${app.recommendation.rerank.prefetch-top-k:30}")
    private int rerankPrefetchTopK = 30;

    /** Time budget of the nightly job for one studying posting. */
    @Value("${app.recommendation.rerank.prefetch-timeout-ms:120000}")
    private long rerankPrefetchTimeoutMs = 120_000;

    @Value("${app.recommendation.rerank.cache-retention-days:30}")
    private int rerankCacheRetentionDays = 30;

    @Value("${app.recommendation.weights.sem:0.30}")
    private double weightSem = 0.30;

    @Value("${app.recommendation.weights.need:0.20}")
    private double weightNeed = 0.20;

    @Value("${app.recommendation.weights.price:0.20}")
    private double weightPrice = 0.20;

    @Value("${app.recommendation.weights.style:0.10}")
    private double weightStyle = 0.10;

    @Value("${app.recommendation.weights.sched:0.10}")
    private double weightSched = 0.10;

    @Value("${app.recommendation.weights.area:0.05}")
    private double weightArea = 0.05;

    @Value("${app.recommendation.weights.segment:0.05}")
    private double weightSegment = 0.05;

    // Weights of a' when a tutor looks for students (design 06, 6.12).
    @Value("${app.recommendation.tutor-weights.sem:0.30}")
    private double tutorWeightSem = 0.30;

    @Value("${app.recommendation.tutor-weights.price:0.25}")
    private double tutorWeightPrice = 0.25;

    @Value("${app.recommendation.tutor-weights.need:0.20}")
    private double tutorWeightNeed = 0.20;

    @Value("${app.recommendation.tutor-weights.segment:0.10}")
    private double tutorWeightSegment = 0.10;

    @Value("${app.recommendation.tutor-weights.sched:0.10}")
    private double tutorWeightSched = 0.10;

    @Value("${app.recommendation.tutor-weights.area:0.05}")
    private double tutorWeightArea = 0.05;

    @Value("${app.recommendation.area.same-province:0.6}")
    private double areaSameProvince = 0.6;

    @Value("${app.recommendation.area.other-province:0.2}")
    private double areaOtherProvince = 0.2;

    /** Suitability assumed for the part of the picture we could not see (missing features). */
    @Value("${app.recommendation.confidence-prior:0.5}")
    private double confidencePrior = 0.5;

    @Value("${app.recommendation.low-confidence-threshold:0.4}")
    private double lowConfidenceThreshold = 0.4;

    /** Postings priced above {@code factor x budget} are filtered out; below that the price is scored. */
    @Value("${app.recommendation.price.hard-factor:1.5}")
    private double priceHardFactor = 1.5;

    @Value("${app.recommendation.quality.base:0.7}")
    private double qualityBase = 0.7;

    @Value("${app.recommendation.quality.weight:0.3}")
    private double qualityWeight = 0.3;

    @Value("${app.recommendation.gate.no-schedule-overlap:0.4}")
    private double gateNoScheduleOverlap = 0.4;

    @Value("${app.recommendation.gate.recent-reject:0.5}")
    private double gateRecentReject = 0.5;

    /** Off: no long-term preference profile is used or built (H1). */
    @Value("${app.recommendation.history.enabled:true}")
    private boolean historyEnabled = true;

    /**
     * Off: tags that A1 extracted with the LLM are ignored when ranking, on both sides and in the
     * preference profile. Only for the ablation "V2-full - A1" of the evaluation (design 09, 9.4).
     */
    @Value("${app.recommendation.enrichment.use-llm-tags:true}")
    private boolean llmTagsEnabled = true;

    /** Largest share of f_hist in the suitability. */
    @Value("${app.recommendation.history.max-weight:0.15}")
    private double historyMaxWeight = 0.15;

    /** Number of positive signals at which f_hist reaches its largest share. */
    @Value("${app.recommendation.history.full-weight-signals:10}")
    private int historyFullWeightSignals = 10;

    @Value("${app.recommendation.history.window-days:180}")
    private int historyWindowDays = 180;

    /** A signal counts half as much after this many days. */
    @Value("${app.recommendation.history.half-life-days:30}")
    private double historyHalfLifeDays = 30;

    /** Off: results are shown strictly by score (R6). */
    @Value("${app.recommendation.exploration.enabled:true}")
    private boolean explorationEnabled = true;

    /** Size of the first page, which is where the exploration slots are. */
    @Value("${app.recommendation.exploration.page-size:20}")
    private int explorationPageSize = 20;

    /** 1-based positions of the first page given to less shown postings. */
    @Value("${app.recommendation.exploration.slots:5,12}")
    private int[] explorationSlots = { 5, 12 };

    /** A posting may only take a slot when its score is at least this share of the last one on the page. */
    @Value("${app.recommendation.exploration.min-score-ratio:0.8}")
    private double explorationMinScoreRatio = 0.8;

    /** Off: /refine returns the stored order unchanged (B5). */
    @Value("${app.recommendation.refine.enabled:true}")
    private boolean refineEnabled = true;

    @Value("${app.recommendation.refine.positive-weight:0.6}")
    private double refinePositiveWeight = 0.6;

    @Value("${app.recommendation.refine.negative-weight:0.3}")
    private double refineNegativeWeight = 0.3;

    /** Weight of a result that was skipped over, relative to one that was dismissed. */
    @Value("${app.recommendation.refine.skipped-weight:0.5}")
    private double refineSkippedWeight = 0.5;

    /** Share of the original f_sem kept when a result is scored again within a session. */
    @Value("${app.recommendation.refine.original-share:0.5}")
    private double refineOriginalShare = 0.5;

    /** Off: a typed sentence is never sent to the LLM; it is used as free text as a whole (A2). */
    @Value("${app.recommendation.parse-query.enabled:true}")
    private boolean parseQueryEnabled = true;

    /** How long the user waits for the form to be filled in before the sentence is used as it is. */
    @Value("${app.recommendation.parse-query.timeout-ms:3000}")
    private long parseQueryTimeoutMs = 3000;

    @Value("${app.recommendation.parse-query.cache-hours:24}")
    private int parseQueryCacheHours = 24;

    @Value("${app.recommendation.parse-query.cache-size:500}")
    private int parseQueryCacheSize = 500;

    /** Off: nobody is notified about new postings that fit them (C7). */
    @Value("${app.recommendation.proactive.enabled:true}")
    private boolean alertEnabled = true;

    /** A new posting is worth a notification from this score on ... */
    @Value("${app.recommendation.proactive.notify-threshold:0.65}")
    private double alertNotifyThreshold = 0.65;

    /** ... and only when it is among this many best results of the person's search. */
    @Value("${app.recommendation.proactive.top-k:3}")
    private int alertTopRank = 3;

    /** At most one such notification per person in this many hours. */
    @Value("${app.recommendation.proactive.cooldown-hours:24}")
    private int alertCooldownHours = 24;

    @Value("${app.recommendation.saved-search.max-per-user:5}")
    private int savedSearchLimit = 5;

    /** Off: a search with few good results simply returns them, without suggestions (C6). */
    @Value("${app.recommendation.relaxation.enabled:true}")
    private boolean relaxationEnabled = true;

    /** A result is "good" from this score on. */
    @Value("${app.recommendation.relaxation.good-threshold:0.50}")
    private double relaxationGoodThreshold = 0.50;

    /** Suggestions are looked for when fewer good results than this were found ... */
    @Value("${app.recommendation.relaxation.min-good:5}")
    private int relaxationMinGood = 5;

    /** ... or fewer results than this in total. */
    @Value("${app.recommendation.relaxation.min-results:10}")
    private int relaxationMinResults = 10;

    @Value("${app.recommendation.relaxation.budget-ms:400}")
    private long relaxationBudgetMs = 400;

    @Value("${app.recommendation.relaxation.max-suggestions:3}")
    private int relaxationMaxSuggestions = 3;

    /** A loosening is worth suggesting when it brings at least this many new good results ... */
    @Value("${app.recommendation.relaxation.min-gain:2}")
    private int relaxationMinGain = 2;

    /** ... or one that beats the best current result by this much. */
    @Value("${app.recommendation.relaxation.best-score-margin:0.10}")
    private double relaxationBestScoreMargin = 0.10;

    @Value("${app.recommendation.query-embedding.timeout-ms:1000}")
    private long queryEmbeddingTimeoutMs = 1000;

    @Value("${app.recommendation.query-embedding.cache-size:500}")
    private int queryEmbeddingCacheSize = 500;

    /** Complaints concluded more than this long ago no longer lower the quality score. */
    @Value("${app.recommendation.complaint.window-days:365}")
    private int complaintWindowDays = 365;

    /** Bayesian smoothing: every tutor starts with this many clean signed contracts. */
    @Value("${app.recommendation.complaint.prior-contracts:5}")
    private double complaintPriorContracts = 5;

    @Value("${app.recommendation.complaint.penalty-scale:40}")
    private double complaintPenaltyScale = 40;

    @Value("${app.recommendation.complaint.max-penalty:20}")
    private double complaintMaxPenalty = 20;

    /** Weight of a complaint against the tutor, by the category the complainant chose. */
    @Value("${app.recommendation.complaint.category-weights.absence:1.0}")
    private double complaintWeightAbsence = 1.0;

    @Value("${app.recommendation.complaint.category-weights.late-or-short:0.5}")
    private double complaintWeightLateOrShort = 0.5;

    @Value("${app.recommendation.complaint.category-weights.teaching-quality:0.5}")
    private double complaintWeightTeachingQuality = 0.5;

    @Value("${app.recommendation.complaint.category-weights.misconduct:1.5}")
    private double complaintWeightMisconduct = 1.5;

    @Value("${app.recommendation.complaint.category-weights.serious-misconduct:3.0}")
    private double complaintWeightSeriousMisconduct = 3.0;

    /** OTHER, and complaints concluded before categories existed. */
    @Value("${app.recommendation.complaint.category-weights.other:1.0}")
    private double complaintWeightOther = 1.0;

    /** A complaint the tutor sent and was found at fault for: its category describes the student, not the tutor. */
    @Value("${app.recommendation.complaint.complainant-weight:1.0}")
    private double complaintComplainantWeight = 1.0;

    /** Both parties at fault: the complaint counts for this share. */
    @Value("${app.recommendation.complaint.both-factor:0.5}")
    private double complaintBothFactor = 0.5;

    // ---- Values that used to be written in the calculators (design 12, section 12.2). The history shares and
    // fit weights of b / b' are, with the a / a' weights above, the priors of the learned weights; the rest
    // stays fixed and is only varied by the sensitivity analysis.

    /** need: a need tag matched through its parent tag (or the other way round). */
    @Value("${app.recommendation.features.need.parent-match:0.6}")
    private double needParentMatch = 0.6;

    /** need: the student has needs but the tutor declared no strengths. */
    @Value("${app.recommendation.features.need.without-tutor-tags:0.4}")
    private double needWithoutTutorTags = 0.4;

    /** style: the student wants a style but the tutor declared none. */
    @Value("${app.recommendation.features.style.without-tutor-tags:0.5}")
    private double styleWithoutTutorTags = 0.5;

    @Value("${app.recommendation.features.segment.adjacent:0.5}")
    private double segmentAdjacent = 0.5;

    @Value("${app.recommendation.features.segment.outside:0.1}")
    private double segmentOutside = 0.1;

    /** segment: the tutor declared no target level. */
    @Value("${app.recommendation.features.segment.without-target:0.5}")
    private double segmentWithoutTarget = 0.5;

    /** sched: overlap is measured against at most this much of the searcher's week. */
    @Value("${app.recommendation.features.schedule.need-cap-minutes:180}")
    private int scheduleNeedCapMinutes = 180;

    /** price (student's view): full marks within budget, 0 at budget × (1 + this). */
    @Value("${app.recommendation.features.price.over-budget-range:0.5}")
    private double priceOverBudgetRange = 0.5;

    /** The share of b_hist in b; the rest is b_fit (design 06, 6.6). */
    @Value("${app.recommendation.willingness.history-share:0.5}")
    private double willingnessHistoryShare = 0.5;

    /** Relative weights of the parts of b_fit (equal: b_fit is their mean). */
    @Value("${app.recommendation.willingness.fit-weights.segment:1}")
    private double willingnessFitSegment = 1;

    @Value("${app.recommendation.willingness.fit-weights.sched:1}")
    private double willingnessFitSched = 1;

    @Value("${app.recommendation.willingness.fit-weights.price:1}")
    private double willingnessFitPrice = 1;

    /** Kernel of b_hist: how similar a past decision's student was to this one. */
    @Value("${app.recommendation.willingness.kernel.grade:0.5}")
    private double willingnessKernelGrade = 0.5;

    @Value("${app.recommendation.willingness.kernel.price:0.3}")
    private double willingnessKernelPrice = 0.3;

    @Value("${app.recommendation.willingness.kernel.mode:0.2}")
    private double willingnessKernelMode = 0.2;

    /** Similarity of a kernel part when one of the two values is unknown, and of grades one level apart. */
    @Value("${app.recommendation.willingness.kernel.unknown-similarity:0.5}")
    private double willingnessKernelUnknown = 0.5;

    @Value("${app.recommendation.willingness.kernel.grade-adjacent:0.5}")
    private double willingnessKernelGradeAdjacent = 0.5;

    /** A tutor is unlikely to go more than this share below the asking price (b_fit, a' price). */
    @Value("${app.recommendation.willingness.tutor-price-tolerance:0.3}")
    private double tutorPriceTolerance = 0.3;

    /** The share of b'_hist in b'; the rest is b'_fit (design 06, 6.12). */
    @Value("${app.recommendation.tutor-willingness.history-share:0.5}")
    private double tutorWillingnessHistoryShare = 0.5;

    @Value("${app.recommendation.tutor-willingness.fit-weights.quality:0.4}")
    private double tutorWillingnessFitQuality = 0.4;

    @Value("${app.recommendation.tutor-willingness.fit-weights.style:0.3}")
    private double tutorWillingnessFitStyle = 0.3;

    @Value("${app.recommendation.tutor-willingness.fit-weights.price:0.3}")
    private double tutorWillingnessFitPrice = 0.3;

    /** Value of a missing part of b'_fit and of an unknown similarity in b'_hist. */
    @Value("${app.recommendation.tutor-willingness.neutral:0.5}")
    private double tutorWillingnessNeutral = 0.5;

    @Value("${app.recommendation.tutor-willingness.kernel.price:0.5}")
    private double tutorWillingnessKernelPrice = 0.5;

    @Value("${app.recommendation.tutor-willingness.kernel.quality:0.5}")
    private double tutorWillingnessKernelQuality = 0.5;

    /** f_hist = cosine-share × s(cos) + tag-share × tag affinity (design 06, 6.4). */
    @Value("${app.recommendation.history.cosine-share:0.6}")
    private double historyCosineShare = 0.6;

    @Value("${app.recommendation.history.tag-share:0.4}")
    private double historyTagShare = 0.4;

    /** Vector part of f_hist for a posting that has no vector yet. */
    @Value("${app.recommendation.history.without-vector:0.5}")
    private double historyWithoutVector = 0.5;

    /** How much each kind of behaviour counts in the preference profile (design 07, 7.4). */
    @Value("${app.recommendation.history.signal-weights.viewed:1}")
    private double historySignalViewed = 1;

    @Value("${app.recommendation.history.signal-weights.clicked:1}")
    private double historySignalClicked = 1;

    @Value("${app.recommendation.history.signal-weights.favorited:2}")
    private double historySignalFavorited = 2;

    @Value("${app.recommendation.history.signal-weights.contacted:3}")
    private double historySignalContacted = 3;

    @Value("${app.recommendation.history.signal-weights.requested:3}")
    private double historySignalRequested = 3;

    @Value("${app.recommendation.history.signal-weights.accepted:4}")
    private double historySignalAccepted = 4;

    @Value("${app.recommendation.history.signal-weights.dismissed:-2}")
    private double historySignalDismissed = -2;

    /** {@code rerank.enabled=false} keeps its old meaning: no reranking at all. */
    public RerankMode getRerankMode() {
        return rerankEnabled ? rerankMode : RerankMode.OFF;
    }

    public boolean isRerankEnabled() {
        return getRerankMode() != RerankMode.OFF;
    }

    /** Weight of each kind of signal, keyed like the signals of {@code PreferenceSignalRepository}. */
    public Map<String, Double> getHistorySignalWeights() {
        return Map.of("VIEWED", historySignalViewed, "CLICKED", historySignalClicked,
                "FAVORITED", historySignalFavorited, "CONTACTED", historySignalContacted,
                "REQUESTED", historySignalRequested, "ACCEPTED", historySignalAccepted,
                "DISMISSED", historySignalDismissed);
    }

    // ---- L1: learned ranking weights (design 12, section 12.10)

    /** Off: every model serves its prior and nothing is learned. */
    @Value("${app.recommendation.learning.enabled:true}")
    private boolean learningEnabled = true;

    @Value("${app.recommendation.learning.models.A_S2T.mode:SHADOW}")
    private WeightLearningMode learningModeAS2T = WeightLearningMode.SHADOW;

    @Value("${app.recommendation.learning.models.A_T2S.mode:SHADOW}")
    private WeightLearningMode learningModeAT2S = WeightLearningMode.SHADOW;

    @Value("${app.recommendation.learning.models.B_S2T.mode:SHADOW}")
    private WeightLearningMode learningModeBS2T = WeightLearningMode.SHADOW;

    @Value("${app.recommendation.learning.models.B_T2S.mode:SHADOW}")
    private WeightLearningMode learningModeBT2S = WeightLearningMode.SHADOW;

    /**
     * κ: how many observations the prior is worth. 50 since 27/10/2026 (was 200): once the prior was put on the scale
     * of the rewards, 200 held the weights almost still (design 13, 13.7).
     */
    @Value("${app.recommendation.learning.prior-strength:50}")
    private double learningPriorStrength = 50;

    /** κ₀: the prior on the intercept (the base rate of feedback) is weak, so the weights do not have to explain it. */
    @Value("${app.recommendation.learning.intercept-prior-strength:1}")
    private double learningInterceptPriorStrength = 1;

    /** ν: assumed noise of the feedback; the posterior covariance is ν² A⁻¹. */
    @Value("${app.recommendation.learning.noise:0.3}")
    private double learningNoise = 0.3;

    /**
     * ρ: Thompson exploration of the suitability side; 0 always serves μ. 0.5 since 27/10/2026 (was 1.0): exploring did
     * not beat serving μ, so the order moves less from one day to the next (13.7).
     */
    @Value("${app.recommendation.learning.exploration-scale:0.5}")
    private double learningExplorationScale = 0.5;

    /** Learned weights are clamped to [min-ratio, max-ratio] × prior. */
    @Value("${app.recommendation.learning.min-ratio:0.5}")
    private double learningMinRatio = 0.5;

    @Value("${app.recommendation.learning.max-ratio:2.0}")
    private double learningMaxRatio = 2.0;

    /** Largest change of one weight in one update. */
    @Value("${app.recommendation.learning.max-step:0.05}")
    private double learningMaxStep = 0.05;

    /**
     * γ: forgetting factor applied once before each update (each nightly run); 1 = never forget. 0.99 since 27/10/2026
     * (was 1.0): it caught up faster when the preferences changed and cost almost nothing when they did not; the data
     * of ~70 runs ago keeps half its weight (13.7).
     */
    @Value("${app.recommendation.learning.forgetting:0.99}")
    private double learningForgetting = 0.99;

    /** Days a search waits before its reward is final. */
    @Value("${app.recommendation.learning.reward-window-days:7}")
    private int learningRewardWindowDays = 7;

    @Value("${app.recommendation.learning.rewards.view:0.3}")
    private double learningRewardView = 0.3;

    @Value("${app.recommendation.learning.rewards.favorite:0.6}")
    private double learningRewardFavorite = 0.6;

    @Value("${app.recommendation.learning.rewards.contact:0.8}")
    private double learningRewardContact = 0.8;

    @Value("${app.recommendation.learning.rewards.request:1.0}")
    private double learningRewardRequest = 1.0;

    @Value("${app.recommendation.learning.rewards.dismissed:-0.2}")
    private double learningRewardDismissed = -0.2;

    @Value("${app.recommendation.learning.rewards.repeat-view:true}")
    private boolean learningRewardRepeatView = true;

    @Value("${app.recommendation.learning.rewards.unfavorite:true}")
    private boolean learningRewardUnfavorite = true;

    @Value("${app.recommendation.learning.rewards.alert-click:false}")
    private boolean learningRewardAlertClick = false;

    @Value("${app.recommendation.learning.rewards.bounce:false}")
    private boolean learningRewardBounce = false;

    /** Accepted within this many hours: a stronger positive sample of b. */
    @Value("${app.recommendation.learning.b-samples.fast-accept-hours:24}")
    private int learningFastAcceptHours = 24;

    @Value("${app.recommendation.learning.b-samples.fast-accept-weight:1.5}")
    private double learningFastAcceptWeight = 1.5;

    /** Weight of the positive sample of the side that reached out. */
    @Value("${app.recommendation.learning.b-samples.initiative-weight:0.5}")
    private double learningInitiativeWeight = 0.5;

    /** A request still PENDING after this many days counts as an implicit rejection. */
    @Value("${app.recommendation.learning.b-samples.implicit-reject-days:14}")
    private int learningImplicitRejectDays = 14;

    @Value("${app.recommendation.learning.b-samples.implicit-reject-weight:0.5}")
    private double learningImplicitRejectWeight = 0.5;

    /** Candidates are used down to the lowest position the searcher interacted with, plus this many. */
    @Value("${app.recommendation.learning.viewed-depth-margin:2}")
    private int learningViewedDepthMargin = 2;

    /** Sample weight of a candidate shown in an exploration slot. */
    @Value("${app.recommendation.learning.exploration-sample-weight:2}")
    private double learningExplorationSampleWeight = 2;

    @Value("${app.recommendation.learning.ipw.enabled:false}")
    private boolean learningIpwEnabled = false;

    @Value("${app.recommendation.learning.ipw.max-weight:10}")
    private double learningIpwMaxWeight = 10;

    /** Observations needed before a suitability model may be activated. */
    @Value("${app.recommendation.learning.min-samples-a:2000}")
    private int learningMinSamplesA = 2000;

    /** Decisions needed before a willingness model may be activated. */
    @Value("${app.recommendation.learning.min-samples-b:300}")
    private int learningMinSamplesB = 300;

    /** The most recent days of data are held out of each update to compare the new weights with the prior. */
    @Value("${app.recommendation.learning.evaluation-days:7}")
    private int learningEvaluationDays = 7;

    /** Observations of a suitability model must also come from at least this many searches with an interaction. */
    @Value("${app.recommendation.learning.min-searches-a:300}")
    private int learningMinSearchesA = 300;

    /** A weight clamped this many nights in a row is reported (design 10, 10.4). */
    @Value("${app.recommendation.learning.clamp-warning-nights:3}")
    private int learningClampWarningNights = 3;

    /**
     * Folder where every learning run writes the observations it used, for the end-to-end check of design 13, 13.7
     * (step 43a). Empty (the default) writes nothing. The files hold user ids and what users did: set it only for
     * an evaluation database, never in the default or production configuration, and keep the folder out of Git.
     */
    @Value("${app.recommendation.learning.observation-dump-dir:}")
    private String learningObservationDumpDir = "";

    /** Activate a new version by itself once it qualifies; otherwise an admin does. */
    @Value("${app.recommendation.learning.auto-activate:true}")
    private boolean learningAutoActivate = true;

    /**
     * How much worse than the prior on the held-out data a new version may be and still be activated: NDCG@10
     * (suitability side) or AUC (willingness side) lower by at most this. 0.002 since 27/10/2026 (was 0): on the
     * end-to-end check the two were ~0.001 apart, so noise alone kept the willingness side at the prior although its
     * weights moved steadily to the hidden ones (design 13, 13.7). The safety band and the step limit still hold.
     */
    @Value("${app.recommendation.learning.activation-tolerance-ndcg:0.002}")
    private double learningActivationToleranceNdcg = 0.002;

    @Value("${app.recommendation.learning.activation-tolerance-auc:0.002}")
    private double learningActivationToleranceAuc = 0.002;

    /** The mode configured for a model. */
    public WeightLearningMode getLearningMode(WeightModel model) {
        return switch (model) {
            case A_S2T -> learningModeAS2T;
            case A_T2S -> learningModeAT2S;
            case B_S2T -> learningModeBS2T;
            case B_T2S -> learningModeBT2S;
        };
    }
}
