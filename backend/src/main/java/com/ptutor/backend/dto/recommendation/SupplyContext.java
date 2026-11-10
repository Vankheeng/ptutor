package com.ptutor.backend.dto.recommendation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;

/**
 * What a tutor offers when looking for students, after merging the filters, their own teaching
 * posting (if they chose to use it) and their profile. Mirror image of {@link QueryContext}.
 */
public record SupplyContext(
        UUID tutorId,
        UUID tutorUserId,
        UUID subjectId,
        List<UUID> gradeIds,
        ModePreference teachingMode,
        AreaScope areaScope,
        List<UUID> districtIds,
        UUID provinceId,
        UUID homeDistrictId,
        UUID homeProvinceId,
        /** Price per lesson the tutor hopes for; null when unknown. */
        BigDecimal expectedPrice,
        List<TimeSlot> availabilities,
        /** TOPIC / GOAL tags the tutor is strong at. */
        Set<CandidateTag> strengthTags,
        /** STYLE tag codes the tutor offers. */
        Set<String> offeredStyleCodes,
        /** SEGMENT tag codes of the students the tutor wants to teach. */
        Set<String> targetSegmentCodes,
        /** The tutor's own quality in [0, 1] (recommendation_score / 100; 0.5 when never computed). */
        double quality,
        String supplyText,
        UUID sourceTeachingRequestId,
        Map<String, CriterionSource> sources,
        List<String> needsInput) {

    public boolean isComplete() {
        return needsInput.isEmpty();
    }

    // Loosened copies for the relaxation suggestions (design 07, 7.1).

    public SupplyContext withArea(AreaScope scope, List<UUID> districts, UUID province) {
        return copy(teachingMode, scope, districts, province, expectedPrice, availabilities);
    }

    public SupplyContext withTeachingMode(ModePreference mode) {
        return copy(mode, areaScope, districtIds, provinceId, expectedPrice, availabilities);
    }

    public SupplyContext withExpectedPrice(BigDecimal price) {
        return copy(teachingMode, areaScope, districtIds, provinceId, price, availabilities);
    }

    private SupplyContext copy(ModePreference mode, AreaScope scope, List<UUID> districts, UUID province,
            BigDecimal price, List<TimeSlot> slots) {
        return new SupplyContext(tutorId, tutorUserId, subjectId, gradeIds, mode, scope, districts, province,
                homeDistrictId, homeProvinceId, price, slots, strengthTags, offeredStyleCodes, targetSegmentCodes,
                quality, supplyText, sourceTeachingRequestId, sources, needsInput);
    }
}
