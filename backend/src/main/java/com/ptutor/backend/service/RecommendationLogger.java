package com.ptutor.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.MatchReasons;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.dto.response.RelaxationSuggestion;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.repository.RecommendationLogRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes one {@code recommendation_logs} row per search (design 08, 8.7). Every feature of every
 * returned candidate is stored, so later pages are served from the log, a ranking can be
 * explained, and weights can be learned from real data later without a schema change.
 */
@Component
@RequiredArgsConstructor
public class RecommendationLogger {

    private final RecommendationLogRepository logRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final EmbeddingTextBuilder textBuilder;

    public RecommendationLog logStudentSearch(
            Student student, QueryContext context, String queryText, List<ScoredCandidate> ranked,
            String algorithmVersion, long latencyMs, RerankService.Status rerankStatus,
            Map<UUID, MatchReasons> reasons, List<RelaxationSuggestion> relaxations) {
        String scrubbedQuery = textBuilder.scrub(queryText);
        ObjectNode storedContext = contextJson(context);
        storedContext.put("demandText", context.demandText());
        storedContext.put("rerankStatus", rerankStatus.name());
        return logRepository.save(RecommendationLog.builder()
                .searcherStudent(student)
                .sourceStudyingRequest(context.sourceStudyingRequestId() == null ? null
                        : entityManager.getReference(StudyingRequest.class, context.sourceStudyingRequestId()))
                .direction(RecommendationDirection.STUDENT_TO_TUTOR)
                .algorithmVersion(algorithmVersion)
                .queryText(scrubbedQuery.isEmpty() ? null : scrubbedQuery)
                .queryContext(storedContext)
                .returnedCandidateIds(candidatesJson(ranked, reasons))
                .relaxationSuggestions(relaxationsJson(relaxations))
                .latencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs))
                .build());
    }

    public RecommendationLog logTutorSearch(
            Tutor tutor, SupplyContext context, List<ScoredStudyingCandidate> ranked, String algorithmVersion,
            long latencyMs, RerankService.Status rerankStatus, Map<UUID, MatchReasons> reasons,
            List<RelaxationSuggestion> relaxations) {
        ObjectNode storedContext = contextJson(context);
        storedContext.put("supplyText", context.supplyText());
        storedContext.put("rerankStatus", rerankStatus.name());
        ArrayNode candidates = objectMapper.createArrayNode();
        int rank = 1;
        for (ScoredStudyingCandidate scored : ranked) {
            ObjectNode node = candidates.addObject();
            node.put("candidateId", scored.candidate().studyingRequestId().toString());
            node.put("candidateUserId", scored.candidate().studentUserId().toString());
            node.put("rank", rank++);
            node.put("score", round(scored.score()));
            node.put("a", round(scored.a()));
            putWillingness(node, scored.willingness());
            node.put("r", round(scored.r()));
            node.putNull("q");
            node.put("g", round(scored.g()));
            node.put("wHist", 0.0);
            node.put("confidence", round(scored.features().confidence()));
            putFeatures(node, scored.features());
            putFeature(node, "rerank", scored.features().rerank());
            node.put("isExploration", false);
            putReasons(node, reasons.get(scored.candidate().studyingRequestId()));
        }
        return logRepository.save(RecommendationLog.builder()
                .searcherTutor(tutor)
                .relaxationSuggestions(relaxationsJson(relaxations))
                .sourceTeachingRequest(context.sourceTeachingRequestId() == null ? null
                        : entityManager.getReference(TeachingRequest.class, context.sourceTeachingRequestId()))
                .direction(RecommendationDirection.TUTOR_TO_STUDENT)
                .algorithmVersion(algorithmVersion)
                .queryContext(storedContext)
                .returnedCandidateIds(candidates)
                .latencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs))
                .build());
    }

    /** The tutor's final criteria with the source of each one. */
    public ObjectNode contextJson(SupplyContext context) {
        ObjectNode json = objectMapper.createObjectNode();
        putText(json, "subjectId", context.subjectId());
        ArrayNode gradeIds = json.putArray("gradeIds");
        context.gradeIds().forEach(id -> gradeIds.add(id.toString()));
        json.put("learningMode", context.teachingMode().name());
        ObjectNode area = json.putObject("area");
        area.put("scope", context.areaScope().name());
        ArrayNode districtIds = area.putArray("districtIds");
        context.districtIds().forEach(id -> districtIds.add(id.toString()));
        putText(area, "provinceId", context.provinceId());
        json.put("expectedPrice", context.expectedPrice());
        ArrayNode availabilities = json.putArray("availabilities");
        context.availabilities().forEach(slot -> availabilities.addObject()
                .put("dayOfWeek", slot.dayOfWeek())
                .put("startTime", slot.startTime().toString())
                .put("endTime", slot.endTime().toString()));
        ArrayNode segments = json.putArray("targetSegmentCodes");
        context.targetSegmentCodes().stream().sorted().forEach(segments::add);
        putText(json, "useTeachingRequestId", context.sourceTeachingRequestId());
        ObjectNode sources = json.putObject("sources");
        context.sources().forEach((criterion, source) -> sources.put(criterion, source.name()));
        return json;
    }

    /** Final criteria with the source of each one; also what the API returns as {@code queryContext}. */
    public ObjectNode contextJson(QueryContext context) {
        ObjectNode json = objectMapper.createObjectNode();
        putText(json, "subjectId", context.subjectId());
        putText(json, "gradeId", context.gradeId());
        json.put("learningMode", context.learningMode().name());
        ObjectNode area = json.putObject("area");
        area.put("scope", context.areaScope().name());
        ArrayNode districtIds = area.putArray("districtIds");
        context.districtIds().forEach(id -> districtIds.add(id.toString()));
        putText(area, "provinceId", context.provinceId());
        ObjectNode price = json.putObject("price");
        price.put("min", context.minPrice());
        price.put("max", context.maxPrice());
        ArrayNode availabilities = json.putArray("availabilities");
        context.availabilities().forEach(slot -> availabilities.addObject()
                .put("dayOfWeek", slot.dayOfWeek())
                .put("startTime", slot.startTime().toString())
                .put("endTime", slot.endTime().toString()));
        ArrayNode needTags = json.putArray("needTagCodes");
        context.needTags().forEach(tag -> needTags.add(tag.code()));
        ArrayNode styleTags = json.putArray("styleTagCodes");
        context.styleTags().forEach(tag -> styleTags.add(tag.code()));
        json.put("levelTagCode", context.levelTagCode());
        json.put("semanticText", context.semanticText());
        putText(json, "useStudyingRequestId", context.sourceStudyingRequestId());
        ObjectNode sources = json.putObject("sources");
        context.sources().forEach((criterion, source) -> sources.put(criterion, source.name()));
        return json;
    }

    private JsonNode candidatesJson(List<ScoredCandidate> ranked, Map<UUID, MatchReasons> reasons) {
        ArrayNode array = objectMapper.createArrayNode();
        int rank = 1;
        for (ScoredCandidate scored : ranked) {
            FeatureVector features = scored.features();
            ObjectNode node = array.addObject();
            node.put("candidateId", scored.candidate().teachingRequestId().toString());
            node.put("candidateUserId", scored.candidate().tutorUserId().toString());
            node.put("rank", rank++);
            node.put("score", round(scored.score()));
            node.put("a", round(scored.a()));
            putWillingness(node, scored.willingness());
            node.put("r", round(scored.r()));
            node.put("q", round(scored.q()));
            node.put("g", round(scored.g()));
            node.put("wHist", 0.0);
            node.put("confidence", round(features.confidence()));
            putFeatures(node, features);
            putFeature(node, "rerank", features.rerank());
            node.put("isExploration", false);
            putReasons(node, reasons.get(scored.candidate().teachingRequestId()));
        }
        return array;
    }

    /** Stored with the candidate so that a later page shows the same sentences as the first one. */
    private void putReasons(ObjectNode node, MatchReasons reasons) {
        MatchReasons known = reasons == null ? MatchReasons.NONE : reasons;
        ArrayNode texts = node.putArray("reasons");
        known.reasons().forEach(texts::add);
        node.put("caution", known.caution());
    }

    /** What {@link #putReasons} stored; empty for logs written before reasons existed. */
    public MatchReasons reasons(JsonNode candidate) {
        List<String> texts = new ArrayList<>();
        candidate.path("reasons").forEach(text -> texts.add(text.asString()));
        JsonNode caution = candidate.path("caution");
        return new MatchReasons(texts, caution.isString() ? caution.asString() : null);
    }

    private JsonNode relaxationsJson(List<RelaxationSuggestion> relaxations) {
        ArrayNode array = objectMapper.createArrayNode();
        for (RelaxationSuggestion relaxation : relaxations) {
            ObjectNode node = array.addObject();
            node.put("code", relaxation.code());
            node.put("message", relaxation.message());
            node.put("gain", relaxation.gain());
            node.set("apply", relaxation.apply());
        }
        return array;
    }

    /** The suggestions of a stored search, for its later pages. */
    public List<RelaxationSuggestion> relaxations(JsonNode stored) {
        List<RelaxationSuggestion> relaxations = new ArrayList<>();
        if (stored != null && stored.isArray()) {
            stored.forEach(node -> relaxations.add(new RelaxationSuggestion(node.path("code").asString(),
                    node.path("message").asString(), node.path("gain").asInt(), node.path("apply"))));
        }
        return relaxations;
    }

    private void putWillingness(ObjectNode node, Willingness willingness) {
        if (willingness == null) {
            node.putNull("b");
            return;
        }
        node.put("b", round(willingness.b()));
        node.putObject("willingness")
                .put("hist", round(willingness.history()))
                .put("fit", round(willingness.fit()))
                .put("cap", round(willingness.capacity()))
                .put("invited", willingness.invited());
    }

    private void putFeatures(ObjectNode node, FeatureVector features) {
        ObjectNode featureNode = node.putObject("features");
        putFeature(featureNode, "sem", features.sem());
        putFeature(featureNode, "need", features.need());
        putFeature(featureNode, "style", features.style());
        putFeature(featureNode, "segment", features.segment());
        putFeature(featureNode, "price", features.price());
        putFeature(featureNode, "sched", features.sched());
        putFeature(featureNode, "area", features.area());
        featureNode.putNull("hist");
    }

    private void putFeature(ObjectNode node, String name, Double value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, round(value));
        }
    }

    private void putText(ObjectNode node, String name, Object value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value.toString());
        }
    }

    private double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
