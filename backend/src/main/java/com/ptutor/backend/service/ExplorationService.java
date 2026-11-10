package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ptutor.backend.entity.RecommendationExposureStats;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.repository.RecommendationExposureStatsRepository;

/**
 * Phase 5 (design 06, 6.11): two places of the first page go to good postings that were rarely
 * shown, so that new postings get a chance and the logs contain data that is not only "what the
 * ranking already liked". Which posting gets a place is drawn by Thompson sampling: one that was
 * shown often and seldom opened is drawn less and less.
 */
@Service
public class ExplorationService {

    private final RecommendationExposureStatsRepository statsRepository;
    private final RecommendationSettings settings;
    private final RandomGenerator random;

    @Autowired
    public ExplorationService(RecommendationExposureStatsRepository statsRepository, RecommendationSettings settings) {
        this(statsRepository, settings, new java.util.Random());
    }

    ExplorationService(
            RecommendationExposureStatsRepository statsRepository, RecommendationSettings settings,
            RandomGenerator random) {
        this.statsRepository = statsRepository;
        this.settings = settings;
        this.random = random;
    }

    /** @param explored the results that were moved up into an exploration slot */
    public record Result<S>(List<S> ranked, Set<UUID> explored) {
    }

    /**
     * @param ranked final ranking, best first, one result per person
     * @param id     the posting behind a result
     */
    public <S> Result<S> apply(
            List<S> ranked, RecommendationCandidateType type, Function<S, UUID> id, ToDoubleFunction<S> score) {
        int pageSize = settings.getExplorationPageSize();
        if (!settings.isExplorationEnabled() || pageSize < 1 || ranked.size() <= pageSize + 5) {
            return new Result<>(ranked, Set.of());
        }
        double minimum = settings.getExplorationMinScoreRatio() * score.applyAsDouble(ranked.get(pageSize - 1));
        List<S> eligible = ranked.subList(pageSize, ranked.size()).stream()
                .filter(result -> score.applyAsDouble(result) >= minimum).toList();
        if (eligible.isEmpty()) {
            return new Result<>(ranked, Set.of());
        }

        Map<UUID, RecommendationExposureStats> stats = new HashMap<>();
        statsRepository.findAllOf(type, ranked.stream().map(id).toList())
                .forEach(row -> stats.put(row.getId().getCandidateId(), row));
        // A posting never shown has no row: it counts as zero impressions and zero clicks.
        List<Integer> shown = ranked.stream().map(result -> impressions7d(stats.get(id.apply(result)))).sorted().toList();
        int median = shown.get(shown.size() / 2);
        // "At most the median" rather than "below": while most postings were never shown the median is 0.
        List<S> pool = new ArrayList<>(eligible.stream()
                .filter(result -> impressions7d(stats.get(id.apply(result))) <= median).toList());

        List<S> page = new ArrayList<>(ranked);
        Set<UUID> explored = new HashSet<>();
        for (int slot : settings.getExplorationSlots()) {
            if (pool.isEmpty() || slot < 1 || slot > pageSize) {
                continue;
            }
            S chosen = null;
            double best = -1;
            for (S candidate : pool) {
                RecommendationExposureStats row = stats.get(id.apply(candidate));
                int clicks = row == null ? 0 : Math.max(0, row.getClicksTotal());
                int impressions = row == null ? 0 : Math.max(clicks, row.getImpressionsTotal());
                double value = beta(1 + clicks, 1 + impressions - clicks) * score.applyAsDouble(candidate);
                if (value > best) {
                    best = value;
                    chosen = candidate;
                }
            }
            pool.remove(chosen);
            page.remove(chosen);
            page.add(slot - 1, chosen);
            explored.add(id.apply(chosen));
        }
        return new Result<>(page, explored);
    }

    private int impressions7d(RecommendationExposureStats row) {
        return row == null || row.getImpressions7d() == null ? 0 : row.getImpressions7d();
    }

    /** A draw from Beta(a, b) for a, b >= 1, as X / (X + Y) with X ~ Gamma(a) and Y ~ Gamma(b). */
    double beta(double a, double b) {
        double x = gamma(a);
        double y = gamma(b);
        return x + y == 0 ? 0.5 : x / (x + y);
    }

    /** Marsaglia and Tsang's method, valid for shape >= 1. */
    private double gamma(double shape) {
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = random.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0) {
                continue;
            }
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v;
            }
        }
    }
}
