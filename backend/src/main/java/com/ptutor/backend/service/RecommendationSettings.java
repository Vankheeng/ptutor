package com.ptutor.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    /** Off: the order of phase 3 is final (V2-RC). On: the best postings are reranked (V2-RCX). */
    @Value("${app.recommendation.rerank.enabled:true}")
    private boolean rerankEnabled = true;

    @Value("${app.recommendation.rerank.top-k:20}")
    private int rerankTopK = 20;

    /** How long a search may wait for pairs that are not cached yet; what is scored by then is used. */
    @Value("${app.recommendation.rerank.timeout-ms:4000}")
    private long rerankTimeoutMs = 4000;

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
}
