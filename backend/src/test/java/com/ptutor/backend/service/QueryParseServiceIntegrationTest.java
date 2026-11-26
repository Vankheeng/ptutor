package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.ParseQueryRequest;
import com.ptutor.backend.client.AiServiceClient.VocabularyTag;
import com.ptutor.backend.dto.response.QueryParseResponse;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.SubjectRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A2 against PostgreSQL with the LLM replaced by a mock: what matters here is that every name and
 * code the model returns is checked against the catalogue and turned into IDs. Rolled back.
 */
@SpringBootTest
@Transactional
class QueryParseServiceIntegrationTest {

    @Autowired SubjectRepository subjectRepository;
    @Autowired GradeRepository gradeRepository;
    @Autowired DistrictRepository districtRepository;
    @Autowired RecommendationTagRepository tagRepository;
    @Autowired EmbeddingTextBuilder textBuilder;
    @Autowired RecommendationSettings settings;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private AiServiceClient aiServiceClient;
    private QueryParseService service;
    private String suffix;
    private UUID provinceId;
    private UUID homeDistrictId;
    private UUID farDistrictId;
    private UUID subjectId;
    private UUID gradeNineId;
    private UUID gradeEightId;
    private UUID userId;

    @AfterEach
    void restoreSettings() {
        ReflectionTestUtils.setField(settings, "parseQueryEnabled", true);
    }

