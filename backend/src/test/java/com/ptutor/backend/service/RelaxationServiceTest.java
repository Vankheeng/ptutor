package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.response.RelaxationSuggestion;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.service.RelaxationService.Variant;

import tools.jackson.databind.json.JsonMapper;

class RelaxationServiceTest {

    private static final UUID HOME = UUID.randomUUID();
    private static final UUID PROVINCE = UUID.randomUUID();

    private final RecommendationSettings settings = new RecommendationSettings();
    private final DistrictRepository districtRepository = mock(DistrictRepository.class);
    private final ProvinceRepository provinceRepository = mock(ProvinceRepository.class);
    private final RelaxationService service = new RelaxationService(
            settings, districtRepository, provinceRepository, JsonMapper.builder().build());

    /** A result of the fake ranking: who it belongs to and how well it scores. */
    private record Result(UUID person, double score) {
    }

    // ---- when and what to suggest

    @Test
    void looksForSuggestionsOnlyWhenGoodResultsOrResultsAreFew() {
        assertThat(service.isNeeded(scores(5, 0.6, 5, 0.3))).as("5 good ones among 10").isFalse();
        assertThat(service.isNeeded(scores(4, 0.6, 6, 0.3))).as("only 4 good ones").isTrue();
        assertThat(service.isNeeded(scores(9, 0.9, 0, 0))).as("only 9 results").isTrue();
        assertThat(service.isNeeded(List.of())).isTrue();
        ReflectionTestUtils.setField(settings, "relaxationEnabled", false);
        assertThat(service.isNeeded(List.of())).isFalse();
    }

    @Test
    void keepsLooseningsThatBringEnoughNewGoodPeopleBestGainFirstLighterFirstOnATie() {
        List<Result> current = List.of(result(0.55), result(0.30));
        UUID known = current.get(0).person();
        Map<String, List<Result>> outcome = Map.of(
                // two new good people, one already shown, one new but weak
                "AREA_ANY", List.of(result(0.70), result(0.60), new Result(known, 0.90), result(0.40)),
                "PRICE_30", List.of(result(0.70), result(0.60)),
                "MODE_ANY", List.of(result(0.80), result(0.70), result(0.60)),
                // a single newcomer, but far better than anything found so far
                "AREA_PROVINCE", List.of(result(0.66)),
                // a single newcomer that is not better enough
                "PRICE_15", List.of(result(0.60)));
        ReflectionTestUtils.setField(settings, "relaxationMaxSuggestions", 10);

        List<RelaxationSuggestion> all = suggest(current, outcome);

        assertThat(all).extracting(RelaxationSuggestion::code)
                .containsExactly("MODE_ANY", "AREA_ANY", "PRICE_30", "AREA_PROVINCE");
        assertThat(all).extracting(RelaxationSuggestion::gain).containsExactly(3, 2, 2, 1);
        assertThat(all.get(0).message()).isEqualTo("MODE_ANY brings 3");
        assertThat(all.get(1).apply().path("code").asString()).isEqualTo("AREA_ANY");

        ReflectionTestUtils.setField(settings, "relaxationMaxSuggestions", 3);
        assertThat(suggest(current, outcome)).extracting(RelaxationSuggestion::code)
                .containsExactly("MODE_ANY", "AREA_ANY", "PRICE_30");
    }

    @Test
    void suggestsNothingWhenTheSearchAlreadyFoundEnough() {
        List<Result> current = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            current.add(result(0.8));
        }

