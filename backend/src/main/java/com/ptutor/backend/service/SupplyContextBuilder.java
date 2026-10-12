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
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.request.StudentRecommendationRequest.Filters;
import com.ptutor.backend.dto.request.TutorRecommendationRequest.Area;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.RecommendationEntityTag;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagRole;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.StudyingCandidateRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Merges a tutor's search into one {@link SupplyContext} (design 05, 5.3 and 5.5). For each
 * criterion the first source that has a value wins: the filter, then the tutor's own teaching
 * posting (only if they asked to use it), then their profile. A tutor needs no posting to search.
 */
@Component
@RequiredArgsConstructor
public class SupplyContextBuilder {

    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final ProvinceRepository provinceRepository;
    private final TeachingRequestRepository teachingRequestRepository;
    private final StudyingCandidateRepository studyingCandidateRepository;
    private final RecommendationTagRepository tagRepository;
    private final RecommendationEntityTagRepository entityTagRepository;
    private final EmbeddingTextBuilder textBuilder;

    /** Call inside a transaction: the tutor's user, district and the posting's associations are read lazily. */
    public SupplyContext forTutor(Tutor tutor, StudentRecommendationRequest request) {
        Filters filters = request.filters() == null
                ? new Filters(null, null, null, null, null, null, null) : request.filters();
        Map<String, CriterionSource> sources = new LinkedHashMap<>();
        TeachingRequest own = request.useTeachingRequestId() == null ? null : teachingRequestRepository
                .findByIdAndTutor_Id(request.useTeachingRequestId(), tutor.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEACHING_REQUEST_NOT_FOUND",
                        "Teaching request not found: " + request.useTeachingRequestId()));

        Subject subject = subject(filters, own, sources);
        List<Grade> grades = grades(filters, own, sources);
        ModePreference mode = mode(filters, own, sources);
        District home = tutor.getUser() == null ? null : tutor.getUser().getDistrict();
        UUID homeDistrictId = home == null ? null : home.getId();
        UUID homeProvinceId = home == null || home.getProvince() == null ? null : home.getProvince().getId();
        ResolvedArea area = area(filters.area(), mode, own, homeDistrictId, homeProvinceId, sources);
        BigDecimal expectedPrice = expectedPrice(filters, own, tutor, sources);
        List<TimeSlot> availabilities = availabilities(filters, own, sources);

        List<RecommendationEntityTag> profileTags = new ArrayList<>(entityTagRepository
                .findAllByEntityTypeAndEntityId(RecommendationEntityType.TUTOR, tutor.getId()));
        if (own != null) {
            profileTags.addAll(entityTagRepository
                    .findAllByEntityTypeAndEntityId(RecommendationEntityType.TEACHING_REQUEST, own.getId()));
        }
        UUID subjectId = subject == null ? null : subject.getId();
        Set<CandidateTag> strengths = profileTags.stream()
                .filter(tag -> tag.getRole() == RecommendationTagRole.STRENGTH)
                .map(RecommendationEntityTag::getTag)
                .filter(tag -> tag.getTagGroup() == RecommendationTagGroup.GOAL
                        || (tag.getTagGroup() == RecommendationTagGroup.TOPIC
                                && (tag.getSubject() == null || tag.getSubject().getId().equals(subjectId))))
                .map(tag -> new CandidateTag(tag.getId(), tag.getParent() == null ? null : tag.getParent().getId()))
                .collect(Collectors.toSet());
        Set<String> styles = codes(profileTags, RecommendationTagRole.OFFERED, RecommendationTagGroup.STYLE);
        Set<String> segments;
        if (filters.targetSegmentCodes() != null) {
            Set<String> wanted = filters.targetSegmentCodes().stream()
                    .filter(Objects::nonNull).map(String::strip).collect(Collectors.toSet());
            segments = wanted.isEmpty() ? Set.of() : tagRepository
                    .findAllByTagGroupAndCodeInAndStatus(
                            RecommendationTagGroup.SEGMENT, wanted, RecommendationTagStatus.ACTIVE)
                    .stream().map(RecommendationTag::getCode).collect(Collectors.toSet());
            sources.put("targetSegments", CriterionSource.FILTER);
        } else {
            segments = codes(profileTags, RecommendationTagRole.TARGET, RecommendationTagGroup.SEGMENT);
            if (!segments.isEmpty()) {
                sources.put("targetSegments", CriterionSource.PROFILE);
            }
        }

