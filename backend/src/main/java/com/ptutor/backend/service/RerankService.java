package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.RerankResult;
import com.ptutor.backend.entity.RecommendationRerankCache;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationRerankCacheRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Phase 4 of the ranking (design 06, 6.9): a cross-encoder reads the searcher's text together with
 * the text of each of the best postings and says how relevant the posting is. Scores are cached by
 * (text of the searcher, posting, content of the posting, model), so a pair is computed once.
 *
 * <p>The model is slow on a CPU, so the pairs are sent a few at a time, best ranked first, until the
 * time budget is used up. Whatever was scored in time is used and cached; the next search with the
 * same criteria continues from there. Reranking never makes a search fail.
 */
@Service
@RequiredArgsConstructor
public class RerankService {

    /** The AI service refuses longer texts; it only reads the first few hundred tokens anyway. */
    static final int MAX_TEXT_CHARS = 8000;

    public enum Status {
        /** Every pair of the top group was already in the cache. */
        CACHE,
        /** At least one pair was computed for this call and the whole top group has a score. */
        ONLINE,
        /** Time ran out or the AI service failed: only part of the top group (or none) has a score. */
        TIMEOUT,
        DISABLED
    }

    /** @param computed how many pairs were scored by the model during this call */
    public record Result(Map<UUID, Double> scores, Status status, int computed) {
    }

    private final AiServiceClient aiServiceClient;
    private final RecommendationRerankCacheRepository cacheRepository;
    private final RecommendationEntityTagRepository entityTagRepository;
    private final EmbeddingTextBuilder textBuilder;
    private final EntityManager entityManager;
    private final RecommendationSettings settings;

    /** For a search: the best {@code rerank.top-k} postings within {@code rerank.timeout-ms}. */
    public Result forSearch(String queryText, RecommendationCandidateType type, List<UUID> rankedCandidateIds) {
        return rerank(queryText, type, rankedCandidateIds, settings.getRerankTopK(),
                Duration.ofMillis(settings.getRerankTimeoutMs()));
    }

    /** For the nightly job: a few more postings than a search uses, with a generous time budget. */
    public Result prefetch(String queryText, RecommendationCandidateType type, List<UUID> rankedCandidateIds) {
        return rerank(queryText, type, rankedCandidateIds, settings.getRerankPrefetchTopK(),
                Duration.ofMillis(settings.getRerankPrefetchTimeoutMs()));
    }

    public int deleteOlderThan(LocalDateTime threshold) {
        return cacheRepository.deleteByComputedAtBefore(threshold);
    }

    Result rerank(String queryText, RecommendationCandidateType type, List<UUID> rankedCandidateIds, int topK,
            Duration budget) {
        if (!settings.isRerankEnabled() || queryText == null || queryText.isBlank()) {
            return new Result(Map.of(), Status.DISABLED, 0);
        }
        long deadline = System.nanoTime() + budget.toNanos();
        String query = limit(queryText.strip());
        String queryHash = textBuilder.hash(query);
        String model = settings.getRerankModelVersion();

        Map<UUID, String> texts = new LinkedHashMap<>();
        for (UUID id : rankedCandidateIds.stream().limit(Math.max(0, topK)).toList()) {
            text(type, id).filter(text -> !text.isBlank()).ifPresent(text -> texts.put(id, limit(text)));
        }
        Map<UUID, String> contentHashes = new LinkedHashMap<>();
        texts.forEach((id, text) -> contentHashes.put(id, textBuilder.hash(text)));

        Map<UUID, Double> scores = new LinkedHashMap<>();
        if (!texts.isEmpty()) {
            for (RecommendationRerankCache row : cacheRepository.findScores(queryHash, type, model, texts.keySet())) {
                UUID id = row.getId().getCandidateId();
                // A posting edited since it was scored has another content hash: the old score is stale.
                if (row.getCandidateContentHash().equals(contentHashes.get(id))) {
                    scores.put(id, row.getRerankScore().doubleValue());
                }
            }
        }
        List<UUID> missing = texts.keySet().stream().filter(id -> !scores.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return new Result(scores, Status.CACHE, 0);
        }

        int computed = 0;
        int chunkSize = Math.max(1, settings.getRerankChunkSize());
        for (int start = 0; start < missing.size(); start += chunkSize) {
            long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
            if (remainingMs <= 0) {
                break;
            }
            List<UUID> chunk = missing.subList(start, Math.min(start + chunkSize, missing.size()));
            Optional<RerankResult> answer = aiServiceClient.rerank(
                    query, chunk.stream().map(texts::get).toList(), Duration.ofMillis(remainingMs));
            if (answer.isEmpty()) {
                break;
            }
            LocalDateTime now = LocalDateTime.now();
            for (int index = 0; index < chunk.size(); index++) {
                UUID id = chunk.get(index);
                // Rounded like the stored column, so a cached score and a fresh one rank identically.
                BigDecimal score = BigDecimal.valueOf(answer.get().scores().get(index))
                        .setScale(4, RoundingMode.HALF_UP);
                cacheRepository.upsert(queryHash, type.name(), id, contentHashes.get(id), answer.get().model(),
                        score, now);
                scores.put(id, score.doubleValue());
                computed++;
            }
        }
        return new Result(scores, scores.size() == texts.size() ? Status.ONLINE : Status.TIMEOUT, computed);
    }

    /** The same text the posting was embedded from, so both models judge the same content. */
    private Optional<String> text(RecommendationCandidateType type, UUID id) {
        return switch (type) {
            case TEACHING_REQUEST -> Optional.ofNullable(entityManager.find(TeachingRequest.class, id))
                    .map(request -> textBuilder.forTeachingRequest(request, tagNames(
                            RecommendationEntityType.TEACHING_REQUEST, id, RecommendationEntityType.TUTOR,
                            request.getTutor() == null ? null : request.getTutor().getId())));
            case STUDYING_REQUEST -> Optional.ofNullable(entityManager.find(StudyingRequest.class, id))
                    .map(request -> textBuilder.forStudyingRequest(request, tagNames(
                            RecommendationEntityType.STUDYING_REQUEST, id, RecommendationEntityType.STUDENT,
                            request.getStudent() == null ? null : request.getStudent().getId())));
        };
    }

    private List<String> tagNames(
            RecommendationEntityType type, UUID id, RecommendationEntityType ownerType, UUID ownerId) {
        List<String> names = new ArrayList<>();
        entityTagRepository.findAllByEntityTypeAndEntityId(type, id)
                .forEach(tag -> names.add(tag.getTag().getName()));
        if (ownerId != null) {
            entityTagRepository.findAllByEntityTypeAndEntityId(ownerType, ownerId)
                    .forEach(tag -> names.add(tag.getTag().getName()));
        }
        return names;
    }

    private String limit(String text) {
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }
}
