package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.MatchReasons;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;

import lombok.RequiredArgsConstructor;

/**
 * Turns the numbers behind a result into sentences the user can read (design 06, 6.13). Templates
 * only, no LLM: every sentence states something that was actually measured. Pure computation.
 */
@Component
@RequiredArgsConstructor
public class MatchReasonBuilder {

    static final int MAX_REASONS = 3;
    static final double NEED_THRESHOLD = 0.7;
    static final double SEM_THRESHOLD = 0.7;
    static final double STYLE_THRESHOLD = 0.5;
    static final double SCHEDULE_THRESHOLD = 0.8;
    static final double AREA_CAUTION_THRESHOLD = 0.6;
    static final double HISTORY_THRESHOLD = 0.75;
    static final int HISTORY_MIN_DECISIONS = 3;
    static final double QUALITY_THRESHOLD = 0.8;

    private static final String[] DAYS = {
        "", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"
    };
    /** Reasons that come from the other side rank below every feature, quality below those. */
    private static final double WILLINGNESS_RANK = -1;
    private static final double QUALITY_RANK = -2;
    /** Why a result sits in an exploration slot is said first: its score alone does not explain its place. */
    private static final double EXPLORATION_RANK = 10;
    static final double HISTORY_THRESHOLD_FEATURE = 0.6;

    private final RecommendationSettings settings;

    /** Display names of tags, looked up once per search. */
    public record TagNames(Map<UUID, String> byId, Map<String, String> byGroupAndCode,
            Map<UUID, String> codeById, Map<String, String> nameByCode) {

        public TagNames(Map<UUID, String> byId, Map<String, String> byGroupAndCode) {
            this(byId, byGroupAndCode, Map.of(), Map.of());
        }

        public static TagNames of(Collection<RecommendationTag> tags) {
            Map<UUID, String> byId = new HashMap<>();
            Map<String, String> byGroupAndCode = new HashMap<>();
            Map<UUID, String> codeById = new HashMap<>();
            Map<String, String> nameByCode = new HashMap<>();
            for (RecommendationTag tag : tags) {
                byId.put(tag.getId(), tag.getName());
                codeById.put(tag.getId(), tag.getCode());
                nameByCode.put(tag.getCode(), tag.getName());
                byGroupAndCode.put(tag.getTagGroup().name() + ":" + tag.getCode(), tag.getName());
            }
            return new TagNames(byId, byGroupAndCode, codeById, nameByCode);
        }

        String style(String code) {
            return byGroupAndCode.get("STYLE:" + code);
        }

        String segment(String code) {
            return byGroupAndCode.get("SEGMENT:" + code);
        }
    }

    /** A teaching posting explained to the student who searched. */
    public MatchReasons forStudent(QueryContext context, ScoredCandidate scored, TagNames names) {
        return forStudent(context, scored, names, false, List.of());
    }

