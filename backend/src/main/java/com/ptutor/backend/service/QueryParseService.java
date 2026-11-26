package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.ParseQueryCatalog;
import com.ptutor.backend.client.AiServiceClient.ParseQueryRequest;
import com.ptutor.backend.client.AiServiceClient.VocabularyTag;
import com.ptutor.backend.dto.response.QueryParseResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.SubjectRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A2 (design 05, 5.4): turns a sentence typed by the user into values for the filter form. The LLM
 * only proposes names and codes from the lists it is given; this class checks every one of them
 * against the catalogue and turns them into IDs. The user reviews the form before searching, so a
 * wrong guess costs a click, and a failure costs nothing. The sentence itself (without contact
 * details) is always returned as the free-text part of the search.
 */
@Service
public class QueryParseService {

    static final String NOT_UNDERSTOOD = "Chưa phân tích được câu; cả câu được dùng làm mô tả nhu cầu.";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final AiServiceClient aiServiceClient;
    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final DistrictRepository districtRepository;
    private final RecommendationTagRepository tagRepository;
    private final EmbeddingTextBuilder textBuilder;
    private final RecommendationSettings settings;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Map<String, Cached> cache;

    public QueryParseService(
            AiServiceClient aiServiceClient,
            SubjectRepository subjectRepository,
            GradeRepository gradeRepository,
            DistrictRepository districtRepository,
            RecommendationTagRepository tagRepository,
            EmbeddingTextBuilder textBuilder,
            RecommendationSettings settings,
            EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.aiServiceClient = aiServiceClient;
        this.subjectRepository = subjectRepository;
        this.gradeRepository = gradeRepository;
        this.districtRepository = districtRepository;
        this.tagRepository = tagRepository;
        this.textBuilder = textBuilder;
        this.settings = settings;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        int capacity = Math.max(1, settings.getParseQueryCacheSize());
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > capacity;
            }
        });
    }

    private record Cached(QueryParseResponse response, Instant storedAt) {
    }

    /** Never fails because of the LLM: without an answer the whole sentence becomes the semantic text. */
    @Transactional(readOnly = true)
    public QueryParseResponse parse(UUID userId, String text, RecommendationDirection direction) {
        String sentence = textBuilder.scrub(text);
        if (sentence.isEmpty() || !settings.isParseQueryEnabled()) {
            return notUnderstood(sentence);
        }
        UUID provinceId = provinceOf(userId);
        String key = key(provinceId, sentence, direction);
        Cached cached = cache.get(key);
        if (cached != null && cached.storedAt().plus(Duration.ofHours(settings.getParseQueryCacheHours()))
                .isAfter(Instant.now())) {
            return cached.response();
        }

        List<Subject> subjects = subjectRepository.findAllByStatusOrderByNameAsc(CatalogStatus.ACTIVE);
        List<Grade> grades = gradeRepository.findAllByStatusOrderByLevelAsc(CatalogStatus.ACTIVE);
        List<District> nearby = provinceId == null ? List.of() : districtRepository.findAllByProvinceIdOrdered(provinceId);
        List<RecommendationTag> tags =
                tagRepository.findAllByStatusOrderByTagGroupAscCodeAsc(RecommendationTagStatus.ACTIVE);
        Map<String, List<VocabularyTag>> vocabulary = new LinkedHashMap<>();
        for (RecommendationTag tag : tags) {
            String name = tag.getTagGroup() == RecommendationTagGroup.TOPIC && tag.getSubject() != null
                    ? tag.getName() + " (" + tag.getSubject().getName() + ")" : tag.getName();
            vocabulary.computeIfAbsent(tag.getTagGroup().name(), group -> new ArrayList<>())
                    .add(new VocabularyTag(tag.getCode(), name));
        }
        Optional<JsonNode> answer = aiServiceClient.parseQuery(
                new ParseQueryRequest(sentence, direction.name(),
                        new ParseQueryCatalog(subjects.stream().map(Subject::getName).toList(),
                                grades.stream().map(Grade::getName).toList(),
                                nearby.stream().map(District::getName).toList()),
                        vocabulary),
                Duration.ofMillis(settings.getParseQueryTimeoutMs()));
        if (answer.isEmpty()) {
            return notUnderstood(sentence);
        }
        QueryParseResponse response = map(answer.get(), sentence, direction, provinceId, subjects, grades, nearby, tags);
        cache.put(key, new Cached(response, Instant.now()));
        return response;
    }

    /** What an earlier call understood from this sentence, for the search log (design 05, 5.7). */
    public Optional<JsonNode> recall(UUID provinceId, String text, RecommendationDirection direction) {
        String sentence = textBuilder.scrub(text);
        Cached cached = sentence.isEmpty() ? null : cache.get(key(provinceId, sentence, direction));
        return cached == null ? Optional.empty() : Optional.of(objectMapper.valueToTree(cached.response()));
    }

    // ---------------------------------------------------------------- names and codes -> IDs

    private QueryParseResponse map(JsonNode answer, String sentence, RecommendationDirection direction, UUID provinceId,
            List<Subject> subjects, List<Grade> grades, List<District> nearby, List<RecommendationTag> tags) {
        boolean studentSearch = direction == RecommendationDirection.STUDENT_TO_TUTOR;
        ObjectNode filters = objectMapper.createObjectNode();
        Set<String> notes = new LinkedHashSet<>();
        List<String> unparsed = new ArrayList<>();
        answer.path("unparsed").forEach(item -> unparsed.add(item.asString("")));

        Subject subject = answer.path("subject").isString()
                ? find(subjects, Subject::getName, answer.path("subject").asString()) : null;
        if (subject != null) {
            filters.put("subjectId", subject.getId().toString());
        }
        List<Grade> foundGrades = new ArrayList<>();
        answer.path("grades").forEach(name -> {
            Grade grade = find(grades, Grade::getName, name.asString(""));
            if (grade != null && !foundGrades.contains(grade)) {
                foundGrades.add(grade);
            }
        });
        if (studentSearch && !foundGrades.isEmpty()) {
            filters.put("gradeId", foundGrades.get(0).getId().toString());
            if (foundGrades.size() > 1) {
                notes.add("Câu nêu nhiều lớp; đã chọn " + foundGrades.get(0).getName() + ".");
            }
        } else if (!foundGrades.isEmpty()) {
            ArrayNode gradeIds = filters.putArray("gradeIds");
            foundGrades.forEach(grade -> gradeIds.add(grade.getId().toString()));
        }

        String mode = answer.path("mode").asString("");
        if (mode.equals("ONLINE") || mode.equals("OFFLINE")) {
            filters.put("learningMode", mode);
        }

        // Districts: the user's province first, then the whole country; names the model could not place too.
        List<String> districtNames = new ArrayList<>();
        answer.path("area").path("districts").forEach(name -> districtNames.add(name.asString("")));
        List<District> everywhere = null;
        List<UUID> districtIds = new ArrayList<>();
        for (String name : new ArrayList<>(districtNames)) {
            District district = find(nearby, District::getName, name);
            if (district == null) {
                everywhere = everywhere == null ? districtRepository.findAllOrdered() : everywhere;
                district = findUnique(everywhere, name);
            }
            if (district == null) {
                notes.add("Không tìm thấy quận / huyện \"" + name + "\".");
            } else if (!districtIds.contains(district.getId())) {
                districtIds.add(district.getId());
            }
        }
        for (String item : new ArrayList<>(unparsed)) {
            everywhere = everywhere == null ? districtRepository.findAllOrdered() : everywhere;
            District district = findUnique(everywhere, item);
            if (district != null) {
                unparsed.remove(item);
                if (!districtIds.contains(district.getId())) {
                    districtIds.add(district.getId());
                }
            }
        }
        String scope = answer.path("area").path("scope").asString("");
        if (!districtIds.isEmpty()) {
            ObjectNode area = filters.putObject("area");
            area.put("scope", "DISTRICTS");
            ArrayNode ids = area.putArray("districtIds");
            districtIds.forEach(id -> ids.add(id.toString()));
        } else if (scope.equals("PROVINCE") && provinceId != null) {
            filters.putObject("area").put("scope", "PROVINCE").put("provinceId", provinceId.toString());
        } else if (scope.equals("ANY")) {
            filters.putObject("area").put("scope", "ANY");
        }

        BigDecimal min = amount(answer.path("price").path("min"));
        BigDecimal max = amount(answer.path("price").path("max"));
        if (studentSearch && (min != null || max != null)) {
            ObjectNode price = filters.putObject("price");
            price.put("min", min);
            price.put("max", max);
        } else if (!studentSearch && (min != null || max != null)) {
            // A tutor states one hoped-for price: the stated floor, or the middle of a range.
            BigDecimal expected = min != null && max != null
                    ? min.add(max).divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                            .divide(THOUSAND, 0, RoundingMode.HALF_UP).multiply(THOUSAND)
                    : min != null ? min : max;
            filters.put("expectedPrice", expected);
        }

        ArrayNode slots = objectMapper.createArrayNode();
        for (JsonNode slot : answer.path("schedule")) {
            try {
                int day = slot.path("dayOfWeek").asInt(0);
                LocalTime start = LocalTime.parse(slot.path("start").asString(""));
                LocalTime end = LocalTime.parse(slot.path("end").asString(""));
                // The same sanity window as schedules inferred from postings (design 04, 4.6).
                if (day >= 1 && day <= 7 && start.isBefore(end) && !start.isBefore(EnrichmentJobProcessor.EARLIEST)
                        && !end.isAfter(EnrichmentJobProcessor.LATEST)) {
                    slots.addObject().put("dayOfWeek", day).put("startTime", start.toString())
                            .put("endTime", end.toString());
                }
            } catch (RuntimeException invalidTime) {
                // An unusable slot is simply not filled in.
            }
        }
        if (!slots.isEmpty()) {
            filters.set("availabilities", slots);
        }

        if (studentSearch) {
            putCodes(filters, "styleTagCodes", codes(answer.path("styleTags"), tags, RecommendationTagGroup.STYLE, null));
            putCodes(filters, "goalTagCodes", codes(answer.path("goalTags"), tags, RecommendationTagGroup.GOAL, null));
            putCodes(filters, "topicTagCodes", subject == null ? List.of()
                    : codes(answer.path("topicTags"), tags, RecommendationTagGroup.TOPIC, subject.getId()));
        } else {
            putCodes(filters, "targetSegmentCodes",
                    codes(answer.path("segmentTags"), tags, RecommendationTagGroup.SEGMENT, null));
        }

        unparsed.stream().filter(item -> !item.isBlank())
                .forEach(item -> notes.add("Chưa nhận ra: \"" + item.strip() + "\"."));
        // The free text is the user's own sentence, not a rewording by the model (decision of 13/10/2026):
        // when the model was asked for "what fits no filter" it sometimes added ideas nobody wrote, such as a
        // preferred gender read into "cô" / "thầy". The price is that words already covered by a filter (price,
        // district, schedule) also reach the query vector. TO WATCH: if the evaluation (design 09) shows that
        // this hurts the semantic feature, extract the remainder again - with a check against the sentence.
        return new QueryParseResponse(filters, sentence, List.copyOf(notes), true);
    }

    private QueryParseResponse notUnderstood(String sentence) {
        return new QueryParseResponse(objectMapper.createObjectNode(), sentence.isEmpty() ? null : sentence,
                sentence.isEmpty() ? List.of() : List.of(NOT_UNDERSTOOD), false);
    }

    private List<String> codes(
            JsonNode values, List<RecommendationTag> tags, RecommendationTagGroup group, UUID subjectId) {
        List<String> codes = new ArrayList<>();
        for (JsonNode value : values) {
            String code = value.asString("");
            boolean known = tags.stream().anyMatch(tag -> tag.getTagGroup() == group && tag.getCode().equals(code)
                    && (subjectId == null || (tag.getSubject() != null && tag.getSubject().getId().equals(subjectId))));
            if (known && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    private void putCodes(ObjectNode filters, String field, List<String> codes) {
        if (!codes.isEmpty()) {
            ArrayNode array = filters.putArray(field);
            codes.forEach(array::add);
        }
    }

    private BigDecimal amount(JsonNode value) {
        return value.isNumber() && value.asLong() > 0 ? BigDecimal.valueOf(value.asLong()) : null;
    }

    private <T> T find(List<T> items, java.util.function.Function<T, String> name, String wanted) {
        String normalized = normalize(wanted);
        if (normalized.isEmpty()) {
            return null;
        }
        return items.stream().filter(item -> normalize(name.apply(item)).equals(normalized)).findFirst().orElse(null);
    }

    /** Outside the user's province a name must be unambiguous: several provinces have a "Châu Thành". */
    private District findUnique(List<District> districts, String wanted) {
        String normalized = normalize(wanted);
        if (normalized.isEmpty()) {
            return null;
        }
        List<District> matches = districts.stream()
                .filter(district -> normalize(district.getName()).equals(normalized)).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** Lower case, no accents, no administrative prefix: "Quận Cầu Giấy" and "cau giay" are the same place. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value.strip().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("\\s+", " ");
        return plain.replaceFirst("^(quan|huyen|thi xa|thanh pho|tp\\.?|tx\\.?|q\\.) ", "").strip();
    }

    private String key(UUID provinceId, String sentence, RecommendationDirection direction) {
        return textBuilder.hash(normalize(sentence) + "|" + direction) + ":" + provinceId;
    }

    private UUID provinceOf(UUID userId) {
        User user = userId == null ? null : entityManager.find(User.class, userId);
        return user == null || user.getDistrict() == null || user.getDistrict().getProvince() == null
                ? null : user.getDistrict().getProvince().getId();
    }
}
