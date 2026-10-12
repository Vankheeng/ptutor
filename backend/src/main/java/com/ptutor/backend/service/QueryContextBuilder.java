package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.CriterionSource;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Area;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Filters;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.RecommendationEntityTag;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagRole;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.SubjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Merges a student's search into one {@link QueryContext} (design 05, 5.5). For each criterion
 * the first source that has a value wins: the filter, then the student's own studying posting
 * (only if they asked to use it), then their profile.
 */
@Component
@RequiredArgsConstructor
public class QueryContextBuilder {

    private static final Set<RecommendationTagRole> NEED_ROLES =
            Set.of(RecommendationTagRole.NEED, RecommendationTagRole.SELF);

    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final ProvinceRepository provinceRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final RecommendationTagRepository tagRepository;
    private final RecommendationEntityTagRepository entityTagRepository;
    private final EmbeddingTextBuilder textBuilder;

    /** {@code student} must have its user and home district loaded. Call inside a transaction. */
    public QueryContext forStudent(Student student, TutorRecommendationRequest request) {
        Filters filters = request.filters() == null
                ? new Filters(null, null, null, null, null, null, null, null, null) : request.filters();
        Map<String, CriterionSource> sources = new LinkedHashMap<>();
        StudyingRequest own = request.useStudyingRequestId() == null ? null : studyingRequestRepository
                .findByIdAndStudent_Id(request.useStudyingRequestId(), student.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDYING_REQUEST_NOT_FOUND",
                        "Studying request not found: " + request.useStudyingRequestId()));
        List<RecommendationEntityTag> profileTags = entityTagRepository
                .findAllByEntityTypeAndEntityId(RecommendationEntityType.STUDENT, student.getId());
        List<RecommendationEntityTag> postingTags = own == null ? List.of() : entityTagRepository
                .findAllByEntityTypeAndEntityId(RecommendationEntityType.STUDYING_REQUEST, own.getId());

        Subject subject = subject(filters, own, sources);
        Grade grade = grade(filters, own, sources);
        ModePreference mode = mode(filters, own, sources);
        District home = student.getUser() == null ? null : student.getUser().getDistrict();
        UUID homeDistrictId = home == null ? null : home.getId();
        UUID homeProvinceId = home == null || home.getProvince() == null ? null : home.getProvince().getId();
        ResolvedArea area = area(filters.area(), mode, own, homeDistrictId, homeProvinceId, sources);
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        if (filters.price() != null && (filters.price().min() != null || filters.price().max() != null)) {
            minPrice = filters.price().min();
            maxPrice = filters.price().max();
            sources.put("price", CriterionSource.FILTER);
        } else if (own != null && (own.getMinPrice() != null || own.getMaxPrice() != null)) {
            minPrice = own.getMinPrice();
            maxPrice = own.getMaxPrice();
            sources.put("price", CriterionSource.STUDYING_REQUEST);
        }
        List<TimeSlot> availabilities = availabilities(filters, own, sources);

        UUID subjectId = subject == null ? null : subject.getId();
        List<RecommendationTag> goalTags = chosenTags(RecommendationTagGroup.GOAL, filters.goalTagCodes(), null);
        List<RecommendationTag> topicTags = chosenTags(RecommendationTagGroup.TOPIC, filters.topicTagCodes(), subjectId);
        List<WeightedTag> needTags;
        List<String> needNames;
        if (filters.goalTagCodes() != null || filters.topicTagCodes() != null) {
            List<RecommendationTag> chosen = new ArrayList<>(goalTags);
            chosen.addAll(topicTags);
            needTags = chosen.stream().map(tag -> weighted(tag, 1.0)).toList();
            needNames = chosen.stream().map(RecommendationTag::getName).toList();
            sources.put("needTags", CriterionSource.FILTER);
        } else {
            List<RecommendationEntityTag> derived = derivedNeedTags(postingTags, subjectId);
            CriterionSource source = CriterionSource.STUDYING_REQUEST;
            if (derived.isEmpty()) {
                derived = derivedNeedTags(profileTags, subjectId);
                source = CriterionSource.PROFILE;
            }
            needTags = derived.stream()
                    .map(tag -> weighted(tag.getTag(), tag.getConfidence().doubleValue())).toList();
            needNames = derived.stream().map(tag -> tag.getTag().getName()).toList();
            if (!derived.isEmpty()) {
                sources.put("needTags", source);
            }
        }