    /**
     * @param explored  the posting was moved up into an exploration slot
     * @param likedTags names of the posting's tags the student showed most interest in before
     */
    public MatchReasons forStudent(
            QueryContext context, ScoredCandidate scored, TagNames names, boolean explored, List<String> likedTags) {
        TeachingCandidate candidate = scored.candidate();
        FeatureVector features = scored.features();
        List<Reason> reasons = new ArrayList<>();
        if (explored) {
            reasons.add(new Reason(EXPLORATION_RANK, "Gia sư mới trên nền tảng"));
        }
        if (reached(features.hist(), HISTORY_THRESHOLD_FEATURE) && features.histWeight() > 0) {
            String shared = names(likedTags);
            reasons.add(new Reason(features.histWeight() * features.hist(), shared.isEmpty()
                    ? "Giống các gia sư bạn đã quan tâm" : "Giống các gia sư bạn đã quan tâm: " + shared));
        }

        if (reached(features.need(), NEED_THRESHOLD)) {
            Set<UUID> strengths = candidate.strengthTags().stream().map(CandidateTag::tagId).collect(Collectors.toSet());
            String matched = names(context.needTags().stream().map(WeightedTag::tagId)
                    .filter(strengths::contains).map(names.byId()::get).toList());
            reasons.add(new Reason(settings.getWeightNeed() * features.need(), matched.isEmpty()
                    ? "Thế mạnh của gia sư đúng phần bạn cần cải thiện"
                    : "Mạnh về " + matched + ", đúng phần bạn cần cải thiện"));
        }
        if (reached(features.sem(), SEM_THRESHOLD)) {
            reasons.add(new Reason(settings.getWeightSem() * features.sem(),
                    "Nội dung tin đăng rất sát với nhu cầu của bạn"));
        }
        if (reached(features.style(), STYLE_THRESHOLD)) {
            // 0.5 is also the neutral value of a tutor without style tags: only say it when tags really match.
            String matched = names(context.styleTags().stream().map(WeightedTag::code).distinct()
                    .filter(candidate.offeredStyleCodes()::contains).map(names::style).toList());
            if (!matched.isEmpty()) {
                reasons.add(new Reason(settings.getWeightStyle() * features.style(),
                        "Phong cách " + matched + ", đúng như bạn mong muốn"));
            }
        }
        if (isFull(features.price()) && candidate.expectedPrice() != null) {
            reasons.add(new Reason(settings.getWeightPrice(),
                    "Học phí " + money(candidate.expectedPrice()) + " — nằm trong ngân sách"));
        }
        if (reached(features.sched(), SCHEDULE_THRESHOLD)) {
            String shared = sharedSlots(context.availabilities(), candidate.availabilities());
            if (!shared.isEmpty()) {
                reasons.add(new Reason(settings.getWeightSched() * features.sched(), "Trùng lịch rảnh: " + shared));
            }
        }
        if (isFull(features.area()) && candidate.teachingMode() == TeachingMode.OFFLINE) {
            Set<UUID> preferred = preferredDistricts(context.areaScope(), context.districtIds(), context.homeDistrictId());
            candidate.districts().stream().filter(district -> preferred.contains(district.id())).findFirst()
                    .ifPresent(district -> reasons.add(
                            new Reason(settings.getWeightArea(), "Dạy tại " + district.name())));
        }

        Willingness willingness = scored.willingness();
        if (willingness != null) {
            if (willingness.history() >= HISTORY_THRESHOLD
                    && candidate.tutorDecisions().size() >= HISTORY_MIN_DECISIONS) {
                reasons.add(new Reason(WILLINGNESS_RANK + willingness.history(), context.gradeLevel() == null
                        ? "Thường nhận học sinh như bạn"
                        : "Thường nhận học sinh lớp " + context.gradeLevel() + " như bạn"));
            }
            if (willingness.capacity() >= 1.0) {
                reasons.add(new Reason(WILLINGNESS_RANK, "Còn " + Math.max(1, candidate.remainingSlots())
                        + " chỗ, chưa có nhiều người đăng ký"));
            }
        }
        if (scored.q() >= QUALITY_THRESHOLD && candidate.tutorAverageRating() != null
                && candidate.tutorTotalReviews() != null && candidate.tutorTotalReviews() > 0) {
            reasons.add(new Reason(QUALITY_RANK, "Đánh giá " + rating(candidate.tutorAverageRating()) + "/5 từ "
                    + candidate.tutorTotalReviews() + " học sinh"));
        }

        String caution = null;
        if (features.noScheduleOverlap()) {
            caution = "Lịch chưa trùng — có thể trao đổi thêm";
        } else if (features.price() != null && features.price() < 1.0 && candidate.expectedPrice() != null
                && context.maxPrice() != null) {
            caution = "Học phí cao hơn ngân sách " + money(candidate.expectedPrice().subtract(context.maxPrice()));
        } else if (features.area() != null && features.area() <= AREA_CAUTION_THRESHOLD) {
            caution = outsideArea(candidate.districts().stream().map(District::name).findFirst().orElse(null));
        }
        return new MatchReasons(best(reasons), caution);
    }

