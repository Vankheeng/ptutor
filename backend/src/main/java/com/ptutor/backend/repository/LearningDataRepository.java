package com.ptutor.backend.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.DecisionSampleKind;
import com.ptutor.backend.service.RecommendationSettings;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The data L1 learns from (design 12, sections 12.4 to 12.6).
 *
 * <ul>
 *   <li>Suitability side (a, a'): the searches of a period, each shown candidate with the features stored in the
 *       search log and the reward of what the searcher did with it within the reward window.</li>
 *   <li>Willingness side (b, b'): the snapshots taken when applications were created, labelled with the other
 *       side's answer, plus the positive samples of the side that reached out.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class LearningDataRepository {

    /** One shown candidate of a search: x in the order of the model's features, r and the sample weight s. */
    public record CandidateFeedback(UUID candidateId, int rank, double[] features, double reward, double weight) {
    }

    /** A search that had at least one interaction, with the candidates the searcher probably read. */
    public record SearchFeedback(UUID logId, LocalDateTime searchedAt, List<CandidateFeedback> candidates) {
    }

    /** A labelled willingness sample. {@code at} is when the label became known. */
    public record DecisionSample(UUID requestId, DecisionSampleKind kind, double[] features, double label,
            double weight, LocalDateTime at) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RecommendationSettings settings;

    /**
     * The searches of {@code model}'s direction made in {@code (from, to]}. Their rewards look at the reward window
     * after each search, so {@code to} should be at least that long ago (design 12, 12.7).
     */
    public List<SearchFeedback> searchFeedback(WeightModel model, LocalDateTime from, LocalDateTime to) {
        boolean studentSearch = switch (model) {
            case A_S2T -> true;
            case A_T2S -> false;
            default -> throw new IllegalArgumentException(model + " is not a suitability model");
        };
        Map<UUID, Search> searches = new HashMap<>();
        jdbc.query("""
                SELECT id, created_at, returned_candidate_ids::text AS candidates,
                       COALESCE(searcher_student_id, searcher_tutor_id) AS searcher
                FROM recommendation_logs
                WHERE direction = :direction AND deleted_at IS NULL
                  AND created_at > :from AND created_at <= :to
                """, new MapSqlParameterSource()
                        .addValue("direction", studentSearch ? "STUDENT_TO_TUTOR" : "TUTOR_TO_STUDENT")
                        .addValue("from", Timestamp.valueOf(from))
                        .addValue("to", Timestamp.valueOf(to)),
                row -> {
                    UUID id = row.getObject("id", UUID.class);
                    searches.put(id, new Search(id, row.getTimestamp("created_at").toLocalDateTime(),
                            row.getObject("searcher", UUID.class), objectMapper.readTree(row.getString("candidates")),
                            new HashMap<>()));
                });
        if (searches.isEmpty()) {
            return List.of();
        }
        Duration window = Duration.ofDays(settings.getLearningRewardWindowDays());

        // What the searcher did with each candidate, from the click log.
        jdbc.query("""
                SELECT c.recommendation_log_id, c.action, c.created_at,
                       COALESCE(c.candidate_teaching_request_id, c.candidate_studying_request_id) AS candidate
                FROM recommendation_click_logs c
                WHERE c.recommendation_log_id IN (:logIds) AND c.deleted_at IS NULL
                """, new MapSqlParameterSource("logIds", searches.keySet()), row -> {
            Search search = searches.get(row.getObject("recommendation_log_id", UUID.class));
            LocalDateTime at = row.getTimestamp("created_at").toLocalDateTime();
            UUID candidate = row.getObject("candidate", UUID.class);
            if (candidate != null && !at.isAfter(search.searchedAt().plus(window))) {
                search.signals().merge(candidate, signal(row.getString("action")), Signal::stronger);
            }
        });

        // Applications (proposals) the searcher sent to a candidate: not in the click log, read from the requests.
        String requests = studentSearch ? """
                SELECT r.student_id AS sender, r.teaching_request_id AS posting, r.created_at
                FROM student_tutor_requests r
                WHERE r.student_id IN (:searchers) AND r.deleted_at IS NULL
                  AND r.created_at > :from AND r.created_at <= :until
                """ : """
                SELECT r.tutor_id AS sender, r.studying_request_id AS posting, r.created_at
                FROM tutor_student_requests r
                WHERE r.tutor_id IN (:searchers) AND r.deleted_at IS NULL
                  AND r.created_at > :from AND r.created_at <= :until
                """;
        Map<UUID, List<Search>> bySearcher = new HashMap<>();
        searches.values().stream().filter(search -> search.searcher() != null)
                .forEach(search -> bySearcher.computeIfAbsent(search.searcher(), key -> new ArrayList<>()).add(search));
        if (!bySearcher.isEmpty()) {
            jdbc.query(requests, new MapSqlParameterSource()
                    .addValue("searchers", bySearcher.keySet())
                    .addValue("from", Timestamp.valueOf(from))
                    .addValue("until", Timestamp.valueOf(to.plus(window))), row -> {
                LocalDateTime at = row.getTimestamp("created_at").toLocalDateTime();
                UUID posting = row.getObject("posting", UUID.class);
                for (Search search : bySearcher.getOrDefault(row.getObject("sender", UUID.class), List.of())) {
                    if (!at.isBefore(search.searchedAt()) && !at.isAfter(search.searchedAt().plus(window))) {
                        search.signals().merge(posting, Signal.REQUEST, Signal::stronger);
                    }
                }
            });
        }

        List<SearchFeedback> feedback = new ArrayList<>();
        for (Search search : searches.values()) {
            SearchFeedback one = feedback(model, search);
            if (one != null) {
                feedback.add(one);
            }
        }
        feedback.sort((left, right) -> left.searchedAt().compareTo(right.searchedAt()));
        return feedback;
    }

    /**
     * The labelled samples of a willingness model whose label became known in {@code (from, to]}. A request
     * still waiting at {@code to} is an implicit rejection once it has waited long enough.
     */
    public List<DecisionSample> decisionSamples(WeightModel model, LocalDateTime from, LocalDateTime to) {
        // A student's application waits for the tutor (b); a tutor's proposal waits for the student (b').
        String decided = switch (model) {
            case B_S2T -> "STUDENT_TUTOR_REQUEST";
            case B_T2S -> "TUTOR_STUDENT_REQUEST";
            default -> throw new IllegalArgumentException(model + " is not a willingness model");
        };
        List<DecisionSample> samples = new ArrayList<>();
        jdbc.query("""
                SELECT s.sample_kind, s.request_type, s.request_id, s.features::text AS features,
                       s.created_at AS snapshot_at,
                       COALESCE(a.status, p.status) AS status,
                       COALESCE(a.created_at, p.created_at) AS requested_at,
                       COALESCE(a.responded_at, a.updated_at, p.responded_at, p.updated_at) AS decided_at,
                       COALESCE(a.updated_at, p.updated_at) AS updated_at,
                       COALESCE(tr.status, sr.status) AS posting_status
                FROM recommendation_decision_snapshots s
                LEFT JOIN student_tutor_requests a
                       ON s.request_type = 'STUDENT_TUTOR_REQUEST' AND a.id = s.request_id AND a.deleted_at IS NULL
                LEFT JOIN teaching_requests tr ON tr.id = a.teaching_request_id
                LEFT JOIN tutor_student_requests p
                       ON s.request_type = 'TUTOR_STUDENT_REQUEST' AND p.id = s.request_id AND p.deleted_at IS NULL
                LEFT JOIN studying_requests sr ON sr.id = p.studying_request_id
                WHERE s.model_key = :model
                """, new MapSqlParameterSource("model", model.name()), row -> {
            DecisionSampleKind kind = DecisionSampleKind.valueOf(row.getString("sample_kind"));
            String status = row.getString("status");
            if (status == null) {
                return; // The request was deleted.
            }
            double[] features = features(model, objectMapper.readTree(row.getString("features")));
            UUID requestId = row.getObject("request_id", UUID.class);
            DecisionSample sample;
            if (kind == DecisionSampleKind.INITIATIVE) {
                sample = new DecisionSample(requestId, kind, features, 1, settings.getLearningInitiativeWeight(),
                        row.getTimestamp("snapshot_at").toLocalDateTime());
            } else if (!decided.equals(row.getString("request_type"))) {
                return;
            } else {
                sample = labelled(requestId, features, status, row.getTimestamp("requested_at").toLocalDateTime(),
                        row.getTimestamp("decided_at").toLocalDateTime(),
                        row.getTimestamp("updated_at").toLocalDateTime(), row.getString("posting_status"), to);
            }
            if (sample != null && sample.at().isAfter(from) && !sample.at().isAfter(to)) {
                samples.add(sample);
            }
        });
        samples.sort((left, right) -> left.at().compareTo(right.at()));
        return samples;
    }

    /** The other side's answer to a request (design 12, 12.6), or null when it gives no label. */
    private DecisionSample labelled(UUID requestId, double[] features, String status, LocalDateTime requestedAt,
            LocalDateTime decidedAt, LocalDateTime updatedAt, String postingStatus, LocalDateTime now) {
        return switch (status) {
            case "ACCEPTED" -> new DecisionSample(requestId, DecisionSampleKind.DECISION, features, 1,
                    Duration.between(requestedAt, decidedAt).toHours() < settings.getLearningFastAcceptHours()
                            ? settings.getLearningFastAcceptWeight() : 1,
                    decidedAt);
            case "REJECTED" -> new DecisionSample(requestId, DecisionSampleKind.DECISION, features, 0, 1, decidedAt);
            case "PENDING" -> {
                // Never answered: an implicit rejection once it has waited long enough.
                LocalDateTime expired = requestedAt.plusDays(settings.getLearningImplicitRejectDays());
                yield expired.isAfter(now) ? null : new DecisionSample(requestId, DecisionSampleKind.DECISION,
                        features, 0, settings.getLearningImplicitRejectWeight(), expired);
            }
            // Cancelled because the posting went to someone else: an implicit rejection. Cancelled by the sender
            // while the posting was still open says nothing about the other side.
            case "CANCELLED" -> "MATCHED".equals(postingStatus)
                    ? new DecisionSample(requestId, DecisionSampleKind.DECISION, features, 0,
                            settings.getLearningImplicitRejectWeight(), updatedAt)
                    : null;
            default -> null;
        };
    }

    /**
     * Candidates down to the lowest position the searcher interacted with, plus a margin (design 12, 12.5); null
     * when the searcher did nothing, since then we do not know whether they read the list at all.
     */
    private SearchFeedback feedback(WeightModel model, Search search) {
        if (search.signals().isEmpty() || !search.candidates().isArray()) {
            return null;
        }
        int deepest = 0;
        for (JsonNode candidate : search.candidates()) {
            if (search.signals().containsKey(candidateId(candidate))) {
                deepest = Math.max(deepest, candidate.path("rank").asInt());
            }
        }
        if (deepest == 0) {
            return null; // Interactions with candidates the log does not keep.
        }
        int depth = deepest + settings.getLearningViewedDepthMargin();
        List<CandidateFeedback> candidates = new ArrayList<>();
        for (JsonNode candidate : search.candidates()) {
            int rank = candidate.path("rank").asInt();
            if (rank < 1 || rank > depth) {
                continue;
            }
            UUID id = candidateId(candidate);
            Signal signal = search.signals().get(id);
            double weight = candidate.path("isExploration").asBoolean() ? settings.getLearningExplorationSampleWeight() : 1;
            candidates.add(new CandidateFeedback(id, rank, features(model, candidate.path("features")),
                    signal == null ? 0 : reward(signal), weight));
        }
        candidates.sort((left, right) -> Integer.compare(left.rank(), right.rank()));
        return new SearchFeedback(search.logId(), search.searchedAt(), candidates);
    }

    /** x in the order of the model's features; a feature the log has no value for is neutral, as in a_base. */
    private double[] features(WeightModel model, JsonNode values) {
        List<String> names = model.features();
        double[] x = new double[names.size()];
        for (int i = 0; i < x.length; i++) {
            JsonNode value = values.path(names.get(i));
            x[i] = value.isNumber() ? value.asDouble() : settings.getConfidencePrior();
        }
        return x;
    }

    private double reward(Signal signal) {
        return switch (signal) {
            case VIEW -> settings.getLearningRewardView();
            case FAVORITE -> settings.getLearningRewardFavorite();
            case CONTACT -> settings.getLearningRewardContact();
            case REQUEST -> settings.getLearningRewardRequest();
            case DISMISS -> settings.getLearningRewardDismissed();
        };
    }

    private static UUID candidateId(JsonNode candidate) {
        String id = candidate.path("candidateId").asString();
        return id == null || id.isEmpty() ? null : UUID.fromString(id);
    }

    private static Signal signal(String action) {
        return switch (action) {
            case "FAVORITED" -> Signal.FAVORITE;
            case "CONTACTED" -> Signal.CONTACT;
            case "DISMISSED" -> Signal.DISMISS;
            default -> Signal.VIEW;
        };
    }

    /** Signals of a candidate, weakest first; any positive signal beats a dismissal (design 12, 12.4). */
    private enum Signal {
        DISMISS, VIEW, FAVORITE, CONTACT, REQUEST;

        static Signal stronger(Signal left, Signal right) {
            return left.ordinal() >= right.ordinal() ? left : right;
        }
    }

    private record Search(UUID logId, LocalDateTime searchedAt, UUID searcher, JsonNode candidates,
            Map<UUID, Signal> signals) {
    }
}