        assertThat(suggest(current, Map.of("AREA_ANY", List.of(result(0.9), result(0.9))))).isEmpty();
    }

    @Test
    void stopsWhenTheTimeBudgetIsUsedUpAndSurvivesALooseningThatFails() {
        Map<String, List<Result>> outcome = Map.of(
                "PRICE_15", List.of(result(0.7), result(0.7)), "AREA_ANY", List.of(result(0.7), result(0.7)));
        List<String> evaluated = new ArrayList<>();
        Function<String, List<Result>> ranking = code -> {
            evaluated.add(code);
            if (code.equals("PRICE_15")) {
                throw new IllegalStateException("database hiccup");
            }
            return outcome.get(code);
        };

        List<RelaxationSuggestion> suggestions = service.suggest(List.of(), variants(outcome), ranking,
                Result::person, Result::score, (variant, gained) -> variant.code());

        assertThat(evaluated).containsExactly("PRICE_15", "AREA_ANY");
        assertThat(suggestions).extracting(RelaxationSuggestion::code).containsExactly("AREA_ANY");

        ReflectionTestUtils.setField(settings, "relaxationBudgetMs", 0L);
        evaluated.clear();
        assertThat(service.suggest(List.of(), variants(outcome), ranking, Result::person, Result::score,
                (variant, gained) -> variant.code())).isEmpty();
        assertThat(evaluated).isEmpty();
    }

    // ---- the loosenings of a student's search

    @Test
    void offersEveryLooseningThatAppliesToTheStudentsCriteria() {
        UUID chosenDistrict = UUID.randomUUID();
        Province province = mock(Province.class);
        when(province.getId()).thenReturn(PROVINCE);
        when(province.getName()).thenReturn("Hà Nội");
        District district = mock(District.class);
        when(district.getProvince()).thenReturn(province);
        when(districtRepository.findById(chosenDistrict)).thenReturn(Optional.of(district));
        QueryContext context = context().mode(ModePreference.OFFLINE).scope(AreaScope.DISTRICTS)
                .districts(List.of(chosenDistrict)).home(HOME, PROVINCE).maxPrice("200000")
                .availabilities(List.of(new TimeSlot(2, java.time.LocalTime.of(19, 0), java.time.LocalTime.of(21, 0))))
                .build();

        Map<String, Variant<QueryContext>> variants = byCode(service.forStudent(context));

        assertThat(variants).as("a schedule only gates the score, so it is never loosened")
                .containsOnlyKeys("PRICE_15", "PRICE_30", "AREA_PROVINCE", "AREA_ANY", "MODE_ANY");
        assertThat(variants.get("PRICE_15").context().maxPrice()).isEqualByComparingTo("230000");
        assertThat(variants.get("PRICE_30").context().maxPrice()).isEqualByComparingTo("260000");
        assertThat(variants.get("PRICE_30").apply().path("price").path("max").decimalValue())
                .isEqualByComparingTo("260000");
        assertThat(variants.get("AREA_PROVINCE").context().areaScope()).isEqualTo(AreaScope.PROVINCE);
        assertThat(variants.get("AREA_PROVINCE").context().provinceId()).isEqualTo(PROVINCE);
        assertThat(variants.get("AREA_PROVINCE").context().districtIds()).isEmpty();
        assertThat(variants.get("AREA_PROVINCE").apply().path("area").path("provinceId").asString())
                .isEqualTo(PROVINCE.toString());
        assertThat(variants.get("AREA_ANY").context().areaScope()).isEqualTo(AreaScope.ANY);
        assertThat(variants.get("MODE_ANY").context().learningMode()).isEqualTo(ModePreference.ANY);
        assertThat(variants.get("MODE_ANY").context().districtIds()).containsExactly(chosenDistrict);
        assertThat(variants.get("MODE_ANY").apply().path("learningMode").asString()).isEqualTo("ANY");
        assertThat(variants.get("PRICE_15").context().demandText()).as("what is searched for stays the same")
                .isEqualTo(context.demandText());

        assertThat(service.studentMessage(variants.get("AREA_PROVINCE"), List.of()))
                .isEqualTo("Mở rộng ra toàn Hà Nội: thêm 0 gia sư phù hợp");
        assertThat(service.studentMessage(variants.get("PRICE_15"), List.of()))
                .isEqualTo("Nếu ngân sách 230.000đ: thêm 0 gia sư phù hợp");
        assertThat(service.studentMessage(variants.get("MODE_ANY"), List.of()))
                .isEqualTo("Nhận cả học online: thêm 0 gia sư phù hợp");
    }

    @Test
    void offersOnlyWhatCanStillBeLoosened() {
        QueryContext open = context().mode(ModePreference.ANY).scope(AreaScope.ANY).build();
        QueryContext online = context().mode(ModePreference.ONLINE).scope(AreaScope.ANY).home(HOME, PROVINCE).build();

        assertThat(service.forStudent(open)).isEmpty();
        Variant<QueryContext> inPerson = service.forStudent(online).get(0);
        assertThat(service.forStudent(online)).hasSize(1);
        assertThat(inPerson.code()).isEqualTo("MODE_ANY");
        // Meeting in person needs a place: the district the student lives in, exactly as "apply" says.
        assertThat(inPerson.context().areaScope()).isEqualTo(AreaScope.DISTRICTS);
        assertThat(inPerson.context().districtIds()).containsExactly(HOME);
        assertThat(inPerson.apply().path("area").path("districtIds").get(0).asString()).isEqualTo(HOME.toString());
        assertThat(service.studentMessage(inPerson, List.of())).startsWith("Nhận cả học trực tiếp");
    }

    // ---- helpers

    private List<RelaxationSuggestion> suggest(List<Result> current, Map<String, List<Result>> outcome) {
        return service.suggest(current, variants(outcome), outcome::get, Result::person, Result::score,
                (variant, gained) -> variant.code() + " brings " + gained.size());
    }

    /** The "context" of a fake variant is just its code. */
    private List<Variant<String>> variants(Map<String, List<Result>> outcome) {
        return outcome.keySet().stream().map(code -> new Variant<>(code, code,
                JsonMapper.builder().build().createObjectNode().put("code", code), null)).toList();
    }

    private Map<String, Variant<QueryContext>> byCode(List<Variant<QueryContext>> variants) {
        Map<String, Variant<QueryContext>> map = new java.util.LinkedHashMap<>();
        variants.forEach(variant -> map.put(variant.code(), variant));
        return map;
    }

    private static Result result(double score) {
        return new Result(UUID.randomUUID(), score);
    }

    private static List<Double> scores(int good, double goodScore, int weak, double weakScore) {
        List<Double> scores = new ArrayList<>();
        for (int index = 0; index < good; index++) {
            scores.add(goodScore);
        }
        for (int index = 0; index < weak; index++) {
            scores.add(weakScore);
        }
        return scores;
    }
}