    /** A studying posting explained to the tutor who searched. */
    public MatchReasons forTutor(SupplyContext context, ScoredStudyingCandidate scored, TagNames names) {
        return forTutor(context, scored, names, false, List.of());
    }

    public MatchReasons forTutor(SupplyContext context, ScoredStudyingCandidate scored, TagNames names,
            boolean explored, List<String> likedTags) {
        StudyingCandidate candidate = scored.candidate();
        FeatureVector features = scored.features();
        List<Reason> reasons = new ArrayList<>();
        if (explored) {
            reasons.add(new Reason(EXPLORATION_RANK, "Tin học ít được xem, có thể hợp với bạn"));
        }
        if (reached(features.hist(), HISTORY_THRESHOLD_FEATURE) && features.histWeight() > 0) {
            String shared = names(likedTags);
            reasons.add(new Reason(features.histWeight() * features.hist(), shared.isEmpty()
                    ? "Giống các học viên bạn đã quan tâm" : "Giống các học viên bạn đã quan tâm: " + shared));
        }

        if (reached(features.need(), NEED_THRESHOLD)) {
            Set<UUID> strengths = context.strengthTags().stream().map(CandidateTag::tagId).collect(Collectors.toSet());
            String matched = names(candidate.needTags().stream().map(WeightedTag::tagId)
                    .filter(strengths::contains).map(names.byId()::get).toList());
            reasons.add(new Reason(settings.getTutorWeightNeed() * features.need(), matched.isEmpty()
                    ? "Học sinh cần đúng phần bạn mạnh"
                    : "Học sinh cần đúng phần bạn mạnh: " + matched));
        }
        if (reached(features.sem(), SEM_THRESHOLD)) {
            reasons.add(new Reason(settings.getTutorWeightSem() * features.sem(),
                    "Nhu cầu của học sinh rất sát với những gì bạn dạy"));
        }
        if (isFull(features.segment()) && candidate.levelCode() != null
                && context.targetSegmentCodes().contains(candidate.levelCode())) {
            String level = names.segment(candidate.levelCode());
            reasons.add(new Reason(settings.getTutorWeightSegment(), level == null
                    ? "Trình độ học sinh đúng đối tượng bạn muốn dạy"
                    : "Trình độ " + level.toLowerCase() + " — đúng đối tượng bạn muốn dạy"));
        }
        if (isFull(features.price()) && candidate.maxPrice() != null) {
            reasons.add(new Reason(settings.getTutorWeightPrice(),
                    "Ngân sách tới " + money(candidate.maxPrice()) + " — đủ mức bạn mong muốn"));
        }
        if (reached(features.sched(), SCHEDULE_THRESHOLD)) {
            String shared = sharedSlots(context.availabilities(), candidate.availabilities());
            if (!shared.isEmpty()) {
                reasons.add(new Reason(settings.getTutorWeightSched() * features.sched(), "Trùng lịch rảnh: " + shared));
            }
        }
        if (isFull(features.area()) && candidate.learningMode() == LearningMode.OFFLINE
                && candidate.district() != null) {
            reasons.add(new Reason(settings.getTutorWeightArea(),
                    "Học tại " + candidate.district().name() + ", trong khu vực bạn dạy"));
        }

        Willingness willingness = scored.willingness();
        if (willingness != null) {
            if (willingness.invited()) {
                reasons.add(new Reason(WILLINGNESS_RANK + 2, "Học sinh này đã gửi đơn vào tin dạy của bạn"));
            } else {
                if (willingness.history() >= HISTORY_THRESHOLD
                        && candidate.studentDecisions().size() >= HISTORY_MIN_DECISIONS) {
                    reasons.add(new Reason(WILLINGNESS_RANK + willingness.history(),
                            "Học sinh này thường nhận lời đề nghị của gia sư"));
                }
                if (willingness.capacity() >= 1.0) {
                    reasons.add(new Reason(WILLINGNESS_RANK, "Còn " + Math.max(1, candidate.remainingSlots())
                            + " chỗ, chưa có nhiều gia sư gửi đề nghị"));
                }
            }
        }

        String caution = null;
        if (features.noScheduleOverlap()) {
            caution = "Lịch chưa trùng — có thể trao đổi thêm";
        } else if (features.price() != null && features.price() < 1.0 && candidate.maxPrice() != null
                && context.expectedPrice() != null) {
            caution = "Ngân sách thấp hơn mức bạn mong muốn "
                    + money(context.expectedPrice().subtract(candidate.maxPrice()));
        } else if (features.area() != null && features.area() <= AREA_CAUTION_THRESHOLD) {
            caution = outsideArea(candidate.district() == null ? null : candidate.district().name());
        }
        return new MatchReasons(best(reasons), caution);
    }

