package com.ptutor.backend.dto.recommendation;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The searcher's need after merging the filters, their own posting (if they chose to use it) and
 * their profile. Everything the ranking needs about the searcher is in here.
 */
public record QueryContext(
        UUID studentId,
        UUID studentUserId,
        UUID subjectId,
        UUID gradeId,
        /** grades.level of the chosen grade; null when unknown. */
        Integer gradeLevel,
        ModePreference learningMode,
        AreaScope areaScope,
        List<UUID> districtIds,
        UUID provinceId,
        UUID homeDistrictId,
        UUID homeProvinceId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<TimeSlot> availabilities,
        List<WeightedTag> needTags,
        List<WeightedTag> styleTags,
        String levelTagCode,
        String semanticText,
        String demandText,
        UUID sourceStudyingRequestId,
        Map<String, CriterionSource> sources,
        List<String> needsInput) {

    /** One free slot; {@code dayOfWeek} is ISO-8601 (1 = Monday ... 7 = Sunday). */
    public record TimeSlot(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    /** A tag the searcher asked for, with how sure we are that they want it (1.0 when they picked it). */
    public record WeightedTag(UUID tagId, String code, UUID parentId, double confidence) {
    }

    public boolean isComplete() {
        return needsInput.isEmpty();
    }

    // Loosened copies for the relaxation suggestions (design 07, 7.1). The demand text stays the same:
    // what the student is looking for has not changed, only how strict the search is.

    public QueryContext withArea(AreaScope scope, List<UUID> districts, UUID province) {
        return copy(learningMode, scope, districts, province, maxPrice, availabilities);
    }

    public QueryContext withLearningMode(ModePreference mode) {
        return copy(mode, areaScope, districtIds, provinceId, maxPrice, availabilities);
    }

    public QueryContext withMaxPrice(BigDecimal price) {
        return copy(learningMode, areaScope, districtIds, provinceId, price, availabilities);
    }

    public QueryContext withoutSchedule() {
        return copy(learningMode, areaScope, districtIds, provinceId, maxPrice, List.of());
    }

    private QueryContext copy(ModePreference mode, AreaScope scope, List<UUID> districts, UUID province,
            BigDecimal price, List<TimeSlot> slots) {
        return new QueryContext(studentId, studentUserId, subjectId, gradeId, gradeLevel, mode, scope, districts,
                province, homeDistrictId, homeProvinceId, minPrice, price, slots, needTags, styleTags, levelTagCode,
                semanticText, demandText, sourceStudyingRequestId, sources, needsInput);
    }
}
