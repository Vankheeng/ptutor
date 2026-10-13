package com.ptutor.backend.service;

import static com.ptutor.backend.service.FeatureCalculatorTest.candidate;
import static com.ptutor.backend.service.FeatureCalculatorTest.context;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ptutor.backend.dto.recommendation.AreaScope;
import com.ptutor.backend.dto.recommendation.MatchReasons;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.TimeSlot;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.ScoredCandidate;
import com.ptutor.backend.dto.recommendation.ScoredStudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.StudyingCandidate.PastProposal;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.CandidateTag;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.District;
import com.ptutor.backend.dto.recommendation.TeachingCandidate.PastDecision;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.service.MatchReasonBuilder.TagNames;

class MatchReasonBuilderTest {

    private static final double P0 = 0.6;
    private static final UUID GEOMETRY = UUID.randomUUID();
    private static final UUID EXAM = UUID.randomUUID();
    private static final UUID DISTRICT = UUID.randomUUID();
    private static final UUID PROVINCE = UUID.randomUUID();
    private static final TagNames NAMES = new TagNames(
            Map.of(GEOMETRY, "Hình học phẳng", EXAM, "Thi vào lớp 10"),
            Map.of("STYLE:kien_nhan", "Kiên nhẫn", "SEGMENT:trung_binh", "Trung bình"));

    private final RecommendationSettings settings = new RecommendationSettings();
    private final Ranker ranker =
            new Ranker(new FeatureCalculator(settings), new WillingnessCalculator(settings), settings);
    private final TutorSideRanker tutorSideRanker = new TutorSideRanker(new FeatureCalculator(settings), settings);
    private final MatchReasonBuilder builder = new MatchReasonBuilder(settings);

    // ---- a student looks at a teaching posting

    @Test
    void keepsTheThreeReasonsThatAddMostToTheScore() {
        QueryContext context = context().gradeLevel(9).scope(AreaScope.DISTRICTS).districts(List.of(DISTRICT))
                .maxPrice("200000")
                .availabilities(List.of(slot(2, "19:00", "21:00"), slot(4, "19:00", "21:00")))
                .need(List.of(new WeightedTag(GEOMETRY, "hinh_hoc_phang", null, 0.95),
                        new WeightedTag(EXAM, "thi_vao_10", null, 0.95)))
                .style(List.of(new WeightedTag(UUID.randomUUID(), "kien_nhan", null, 1.0))).build();
        TeachingCandidate posting = candidate().cosine(0.80).price("180000").tutorScore("82.00")
                .districts(List.of(new District(DISTRICT, "Cầu Giấy", PROVINCE)))
                .availabilities(List.of(slot(2, "18:00", "20:00"), slot(4, "19:00", "21:00")))
                .strengths(Set.of(new CandidateTag(GEOMETRY, null), new CandidateTag(EXAM, null)))
                .styles(Set.of("kien_nhan")).build();

        MatchReasons reasons = builder.forStudent(context, ranker.score(context, posting, P0), NAMES);

        // sem 0.30 x 1, need 0.20 x 1 and price 0.20 x 1 outweigh style 0.10, schedule 0.10 x 0.75 and area 0.05
        assertThat(reasons.reasons()).hasSize(3).contains(
                "Nội dung tin đăng rất sát với nhu cầu của bạn", "Học phí 180.000đ — nằm trong ngân sách");
        assertThat(reasons.reasons()).filteredOn(text -> text.startsWith("Mạnh về")).singleElement().asString()
                .contains("Hình học phẳng").contains("Thi vào lớp 10").endsWith(", đúng phần bạn cần cải thiện");
        assertThat(reasons.caution()).isNull();
    }

    @Test
    void weakerFeaturesAreNamedWhenTheStrongOnesAreAbsent() {
        QueryContext context = context().scope(AreaScope.DISTRICTS).districts(List.of(DISTRICT))
                .availabilities(List.of(slot(2, "19:00", "21:00"), slot(4, "19:00", "21:00")))
                .style(List.of(new WeightedTag(UUID.randomUUID(), "kien_nhan", null, 1.0))).build();
        TeachingCandidate posting = candidate().cosine(0.40)
                .districts(List.of(new District(DISTRICT, "Cầu Giấy", PROVINCE)))
                .availabilities(List.of(slot(2, "19:00", "20:00"), slot(4, "19:00", "21:00")))
                .styles(Set.of("kien_nhan")).build();

        MatchReasons reasons = builder.forStudent(context, ranker.score(context, posting, P0), NAMES);

        assertThat(reasons.reasons()).containsExactly(
                "Phong cách Kiên nhẫn, đúng như bạn mong muốn",
                "Trùng lịch rảnh: Thứ Ba 19:00–20:00, Thứ Năm 19:00–21:00",
                "Dạy tại Cầu Giấy");
    }

