package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.response.RelaxationSuggestion;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * C6 (design 07, 7.1): when a search finds few good results, each criterion is loosened on its own,
 * the ranking is run again, and the loosenings that would really bring more good people are offered
 * with a one-click "apply". Suggestions never change the results that were returned.
 */
@Service
@RequiredArgsConstructor
public class RelaxationService {

    private static final Logger log = LoggerFactory.getLogger(RelaxationService.class);

    public static final String SCHEDULE_FLEX = "SCHEDULE_FLEX";
    public static final String PRICE_15 = "PRICE_15";
    public static final String AREA_PROVINCE = "AREA_PROVINCE";
    public static final String MODE_ANY = "MODE_ANY";
    public static final String AREA_ANY = "AREA_ANY";
    public static final String PRICE_30 = "PRICE_30";

    /** Tried in this order while the time budget lasts, and preferred in this order when gains are equal. */
    static final List<String> LIGHTEST_FIRST =
            List.of(SCHEDULE_FLEX, PRICE_15, AREA_PROVINCE, MODE_ANY, AREA_ANY, PRICE_30);

    static final BigDecimal RATING_WORTH_MENTIONING = new BigDecimal("4.5");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final RecommendationSettings settings;
    private final DistrictRepository districtRepository;
    private final ProvinceRepository provinceRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param context the loosened criteria
     * @param apply   filter values the client overwrites to run exactly this search
     * @param detail  what the sentence shown to the user needs: a province name, a price, a mode
     */
    public record Variant<C>(String code, C context, ObjectNode apply, String detail) {
    }

    /** Few good results (score at or above the threshold) or few results at all. */
    public boolean isNeeded(List<Double> scores) {
        if (!settings.isRelaxationEnabled()) {
            return false;
        }
        long good = scores.stream().filter(score -> score >= settings.getRelaxationGoodThreshold()).count();
        return good < settings.getRelaxationMinGood() || scores.size() < settings.getRelaxationMinResults();
    }