    @BeforeEach
    void setUp() {
        aiServiceClient = mock(AiServiceClient.class);
        service = new QueryParseService(aiServiceClient, subjectRepository, gradeRepository, districtRepository,
                tagRepository, textBuilder, settings, entityManager, objectMapper);
        suffix = UUID.randomUUID().toString().substring(0, 8);
        provinceId = insert("INSERT INTO provinces (id, name) VALUES (?, ?)", "Tỉnh " + suffix);
        UUID otherProvinceId = insert("INSERT INTO provinces (id, name) VALUES (?, ?)", "Tỉnh khác " + suffix);
        homeDistrictId = insert("INSERT INTO districts (id, name, province_id) VALUES (?, ?, ?)",
                "Quận Cầu Giấy " + suffix, provinceId);
        farDistrictId = insert("INSERT INTO districts (id, name, province_id) VALUES (?, ?, ?)",
                "Thủ Đức " + suffix, otherProvinceId);
        // The same name in two provinces: outside the user's own province it is ambiguous.
        insert("INSERT INTO districts (id, name, province_id) VALUES (?, ?, ?)", "Châu Thành " + suffix, otherProvinceId);
        insert("INSERT INTO districts (id, name, province_id) VALUES (?, ?, ?)", "Châu Thành " + suffix,
                insert("INSERT INTO provinces (id, name) VALUES (?, ?)", "Tỉnh thứ ba " + suffix));
        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Toán " + suffix);
        UUID otherSubjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Văn " + suffix);
        gradeNineId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 9)", "Lớp 9 " + suffix);
        gradeEightId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 8)", "Lớp 8 " + suffix);
        insert("INSERT INTO recommendation_tags (id, tag_group, subject_id, code, name) VALUES (?, 'TOPIC', ?, ?, 'Hình học')",
                subjectId, "hinh_" + suffix);
        insert("INSERT INTO recommendation_tags (id, tag_group, subject_id, code, name) VALUES (?, 'TOPIC', ?, ?, 'Nghị luận')",
                otherSubjectId, "nghi_luan_" + suffix);
        userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status, district_id)
                VALUES (?, ?, ?, 'x', 'ACTIVE', ?)
                """, userId, "cid-" + userId, userId + "@example.com", homeDistrictId);
    }

    @Test
    void turnsWhatTheModelUnderstoodIntoFilterValuesWithIds() {
        answer("""
                {"subject": "toán %1$s", "grades": ["Lớp 9 %1$s", "Lớp 8 %1$s"], "mode": "OFFLINE",
                 "area": {"scope": "DISTRICTS", "districts": ["cầu giấy %1$s", "Thủ Đức %1$s", "Châu Thành %1$s", "Quận Lạ"]},
                 "price": {"min": null, "max": 200000},
                 "schedule": [{"dayOfWeek": 2, "start": "19:00", "end": "21:00"},
                              {"dayOfWeek": 4, "start": "05:00", "end": "07:00"},
                              {"dayOfWeek": 9, "start": "19:00", "end": "21:00"},
                              {"dayOfWeek": 5, "start": "tối", "end": "khuya"}],
                 "styleTags": ["kien_nhan", "khong_co_ma_nay"], "goalTags": ["thi_vao_10"], "segmentTags": ["trung_binh"],
                 "topicTags": ["hinh_%1$s", "nghi_luan_%1$s"],
                 "semanticText": "Ưu tiên gia sư nữ", "unparsed": ["gần trường"]}
                """.formatted(suffix));

        QueryParseResponse response = service.parse(userId,
                "Gia sư Toán lớp 9 kiên nhẫn, gọi 0912345678", RecommendationDirection.STUDENT_TO_TUTOR);

        assertThat(response.understood()).isTrue();
        JsonNode filters = response.filters();
        assertThat(filters.path("subjectId").asString()).isEqualTo(subjectId.toString());
        assertThat(filters.path("gradeId").asString()).as("a student searches for one grade")
                .isEqualTo(gradeNineId.toString());
        assertThat(filters.path("learningMode").asString()).isEqualTo("OFFLINE");
        assertThat(filters.path("area").path("scope").asString()).isEqualTo("DISTRICTS");
        assertThat(filters.path("area").path("districtIds")).extracting(JsonNode::asString)
                .as("own province without the prefix, then a unique name elsewhere")
                .containsExactly(homeDistrictId.toString(), farDistrictId.toString());
        assertThat(filters.path("price").path("max").decimalValue()).isEqualByComparingTo("200000");
        assertThat(filters.path("price").path("min").isNull()).isTrue();
        assertThat(filters.path("availabilities")).hasSize(1);
        assertThat(filters.path("availabilities").get(0).path("startTime").asString()).isEqualTo("19:00");
        assertThat(filters.path("styleTagCodes")).extracting(JsonNode::asString).containsExactly("kien_nhan");
        assertThat(filters.path("goalTagCodes")).extracting(JsonNode::asString).containsExactly("thi_vao_10");
        assertThat(filters.path("topicTagCodes")).extracting(JsonNode::asString)
                .as("only topics of the subject that was recognised").containsExactly("hinh_" + suffix);
        assertThat(filters.has("targetSegmentCodes")).isFalse();
        assertThat(response.semanticText())
                .as("the user's own words without contact details; whatever the model writes is ignored")
                .isEqualTo("Gia sư Toán lớp 9 kiên nhẫn, gọi");
        assertThat(response.notes()).containsExactlyInAnyOrder(
                "Câu nêu nhiều lớp; đã chọn Lớp 9 " + suffix + ".",
                "Không tìm thấy quận / huyện \"Châu Thành " + suffix + "\".",
                "Không tìm thấy quận / huyện \"Quận Lạ\".",
                "Chưa nhận ra: \"gần trường\".");

        ArgumentCaptor<ParseQueryRequest> sent = ArgumentCaptor.forClass(ParseQueryRequest.class);
        verify(aiServiceClient).parseQuery(sent.capture(), any());
        assertThat(sent.getValue().text()).as("contact details never leave the backend")
                .isEqualTo("Gia sư Toán lớp 9 kiên nhẫn, gọi");
        assertThat(sent.getValue().direction()).isEqualTo("STUDENT_TO_TUTOR");
        assertThat(sent.getValue().catalog().subjects()).contains("Toán " + suffix);
        assertThat(sent.getValue().catalog().districtsInUserProvince()).containsExactly("Quận Cầu Giấy " + suffix);
        assertThat(sent.getValue().vocabulary().get("TOPIC")).extracting(VocabularyTag::name)
                .contains("Hình học (Toán " + suffix + ")");
        assertThat(sent.getValue().vocabulary()).containsKeys("STYLE", "GOAL", "SEGMENT");
    }

    @Test
    void aTutorsSentenceFillsTheTutorForm() {
        answer("""
                {"subject": "Toán %1$s", "grades": ["Lớp 8 %1$s", "Lớp 9 %1$s"], "mode": "ONLINE",
                 "area": {"scope": "PROVINCE", "districts": []}, "price": {"min": 150000, "max": 210000},
                 "schedule": [], "styleTags": ["kien_nhan"], "goalTags": [], "segmentTags": ["trung_binh", "kha"],
                 "topicTags": [], "semanticText": "", "unparsed": []}
                """.formatted(suffix));

        QueryParseResponse response = service.parse(userId, "tìm học viên toán 8, 9", RecommendationDirection.TUTOR_TO_STUDENT);

        JsonNode filters = response.filters();
        assertThat(filters.path("gradeIds")).extracting(JsonNode::asString)
                .containsExactly(gradeEightId.toString(), gradeNineId.toString());
        assertThat(filters.has("gradeId")).isFalse();
        assertThat(filters.path("learningMode").asString()).isEqualTo("ONLINE");
        assertThat(filters.path("area").path("scope").asString()).isEqualTo("PROVINCE");
        assertThat(filters.path("area").path("provinceId").asString()).isEqualTo(provinceId.toString());
        assertThat(filters.path("expectedPrice").decimalValue()).as("the middle of the range").isEqualByComparingTo("180000");
        assertThat(filters.path("targetSegmentCodes")).extracting(JsonNode::asString).containsExactly("trung_binh", "kha");
        assertThat(filters.has("styleTagCodes")).as("a tutor's form has no style filter").isFalse();
        assertThat(response.semanticText()).isEqualTo("tìm học viên toán 8, 9");
        assertThat(response.notes()).isEmpty();
    }

    @Test
    void aPlaceTheModelCouldNotMatchIsStillLookedUpNationwide() {
        answer("""
                {"subject": null, "grades": [], "mode": "OFFLINE", "area": {"scope": null, "districts": []},
                 "price": {"min": null, "max": null}, "schedule": [], "styleTags": [], "goalTags": [],
                 "segmentTags": [], "topicTags": [], "semanticText": "", "unparsed": ["cờ vua", "Thủ Đức %s"]}
                """.formatted(suffix));

        QueryParseResponse response = service.parse(userId, "thầy dạy cờ vua ở Thủ Đức", RecommendationDirection.STUDENT_TO_TUTOR);

        assertThat(response.filters().path("area").path("districtIds")).extracting(JsonNode::asString)
                .containsExactly(farDistrictId.toString());
        assertThat(response.filters().has("subjectId")).isFalse();
        assertThat(response.notes()).containsExactly("Chưa nhận ra: \"cờ vua\".");
    }

    @Test
    void whenTheModelDoesNotAnswerTheWholeSentenceBecomesFreeTextAndNothingIsCached() {
        when(aiServiceClient.parseQuery(any(), any())).thenReturn(Optional.empty());

        QueryParseResponse failed = service.parse(userId, "Gia sư Toán, a@b.vn", RecommendationDirection.STUDENT_TO_TUTOR);

        assertThat(failed.understood()).isFalse();
        assertThat(failed.filters().isEmpty()).isTrue();
        assertThat(failed.semanticText()).isEqualTo("Gia sư Toán,");
        assertThat(failed.notes()).containsExactly(QueryParseService.NOT_UNDERSTOOD);
        assertThat(service.recall(provinceId, "Gia sư Toán, a@b.vn", RecommendationDirection.STUDENT_TO_TUTOR)).isEmpty();

        service.parse(userId, "Gia sư Toán, a@b.vn", RecommendationDirection.STUDENT_TO_TUTOR);
        verify(aiServiceClient, times(2)).parseQuery(any(), any());
    }

    @Test
    void theSameSentenceIsAnsweredFromTheCacheAndCanBeRecalledForTheLog() {
        answer("""
                {"subject": "Toán %s", "grades": [], "mode": null, "area": {"scope": null, "districts": []},
                 "price": {"min": null, "max": null}, "schedule": [], "styleTags": [], "goalTags": [],
                 "segmentTags": [], "topicTags": [], "semanticText": "", "unparsed": []}
                """.formatted(suffix));
        String sentence = "Tìm gia sư Toán " + suffix;

        QueryParseResponse first = service.parse(userId, sentence, RecommendationDirection.STUDENT_TO_TUTOR);
        QueryParseResponse again = service.parse(userId, "  tìm GIA SƯ toán " + suffix + " ", RecommendationDirection.STUDENT_TO_TUTOR);
        service.parse(userId, sentence, RecommendationDirection.TUTOR_TO_STUDENT);

        assertThat(again).isSameAs(first);
        verify(aiServiceClient, times(2)).parseQuery(any(), any());
        assertThat(service.recall(provinceId, sentence, RecommendationDirection.STUDENT_TO_TUTOR)).isPresent()
                .get().extracting(node -> node.path("filters").path("subjectId").asString()).isEqualTo(subjectId.toString());
        assertThat(service.recall(UUID.randomUUID(), sentence, RecommendationDirection.STUDENT_TO_TUTOR))
                .as("another province has other districts").isEmpty();
    }

    @Test
    void doesNotCallTheModelWhenSwitchedOffOrWhenOnlyContactDetailsWereTyped() {
        ReflectionTestUtils.setField(settings, "parseQueryEnabled", false);
        assertThat(service.parse(userId, "Gia sư Toán", RecommendationDirection.STUDENT_TO_TUTOR).semanticText())
                .isEqualTo("Gia sư Toán");
        ReflectionTestUtils.setField(settings, "parseQueryEnabled", true);

        QueryParseResponse empty = service.parse(userId, "0912345678", RecommendationDirection.STUDENT_TO_TUTOR);

        assertThat(empty.semanticText()).isNull();
        assertThat(empty.notes()).isEmpty();
        verify(aiServiceClient, never()).parseQuery(any(), any());
    }

    @ParameterizedTest
    @CsvSource({ "Quận Cầu Giấy, cau giay", "  HUYỆN  Đông Anh , dong anh", "TP. Thủ Đức, thu duc", "Đống Đa, dong da",
        "Thị xã Sơn Tây, son tay" })
    void namesAreComparedWithoutAccentsCaseOrAdministrativePrefix(String written, String expected) {
        assertThat(QueryParseService.normalize(written)).isEqualTo(expected);
    }

    private void answer(String json) {
        when(aiServiceClient.parseQuery(any(), any())).thenReturn(Optional.of(objectMapper.readTree(json)));
    }

    private UUID insert(String sql, Object... args) {
        UUID id = UUID.randomUUID();
        Object[] all = new Object[args.length + 1];
        all[0] = id;
        System.arraycopy(args, 0, all, 1, args.length);
        jdbc.update(sql, all);
        return id;
    }
}
