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

    @Value("${app.recommendation.algorithm-version:v2.1-a}")
    private String algorithmVersion = "v2.1-a";

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

    @Value("${app.recommendation.query-embedding.timeout-ms:1000}")
    private long queryEmbeddingTimeoutMs = 1000;

    @Value("${app.recommendation.query-embedding.cache-size:500}")
    private int queryEmbeddingCacheSize = 500;
}