        List<WeightedTag> styleTags;
        List<String> styleNames;
        if (filters.styleTagCodes() != null) {
            List<RecommendationTag> chosen = chosenTags(RecommendationTagGroup.STYLE, filters.styleTagCodes(), null);
            styleTags = chosen.stream().map(tag -> weighted(tag, 1.0)).toList();
            styleNames = chosen.stream().map(RecommendationTag::getName).toList();
            sources.put("styleTags", CriterionSource.FILTER);
        } else {
            List<RecommendationEntityTag> derived = derivedStyleTags(postingTags);
            CriterionSource source = CriterionSource.STUDYING_REQUEST;
            if (derived.isEmpty()) {
                derived = derivedStyleTags(profileTags);
                source = CriterionSource.PROFILE;
            }
            styleTags = derived.stream()
                    .map(tag -> weighted(tag.getTag(), tag.getConfidence().doubleValue())).toList();
            styleNames = derived.stream().map(tag -> tag.getTag().getName()).toList();
            if (!derived.isEmpty()) {
                sources.put("styleTags", source);
            }
        }

        RecommendationTag level = profileTags.stream()
                .filter(tag -> tag.getRole() == RecommendationTagRole.SELF
                        && tag.getTag().getTagGroup() == RecommendationTagGroup.SEGMENT)
                .map(RecommendationEntityTag::getTag)
                .findFirst().orElse(null);
        if (level != null) {
            sources.put("level", CriterionSource.PROFILE);
        }

        List<String> needsInput = new ArrayList<>();
        if (subject == null) {
            needsInput.add("subjectId");
        }
        if (grade == null) {
            needsInput.add("gradeId");
        }
        String semanticText = textBuilder.scrub(request.semanticText());
        String demandText = demandText(subject, grade, needTags, needNames, styleNames, level, semanticText,
                student, own);