    /** Feature reasons by how much they add to the score, then the other side's reasons, then quality. */
    private List<String> best(List<Reason> reasons) {
        return reasons.stream().sorted(Comparator.comparingDouble(Reason::rank).reversed())
                .limit(MAX_REASONS).map(Reason::text).toList();
    }

    private boolean reached(Double feature, double threshold) {
        return feature != null && feature >= threshold;
    }

    private boolean isFull(Double feature) {
        return feature != null && feature >= 1.0;
    }

    private Set<UUID> preferredDistricts(AreaScope scope, List<UUID> districtIds, UUID homeDistrictId) {
        return scope == AreaScope.DISTRICTS ? Set.copyOf(districtIds)
                : homeDistrictId == null ? Set.of() : Set.of(homeDistrictId);
    }

    private String outsideArea(String district) {
        return district == null ? "Ngoài khu vực bạn chọn ưu tiên"
                : "Ngoài khu vực bạn chọn ưu tiên (" + district + ")";
    }

    /** At most two names, so the sentence stays short. */
    private String names(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(2)
                .collect(Collectors.joining(", "));
    }

    /** The first two periods both sides are free, e.g. "Thứ Ba 19:00–20:00, Thứ Năm 19:00–21:00". */
    String sharedSlots(List<TimeSlot> mine, List<TimeSlot> theirs) {
        List<TimeSlot> shared = new ArrayList<>();
        for (TimeSlot own : mine) {
            for (TimeSlot other : theirs) {
                if (own.dayOfWeek() != other.dayOfWeek()) {
                    continue;
                }
                LocalTime start = own.startTime().isAfter(other.startTime()) ? own.startTime() : other.startTime();
                LocalTime end = own.endTime().isBefore(other.endTime()) ? own.endTime() : other.endTime();
                if (start.isBefore(end)) {
                    shared.add(new TimeSlot(own.dayOfWeek(), start, end));
                }
            }
        }
        return shared.stream()
                .sorted(Comparator.comparingInt(TimeSlot::dayOfWeek).thenComparing(TimeSlot::startTime))
                .map(slot -> DAYS[slot.dayOfWeek()] + " " + slot.startTime() + "–" + slot.endTime())
                .distinct().limit(2).collect(Collectors.joining(", "));
    }

    /** 180000 -> "180.000đ". */
    static String money(BigDecimal amount) {
        String digits = amount.setScale(0, RoundingMode.HALF_UP).abs().toPlainString();
        StringBuilder grouped = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && (digits.length() - index) % 3 == 0) {
                grouped.append('.');
            }
            grouped.append(digits.charAt(index));
        }
        return grouped + "đ";
    }

    private String rating(BigDecimal average) {
        return average.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private record Reason(double rank, String text) {
    }
}