        List<String> needsInput = new ArrayList<>();
        if (subject == null) {
            needsInput.add("subjectId");
        }
        if (grades.isEmpty()) {
            needsInput.add("gradeIds");
        }
        double quality = tutor.getRecommendationScore() == null ? Ranker.QUALITY_WHEN_UNKNOWN
                : Math.max(0, Math.min(1, tutor.getRecommendationScore().doubleValue() / 100.0));
        return new SupplyContext(
                tutor.getId(), tutor.getUser() == null ? null : tutor.getUser().getId(), subjectId,
                grades.stream().map(Grade::getId).toList(), mode, area.scope(), area.districtIds(),
                area.provinceId(), homeDistrictId, homeProvinceId, expectedPrice, availabilities, strengths, styles,
                segments, quality, supplyText(subject, grades, tutor, own), own == null ? null : own.getId(),
                Map.copyOf(sources), List.copyOf(needsInput));
    }

    private Subject subject(Filters filters, TeachingRequest own, Map<String, CriterionSource> sources) {
        if (filters.subjectId() != null) {
            sources.put("subject", CriterionSource.FILTER);
            return subjectRepository.findById(filters.subjectId())
                    .filter(subject -> subject.getStatus() == CatalogStatus.ACTIVE)
                    .orElseThrow(() -> badRequest("INVALID_SUBJECT", "Subject not found or inactive"));
        }
        if (own != null && own.getSubject() != null) {
            sources.put("subject", CriterionSource.TEACHING_REQUEST);
            return own.getSubject();
        }
        return null;
    }

    private List<Grade> grades(Filters filters, TeachingRequest own, Map<String, CriterionSource> sources) {
        if (filters.gradeIds() != null && !filters.gradeIds().isEmpty()) {
            sources.put("grades", CriterionSource.FILTER);
            List<UUID> ids = filters.gradeIds().stream().distinct().toList();
            List<Grade> found = gradeRepository.findAllById(ids).stream()
                    .filter(grade -> grade.getStatus() == CatalogStatus.ACTIVE).toList();
            if (found.size() != ids.size()) {
                throw badRequest("INVALID_GRADE", "Grade not found or inactive");
            }
            return found;
        }
        if (own != null && !own.getGradeAssociations().isEmpty()) {
            sources.put("grades", CriterionSource.TEACHING_REQUEST);
            return own.getGradeAssociations().stream()
                    .map(association -> association.getGrade()).filter(Objects::nonNull).toList();
        }
        return List.of();
    }

    private ModePreference mode(Filters filters, TeachingRequest own, Map<String, CriterionSource> sources) {
        if (filters.learningMode() != null) {
            sources.put("learningMode", CriterionSource.FILTER);
            return filters.learningMode();
        }
        if (own != null && own.getTeachingMode() != null) {
            sources.put("learningMode", CriterionSource.TEACHING_REQUEST);
            return own.getTeachingMode() == TeachingMode.ONLINE ? ModePreference.ONLINE : ModePreference.OFFLINE;
        }
        sources.put("learningMode", CriterionSource.DEFAULT);
        return ModePreference.ANY;
    }

    private ResolvedArea area(
            Area chosen, ModePreference mode, TeachingRequest own, UUID homeDistrictId, UUID homeProvinceId,
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
        if (own != null && !own.getDistrictAssociations().isEmpty()) {
            sources.put("area", CriterionSource.TEACHING_REQUEST);
            return new ResolvedArea(AreaScope.DISTRICTS, own.getDistrictAssociations().stream()
                    .map(association -> association.getDistrict().getId()).distinct().toList(), null);
        }
        if (homeDistrictId != null) {
            sources.put("area", CriterionSource.PROFILE);
            return new ResolvedArea(AreaScope.DISTRICTS, List.of(homeDistrictId), null);
        }
        sources.put("area", CriterionSource.DEFAULT);
        return new ResolvedArea(AreaScope.ANY, List.of(), null);
    }

    /** Left empty, the price defaults to the median of what the tutor asked for in their postings. */
    private BigDecimal expectedPrice(
            Filters filters, TeachingRequest own, Tutor tutor, Map<String, CriterionSource> sources) {
        if (filters.expectedPrice() != null) {
            sources.put("expectedPrice", CriterionSource.FILTER);
            return filters.expectedPrice();
        }
        if (own != null && own.getExpectedPrice() != null) {
            sources.put("expectedPrice", CriterionSource.TEACHING_REQUEST);
            return own.getExpectedPrice();
        }
        BigDecimal median = studyingCandidateRepository.medianExpectedPrice(tutor.getId()).orElse(null);
        if (median != null) {
            sources.put("expectedPrice", CriterionSource.PROFILE);
        }
        return median;
    }

    private List<TimeSlot> availabilities(
            Filters filters, TeachingRequest own, Map<String, CriterionSource> sources) {
        if (filters.availabilities() != null) {
            sources.put("availabilities", CriterionSource.FILTER);
            return filters.availabilities().stream()
                    .map(slot -> new TimeSlot(slot.dayOfWeek(), slot.startTime(), slot.endTime())).toList();
        }
        if (own != null && !own.getAvailabilities().isEmpty()) {
            sources.put("availabilities", CriterionSource.TEACHING_REQUEST);
            return own.getAvailabilities().stream()
                    .map(slot -> new TimeSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime())).toList();
        }
        return List.of();
    }

    private Set<String> codes(
            List<RecommendationEntityTag> tags, RecommendationTagRole role, RecommendationTagGroup group) {
        return tags.stream()
                .filter(tag -> tag.getRole() == role && tag.getTag().getTagGroup() == group)
                .map(tag -> tag.getTag().getCode())
                .collect(Collectors.toSet());
    }

    /** Text that is embedded to find semantically close studying postings. No personal data. */
    private String supplyText(Subject subject, List<Grade> grades, Tutor tutor, TeachingRequest own) {
        List<String> lines = new ArrayList<>();
        String header = subject == null ? "" : "Môn " + textBuilder.scrub(subject.getName());
        if (!grades.isEmpty()) {
            header += (header.isEmpty() ? "Dạy " : ", dạy ")
                    + grades.stream().map(Grade::getName).collect(Collectors.joining(", "));
        }
        lines.add(header);
        if (own != null) {
            lines.add(List.of(textBuilder.scrub(own.getTitle()), textBuilder.scrub(own.getDescription())).stream()
                    .filter(part -> !part.isEmpty()).collect(Collectors.joining(". ")));
        }
        lines.add(textBuilder.forTutor(tutor));
        return lines.stream().filter(line -> line != null && !line.isEmpty()).collect(Collectors.joining("\n"));
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record ResolvedArea(AreaScope scope, List<UUID> districtIds, UUID provinceId) {
    }
}