        return new QueryContext(
                student.getId(), student.getUser() == null ? null : student.getUser().getId(),
                subjectId, grade == null ? null : grade.getId(), grade == null ? null : grade.getLevel(),
                mode, area.scope(), area.districtIds(), area.provinceId(), homeDistrictId, homeProvinceId,
                minPrice, maxPrice, availabilities, needTags, styleTags,
                level == null ? null : level.getCode(),
                semanticText.isEmpty() ? null : semanticText, demandText,
                own == null ? null : own.getId(), Map.copyOf(sources), List.copyOf(needsInput));
    }

    private Subject subject(Filters filters, StudyingRequest own, Map<String, CriterionSource> sources) {
        if (filters.subjectId() != null) {
            sources.put("subject", CriterionSource.FILTER);
            return subjectRepository.findById(filters.subjectId())
                    .filter(subject -> subject.getStatus() == CatalogStatus.ACTIVE)
                    .orElseThrow(() -> badRequest("INVALID_SUBJECT", "Subject not found or inactive"));
        }
        if (own != null && own.getSubject() != null) {
            sources.put("subject", CriterionSource.STUDYING_REQUEST);
            return own.getSubject();
        }
        return null;
    }

    private Grade grade(Filters filters, StudyingRequest own, Map<String, CriterionSource> sources) {
        if (filters.gradeId() != null) {
            sources.put("grade", CriterionSource.FILTER);
            return gradeRepository.findById(filters.gradeId())
                    .filter(grade -> grade.getStatus() == CatalogStatus.ACTIVE)
                    .orElseThrow(() -> badRequest("INVALID_GRADE", "Grade not found or inactive"));
        }
        if (own != null && own.getGrade() != null) {
            sources.put("grade", CriterionSource.STUDYING_REQUEST);
            return own.getGrade();
        }
        return null;
    }

    private ModePreference mode(Filters filters, StudyingRequest own, Map<String, CriterionSource> sources) {
        if (filters.learningMode() != null) {
            sources.put("learningMode", CriterionSource.FILTER);
            return filters.learningMode();
        }
        if (own != null && own.getLearningMode() != null) {
            sources.put("learningMode", CriterionSource.STUDYING_REQUEST);
            return own.getLearningMode() == LearningMode.ONLINE ? ModePreference.ONLINE : ModePreference.OFFLINE;
        }
        sources.put("learningMode", CriterionSource.DEFAULT);
        return ModePreference.ANY;
    }

    /** The searcher decides where to look; by default the district they live in. */
    private ResolvedArea area(
            Area chosen, ModePreference mode, StudyingRequest own, UUID homeDistrictId, UUID homeProvinceId,
            Map<String, CriterionSource> sources) {
        if (mode == ModePreference.ONLINE) {
            return new ResolvedArea(AreaScope.ANY, List.of(), null);
        }
        if (chosen != null && chosen.scope() != null) {
            sources.put("area", CriterionSource.FILTER);
            return switch (chosen.scope()) {
                case DISTRICTS -> new ResolvedArea(
                        AreaScope.DISTRICTS, chosen.districtIds().stream().distinct().toList(), null);
                case PROVINCE -> {
                    UUID provinceId = chosen.provinceId() != null ? chosen.provinceId() : homeProvinceId;
                    if (provinceId == null || !provinceRepository.existsById(provinceId)) {
                        throw badRequest("INVALID_PROVINCE", "Province is required for a province-wide search");
                    }
                    yield new ResolvedArea(AreaScope.PROVINCE, List.of(), provinceId);
                }
                case ANY -> new ResolvedArea(AreaScope.ANY, List.of(), null);
            };
        }
        if (own != null && own.getDistrict() != null) {
            sources.put("area", CriterionSource.STUDYING_REQUEST);
            return new ResolvedArea(AreaScope.DISTRICTS, List.of(own.getDistrict().getId()), null);
        }
        if (homeDistrictId != null) {
            sources.put("area", CriterionSource.PROFILE);
            return new ResolvedArea(AreaScope.DISTRICTS, List.of(homeDistrictId), null);
        }
        sources.put("area", CriterionSource.DEFAULT);
        return new ResolvedArea(AreaScope.ANY, List.of(), null);
    }

    private List<TimeSlot> availabilities(
            Filters filters, StudyingRequest own, Map<String, CriterionSource> sources) {
        if (filters.availabilities() != null) {
            sources.put("availabilities", CriterionSource.FILTER);
            return filters.availabilities().stream()
                    .map(slot -> new TimeSlot(slot.dayOfWeek(), slot.startTime(), slot.endTime())).toList();
        }
        if (own != null && !own.getAvailabilities().isEmpty()) {
            sources.put("availabilities", CriterionSource.STUDYING_REQUEST);
            return own.getAvailabilities().stream()
                    .map(slot -> new TimeSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime())).toList();
        }
        return List.of();
    }

    /** Codes that are not in the vocabulary are ignored; TOPIC tags must belong to the chosen subject. */
    private List<RecommendationTag> chosenTags(RecommendationTagGroup group, List<String> codes, UUID subjectId) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = codes.stream().filter(Objects::nonNull).map(String::strip).collect(Collectors.toSet());
        return tagRepository.findAllByTagGroupAndCodeInAndStatus(group, wanted, RecommendationTagStatus.ACTIVE)
                .stream()
                .filter(tag -> belongsToSubject(tag, subjectId))
                .toList();
    }

    private List<RecommendationEntityTag> derivedNeedTags(List<RecommendationEntityTag> tags, UUID subjectId) {
        return tags.stream()
                .filter(tag -> NEED_ROLES.contains(tag.getRole()))
                .filter(tag -> tag.getTag().getTagGroup() == RecommendationTagGroup.GOAL
                        || tag.getTag().getTagGroup() == RecommendationTagGroup.TOPIC)
                .filter(tag -> belongsToSubject(tag.getTag(), subjectId))
                .toList();
    }

    private List<RecommendationEntityTag> derivedStyleTags(List<RecommendationEntityTag> tags) {
        return tags.stream()
                .filter(tag -> tag.getRole() == RecommendationTagRole.PREFERRED
                        && tag.getTag().getTagGroup() == RecommendationTagGroup.STYLE)
                .toList();
    }

    private boolean belongsToSubject(RecommendationTag tag, UUID subjectId) {
        return tag.getTagGroup() != RecommendationTagGroup.TOPIC
                || tag.getSubject() == null
                || tag.getSubject().getId().equals(subjectId);
    }

    private WeightedTag weighted(RecommendationTag tag, double confidence) {
        return new WeightedTag(
                tag.getId(), tag.getCode(), tag.getParent() == null ? null : tag.getParent().getId(), confidence);
    }

    /** Text that is embedded to find semantically close postings (design 05, 5.5). No personal data. */
    private String demandText(
            Subject subject, Grade grade, List<WeightedTag> needTags, List<String> needNames,
            List<String> styleNames, RecommendationTag level, String semanticText, Student student,
            StudyingRequest own) {
        List<String> lines = new ArrayList<>();
        lines.add(sentence(
                subject == null ? "" : "Môn " + subject.getName(),
                grade == null ? "" : grade.getName(),
                needNames.isEmpty() || needTags.isEmpty() ? "" : "Mục tiêu và phần cần cải thiện: "
                        + String.join(", ", needNames)));
        lines.add(sentence(
                styleNames.isEmpty() ? "" : "Phong cách mong muốn: " + String.join(", ", styleNames),
                level == null ? "" : "Trình độ: " + level.getName()));
        lines.add(semanticText);
        String profile = sentence(textBuilder.scrub(student.getGoalsDescription()),
                textBuilder.scrub(student.getWeakPoints()), textBuilder.scrub(student.getCurrentLevel()));
        lines.add(profile.isEmpty() ? "" : "Hồ sơ: " + profile);
        if (own != null) {
            lines.add(sentence(textBuilder.scrub(own.getTitle()), textBuilder.scrub(own.getDescription()),
                    textBuilder.scrub(own.getLearningGoals())));
        }
        return lines.stream().filter(line -> line != null && !line.isEmpty()).collect(Collectors.joining("\n"));
    }

    private String sentence(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                String trimmed = part.strip();
                kept.add(trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
            }
        }
        return String.join(". ", kept);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record ResolvedArea(AreaScope scope, List<UUID> districtIds, UUID provinceId) {
    }
}