    /**
     * @param current the people already returned, one result per person
     * @param ranking phases 1-3 for a loosened context, one result per person
     * @param person  who a result belongs to: a loosening only counts people not returned already
     * @param message the sentence for a kept loosening, given the newly gained good results
     */
    public <C, S> List<RelaxationSuggestion> suggest(
            List<S> current, List<Variant<C>> variants, Function<C, List<S>> ranking,
            Function<S, UUID> person, ToDoubleFunction<S> score, BiFunction<Variant<C>, List<S>, String> message) {
        if (variants.isEmpty() || !isNeeded(current.stream().map(score::applyAsDouble).toList())) {
            return List.of();
        }
        long deadline = System.nanoTime() + settings.getRelaxationBudgetMs() * 1_000_000;
        Set<UUID> known = current.stream().map(person).collect(Collectors.toSet());
        double currentBest = current.stream().mapToDouble(score).max().orElse(0);

        List<Evaluated> kept = new ArrayList<>();
        List<Variant<C>> ordered = variants.stream()
                .sorted(Comparator.comparingInt((Variant<C> variant) -> LIGHTEST_FIRST.indexOf(variant.code())))
                .toList();
        for (Variant<C> variant : ordered) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            List<S> gained;
            try {
                // Ranked with μ, like other background work (design 12, 12.4).
                gained = WeightProvider.withMeanWeights(() -> ranking.apply(variant.context())).stream()
                        .filter(result -> !known.contains(person.apply(result)))
                        .filter(result -> score.applyAsDouble(result) >= settings.getRelaxationGoodThreshold())
                        .toList();
            } catch (RuntimeException exception) {
                // A suggestion is a bonus: it must never make the search itself fail.
                log.warn("Relaxation {} could not be evaluated: {}", variant.code(), exception.toString());
                continue;
            }
            double best = gained.stream().mapToDouble(score).max().orElse(0);
            if (gained.size() >= settings.getRelaxationMinGain()
                    || (!gained.isEmpty() && best >= currentBest + settings.getRelaxationBestScoreMargin())) {
                kept.add(new Evaluated(LIGHTEST_FIRST.indexOf(variant.code()), new RelaxationSuggestion(
                        variant.code(), message.apply(variant, gained), gained.size(), variant.apply())));
            }
        }
        return kept.stream()
                .sorted(Comparator.comparingInt((Evaluated evaluated) -> evaluated.suggestion().gain()).reversed()
                        .thenComparingInt(Evaluated::weight))
                .limit(settings.getRelaxationMaxSuggestions())
                .map(Evaluated::suggestion).toList();
    }

    // ---------------------------------------------------------------- a student looks for tutors

    public List<Variant<QueryContext>> forStudent(QueryContext context) {
        List<Variant<QueryContext>> variants = new ArrayList<>();
        if (!context.availabilities().isEmpty()) {
            ObjectNode apply = objectMapper.createObjectNode();
            apply.putArray("availabilities");
            variants.add(new Variant<>(SCHEDULE_FLEX, context.withoutSchedule(), apply, null));
        }
        if (context.maxPrice() != null && context.maxPrice().signum() > 0) {
            for (String code : List.of(PRICE_15, PRICE_30)) {
                BigDecimal raised = context.maxPrice().multiply(new BigDecimal(code.equals(PRICE_15) ? "1.15" : "1.30"))
                        .divide(THOUSAND, 0, RoundingMode.CEILING).multiply(THOUSAND);
                ObjectNode apply = objectMapper.createObjectNode();
                ObjectNode price = apply.putObject("price");
                price.put("min", context.minPrice());
                price.put("max", raised);
                variants.add(new Variant<>(code, context.withMaxPrice(raised), apply,
                        MatchReasonBuilder.money(raised)));
            }
        }
        if (context.areaScope() == AreaScope.DISTRICTS) {
            Province province = province(context.provinceId(), context.districtIds(), context.homeProvinceId());
            if (province != null) {
                variants.add(new Variant<>(AREA_PROVINCE,
                        context.withArea(AreaScope.PROVINCE, List.of(), province.getId()),
                        areaApply(AreaScope.PROVINCE, List.of(), province.getId()), province.getName()));
            }
        }
        if (context.areaScope() != AreaScope.ANY) {
            variants.add(new Variant<>(AREA_ANY, context.withArea(AreaScope.ANY, List.of(), null),
                    areaApply(AreaScope.ANY, List.of(), null), null));
        }
        if (context.learningMode() == ModePreference.OFFLINE) {
            ObjectNode apply = objectMapper.createObjectNode();
            apply.put("learningMode", ModePreference.ANY.name());
            variants.add(new Variant<>(MODE_ANY, context.withLearningMode(ModePreference.ANY), apply, "online"));
        } else if (context.learningMode() == ModePreference.ONLINE) {
            // Meeting in person needs a place: the district the student lives in, like a search without filters.
            AreaScope scope = context.homeDistrictId() == null ? AreaScope.ANY : AreaScope.DISTRICTS;
            List<UUID> districts = context.homeDistrictId() == null ? List.of() : List.of(context.homeDistrictId());
            ObjectNode apply = areaApply(scope, districts, null);
            apply.put("learningMode", ModePreference.ANY.name());
            variants.add(new Variant<>(MODE_ANY,
                    context.withLearningMode(ModePreference.ANY).withArea(scope, districts, null), apply,
                    "trực tiếp"));
        }
        return variants;
    }

    public String studentMessage(Variant<QueryContext> variant, List<ScoredCandidate> gained) {
        String more = "thêm " + gained.size() + " gia sư";
        return switch (variant.code()) {
            case AREA_PROVINCE -> "Mở rộng ra toàn " + variant.detail() + ": " + more + " phù hợp";
            case AREA_ANY -> "Bỏ giới hạn khu vực: " + more + " phù hợp";
            case MODE_ANY -> {
                long wellRated = gained.stream()
                        .filter(scored -> scored.candidate().tutorAverageRating() != null && scored.candidate()
                                .tutorAverageRating().compareTo(RATING_WORTH_MENTIONING) > 0)
                        .count();
                yield "Nhận cả học " + variant.detail() + ": " + more
                        + (wellRated > 0 ? ", trong đó " + wellRated + " người đánh giá trên 4,5" : " phù hợp");
            }
            case SCHEDULE_FLEX -> "Nếu linh hoạt lịch học: " + more + " phù hợp";
            default -> "Nếu ngân sách " + variant.detail() + ": " + more + " phù hợp";
        };
    }

    // ---------------------------------------------------------------- a tutor looks for students

    public List<Variant<SupplyContext>> forTutor(SupplyContext context) {
        List<Variant<SupplyContext>> variants = new ArrayList<>();
        if (!context.availabilities().isEmpty()) {
            ObjectNode apply = objectMapper.createObjectNode();
            apply.putArray("availabilities");
            variants.add(new Variant<>(SCHEDULE_FLEX, context.withoutSchedule(), apply, null));
        }
        if (context.expectedPrice() != null && context.expectedPrice().signum() > 0) {
            for (String code : List.of(PRICE_15, PRICE_30)) {
                BigDecimal lowered = context.expectedPrice()
                        .multiply(new BigDecimal(code.equals(PRICE_15) ? "0.90" : "0.80"))
                        .divide(THOUSAND, 0, RoundingMode.FLOOR).multiply(THOUSAND);
                if (lowered.signum() <= 0) {
                    continue;
                }
                ObjectNode apply = objectMapper.createObjectNode();
                apply.put("expectedPrice", lowered);
                variants.add(new Variant<>(code, context.withExpectedPrice(lowered), apply,
                        MatchReasonBuilder.money(lowered)));
            }
        }
        if (context.areaScope() == AreaScope.DISTRICTS) {
            Province province = province(context.provinceId(), context.districtIds(), context.homeProvinceId());
            if (province != null) {
                variants.add(new Variant<>(AREA_PROVINCE,
                        context.withArea(AreaScope.PROVINCE, List.of(), province.getId()),
                        areaApply(AreaScope.PROVINCE, List.of(), province.getId()), province.getName()));
            }
        }
        if (context.areaScope() != AreaScope.ANY) {
            variants.add(new Variant<>(AREA_ANY, context.withArea(AreaScope.ANY, List.of(), null),
                    areaApply(AreaScope.ANY, List.of(), null), null));
        }
        if (context.teachingMode() == ModePreference.OFFLINE) {
            ObjectNode apply = objectMapper.createObjectNode();
            apply.put("learningMode", ModePreference.ANY.name());
            variants.add(new Variant<>(MODE_ANY, context.withTeachingMode(ModePreference.ANY), apply, "online"));
        } else if (context.teachingMode() == ModePreference.ONLINE) {
            AreaScope scope = context.homeDistrictId() == null ? AreaScope.ANY : AreaScope.DISTRICTS;
            List<UUID> districts = context.homeDistrictId() == null ? List.of() : List.of(context.homeDistrictId());
            ObjectNode apply = areaApply(scope, districts, null);
            apply.put("learningMode", ModePreference.ANY.name());
            variants.add(new Variant<>(MODE_ANY,
                    context.withTeachingMode(ModePreference.ANY).withArea(scope, districts, null), apply,
                    "trực tiếp"));
        }
        return variants;
    }

    public String tutorMessage(Variant<SupplyContext> variant, List<ScoredStudyingCandidate> gained) {
        String more = "thêm " + gained.size() + " học viên phù hợp";
        return switch (variant.code()) {
            case AREA_PROVINCE -> "Mở rộng ra toàn " + variant.detail() + ": " + more;
            case AREA_ANY -> "Bỏ giới hạn khu vực: " + more;
            case MODE_ANY -> "Nhận cả dạy " + variant.detail() + ": " + more;
            case SCHEDULE_FLEX -> "Nếu linh hoạt lịch dạy: " + more;
            default -> "Nếu nhận mức " + variant.detail() + " mỗi buổi: " + more;
        };
    }

    // ---------------------------------------------------------------- helpers

    /** The province to widen to: the one chosen, else the one of the chosen districts, else the home one. */
    private Province province(UUID chosenProvinceId, List<UUID> districtIds, UUID homeProvinceId) {
        if (chosenProvinceId != null) {
            return provinceRepository.findById(chosenProvinceId).orElse(null);
        }
        for (UUID districtId : districtIds) {
            Province province = districtRepository.findById(districtId).map(District::getProvince).orElse(null);
            if (province != null) {
                return province;
            }
        }
        return homeProvinceId == null ? null : provinceRepository.findById(homeProvinceId).orElse(null);
    }

    private ObjectNode areaApply(AreaScope scope, List<UUID> districtIds, UUID provinceId) {
        ObjectNode apply = objectMapper.createObjectNode();
        ObjectNode area = apply.putObject("area");
        area.put("scope", scope.name());
        if (!districtIds.isEmpty()) {
            tools.jackson.databind.node.ArrayNode ids = area.putArray("districtIds");
            districtIds.forEach(id -> ids.add(id.toString()));
        }
        if (provinceId != null) {
            area.put("provinceId", provinceId.toString());
        }
        return apply;
    }

    private record Evaluated(int weight, RelaxationSuggestion suggestion) {
    }
}
