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
}