    @Test
    void neutralValuesOfMissingDataAreNeverPresentedAsAMatch() {
        // The tutor has no style tags (f_style = 0.5), teaches online (f_area = 1) and already has
        // applications waiting: nothing to say.
        QueryContext context = context()
                .style(List.of(new WeightedTag(UUID.randomUUID(), "kien_nhan", null, 1.0))).build();
        TeachingCandidate posting = candidate().cosine(0.40).mode(TeachingMode.ONLINE).capacity(1, 3).build();

        MatchReasons reasons = builder.forStudent(context, ranker.score(context, posting, P0), NAMES);

        assertThat(reasons.reasons()).isEmpty();
        assertThat(reasons.caution()).isNull();
    }

    @Test
    void reasonsFromTheTutorsSideAndQualityComeAfterTheFeatures() {
        QueryContext context = context().gradeLevel(9).maxPrice("200000").build();
        List<PastDecision> accepted = List.of(decision(true), decision(true), decision(true), decision(true));
        TeachingCandidate posting = rated(candidate().cosine(0.40).price("180000").tutorScore("90.00")
                .mode(TeachingMode.ONLINE).capacity(2, 0).decisions(accepted).build(), "4.66", 12);

        ScoredCandidate scored = ranker.score(context, posting, P0);
        MatchReasons reasons = builder.forStudent(context, scored, NAMES);

        assertThat(scored.willingness().history()).isGreaterThanOrEqualTo(0.75);
        assertThat(reasons.reasons()).containsExactly(
                "Học phí 180.000đ — nằm trong ngân sách",
                "Thường nhận học sinh lớp 9 như bạn",
                "Còn 2 chỗ, chưa có nhiều người đăng ký");

        // Without the reciprocal part there is no willingness, so quality gets its turn.
        org.springframework.test.util.ReflectionTestUtils.setField(settings, "reciprocalEnabled", false);
        assertThat(builder.forStudent(context, ranker.score(context, posting, P0), NAMES).reasons())
                .containsExactly("Học phí 180.000đ — nằm trong ngân sách", "Đánh giá 4,7/5 từ 12 học sinh");
    }

    @Test
    void oneCautionIsGivenAndTheScheduleComesFirst() {
        QueryContext context = context().scope(AreaScope.DISTRICTS).districts(List.of(DISTRICT)).maxPrice("150000")
                .availabilities(List.of(slot(2, "19:00", "21:00"))).build();
        District elsewhere = new District(UUID.randomUUID(), "Hà Đông", PROVINCE);
        TeachingCandidate clashing = candidate().cosine(0.40).price("180000").districts(List.of(elsewhere))
                .availabilities(List.of(slot(3, "19:00", "21:00"))).build();
        TeachingCandidate expensive = candidate().cosine(0.40).price("180000").districts(List.of(elsewhere)).build();
        TeachingCandidate far = candidate().cosine(0.40).price("150000").districts(List.of(elsewhere)).build();

        assertThat(builder.forStudent(context, ranker.score(context, clashing, P0), NAMES).caution())
                .isEqualTo("Lịch chưa trùng — có thể trao đổi thêm");
        assertThat(builder.forStudent(context, ranker.score(context, expensive, P0), NAMES).caution())
                .isEqualTo("Học phí cao hơn ngân sách 30.000đ");
        assertThat(builder.forStudent(context, ranker.score(context, far, P0), NAMES).caution())
                .isEqualTo("Ngoài khu vực bạn chọn ưu tiên (Hà Đông)");
    }

    // ---- a tutor looks at a studying posting

    @Test
    void explainsAStudyingPostingFromTheTutorsPointOfView() {
        District district = new District(DISTRICT, "Cầu Giấy", PROVINCE);
        SupplyContext tutor = supply(new BigDecimal("180000"), List.of(slot(2, "19:00", "21:00")),
                Set.of(new CandidateTag(GEOMETRY, null)), Set.of("trung_binh"));
        StudyingCandidate posting = studying(0.80, "200000", LearningMode.OFFLINE, district,
                List.of(slot(2, "19:00", "21:00")), List.of(new WeightedTag(GEOMETRY, "hinh_hoc_phang", null, 1.0)),
                "trung_binh", 1, 0, List.of(), false);

        MatchReasons reasons = builder.forTutor(tutor, tutorSideRanker.score(tutor, posting, P0), NAMES);

        // sem 0.30, price 0.25 and need 0.20 come before segment 0.10, schedule 0.10 and area 0.05
        assertThat(reasons.reasons()).containsExactly(
                "Nhu cầu của học sinh rất sát với những gì bạn dạy",
                "Ngân sách tới 200.000đ — đủ mức bạn mong muốn",
                "Học sinh cần đúng phần bạn mạnh: Hình học phẳng");
        assertThat(reasons.caution()).isNull();

        StudyingCandidate plain = studying(0.40, null, LearningMode.OFFLINE, district,
                List.of(slot(2, "19:00", "21:00")), List.of(), "trung_binh", 1, 0, List.of(), false);
        assertThat(builder.forTutor(tutor, tutorSideRanker.score(tutor, plain, P0), NAMES).reasons())
                .containsExactly("Trình độ trung bình — đúng đối tượng bạn muốn dạy",
                        "Trùng lịch rảnh: Thứ Ba 19:00–21:00",
                        "Học tại Cầu Giấy, trong khu vực bạn dạy");
    }

    @Test
    void tellsTheTutorWhatTheStudentAlreadyDidAndWhatMayNotFit() {
        SupplyContext tutor = supply(new BigDecimal("180000"), List.of(), Set.of(), Set.of());
        List<PastProposal> accepted = List.of(proposal(), proposal(), proposal(), proposal());
        StudyingCandidate applied = studying(0.40, null, LearningMode.ONLINE, null, List.of(), List.of(), null,
                1, 0, List.of(), true);
        StudyingCandidate easygoing = studying(0.40, null, LearningMode.ONLINE, null, List.of(), List.of(), null,
                2, 0, accepted, false);
        StudyingCandidate tightBudget = studying(0.40, "150000", LearningMode.ONLINE, null, List.of(), List.of(),
                null, 1, 3, List.of(), false);

        assertThat(builder.forTutor(tutor, tutorSideRanker.score(tutor, applied, P0), NAMES).reasons())
                .containsExactly("Học sinh này đã gửi đơn vào tin dạy của bạn");
        assertThat(builder.forTutor(tutor, tutorSideRanker.score(tutor, easygoing, P0), NAMES).reasons())
                .containsExactly("Học sinh này thường nhận lời đề nghị của gia sư",
                        "Chưa có gia sư nào khác đang chờ học sinh này trả lời");
        MatchReasons tight = builder.forTutor(tutor, tutorSideRanker.score(tutor, tightBudget, P0), NAMES);
        assertThat(tight.reasons()).isEmpty();
        assertThat(tight.caution()).isEqualTo("Ngân sách thấp hơn mức bạn mong muốn 30.000đ");
    }

    // ---- formatting

    @ParameterizedTest
    @CsvSource({ "180000, 180.000đ", "1250000, 1.250.000đ", "999, 999đ", "50000.4, 50.000đ" })
    void moneyIsWrittenTheVietnameseWay(String amount, String expected) {
        assertThat(MatchReasonBuilder.money(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    void sharedSlotsAreTheOverlapOfBothSchedulesAtMostTwo() {
        String shared = builder.sharedSlots(
                List.of(slot(7, "08:00", "11:00"), slot(2, "19:00", "21:00"), slot(4, "19:00", "21:00")),
                List.of(slot(4, "20:00", "22:00"), slot(2, "18:00", "19:30"), slot(7, "09:00", "10:00"),
                        slot(5, "19:00", "21:00")));

        assertThat(shared).isEqualTo("Thứ Ba 19:00–19:30, Thứ Năm 20:00–21:00");
        assertThat(builder.sharedSlots(List.of(slot(2, "19:00", "21:00")), List.of(slot(2, "21:00", "22:00")))).isEmpty();
    }

    // ---- helpers

    private static TimeSlot slot(int day, String start, String end) {
        return new TimeSlot(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static PastDecision decision(boolean accepted) {
        return new PastDecision(9, new BigDecimal("180000"), LearningMode.ONLINE, accepted);
    }

    private static PastProposal proposal() {
        return new PastProposal(new BigDecimal("180000"), 0.5, true);
    }

    private static TeachingCandidate rated(TeachingCandidate candidate, String rating, int reviews) {
        return new TeachingCandidate(candidate.teachingRequestId(), candidate.tutorId(), candidate.tutorUserId(),
                candidate.title(), candidate.expectedPrice(), candidate.teachingMode(), candidate.createdAt(),
                candidate.cosine(), candidate.tutorScore(), candidate.tutorDisplayName(), candidate.tutorAvatarUrl(),
                new BigDecimal(rating), reviews, candidate.districts(), candidate.availabilities(),
                candidate.strengthTags(), candidate.offeredStyleCodes(), candidate.targetSegmentCodes(),
                candidate.rejectedStudentRecently(), candidate.remainingSlots(), candidate.pendingApplications(),
                candidate.tutorDecisions(), candidate.tutorInvitedStudent());
    }

    private static SupplyContext supply(
            BigDecimal expectedPrice, List<TimeSlot> slots, Set<CandidateTag> strengths, Set<String> segments) {
        return new SupplyContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()),
                ModePreference.ANY, AreaScope.DISTRICTS, List.of(DISTRICT), null, DISTRICT, PROVINCE, expectedPrice,
                slots, strengths, Set.of(), segments, 0.5, "", null, Map.of(), List.of());
    }

    private static StudyingCandidate studying(
            double cosine, String maxPrice, LearningMode mode, District district, List<TimeSlot> slots,
            List<WeightedTag> need, String level, int remaining, int pending, List<PastProposal> decisions,
            boolean applied) {
        return new StudyingCandidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Tin học", null, null,
                maxPrice == null ? null : new BigDecimal(maxPrice), mode, "Lớp 9", district, null, cosine, slots, need,
                Set.of(), level, false, remaining, pending, decisions, applied);
    }
}
