package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.client.AiServiceClient.EnrichRequest;
import com.ptutor.backend.client.AiServiceClient.EnrichResult;
import com.ptutor.backend.client.AiServiceClient.SuggestTagsRequest;
import com.ptutor.backend.client.AiServiceClient.SuggestedTag;
import com.ptutor.backend.client.AiServiceClient.TagSuggestions;
import com.ptutor.backend.client.AiServiceClient.VocabularyTag;
import com.ptutor.backend.dto.request.RecommendationTagCreateRequest;
import com.ptutor.backend.dto.request.RecommendationTagCreateRequest.Item;
import com.ptutor.backend.dto.request.RecommendationTagUpdateRequest;
import com.ptutor.backend.dto.response.RecommendationTagResponse;
import com.ptutor.backend.dto.response.RecommendationTagSuggestionResponse;
import com.ptutor.backend.dto.response.RecommendationTagSuggestionResponse.Suggestion;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.EnrichmentResultRepository;
import com.ptutor.backend.repository.RecommendationEntityTagRepository;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;

import jakarta.persistence.EntityManager;

/**
 * Tag administration against PostgreSQL with the AI service replaced by a mock. Uses the
 * vocabulary seeded at start-up plus subjects created for the test. Everything is rolled back.
 */
@SpringBootTest
@Transactional
class RecommendationTagServiceIntegrationTest {

    @Autowired RecommendationTagRepository tagRepository;
    @Autowired RecommendationEntityTagRepository entityTagRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired AiEnrichmentJobRepository jobRepository;
    @Autowired EnrichmentResultRepository resultRepository;
    @Autowired AiJobQueueService queueService;
    @Autowired EmbeddingTextBuilder textBuilder;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private AiServiceClient aiServiceClient;
    private RecommendationTagService service;
    private UUID subjectId;
    private UUID otherSubjectId;
    private UUID tutorId;
    private UUID openPosting;
    private UUID draftPosting;
    private UUID otherSubjectPosting;
    private String prefix;

    @BeforeEach
    void setUp() {
        aiServiceClient = mock(AiServiceClient.class);
        service = new RecommendationTagService(tagRepository, entityTagRepository, subjectRepository, jobRepository,
                queueService, aiServiceClient, textBuilder, entityManager, 30_000);
        // Jobs committed by a developer's running application must not leak into the assertions.
        jdbc.update("DELETE FROM ai_enrichment_jobs");

        prefix = "t" + UUID.randomUUID().toString().substring(0, 8) + "_";
        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Sinh-" + UUID.randomUUID());
        otherSubjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')",
                "Sử-" + UUID.randomUUID());
        tutorId = insert("INSERT INTO tutors (id, user_id) VALUES (?, ?)", insertUser());
        openPosting = posting(subjectId, "Sinh 9 di truyền, zalo 0912345678", "OPEN");
        draftPosting = posting(subjectId, "Sinh 8", "DRAFT");
        otherSubjectPosting = posting(otherSubjectId, "Sử 9", "OPEN");
    }

    @Test
    void createsTypedTopicsWithParentsFromTheSameRequestAndReEnrichesLivePostingsOfThatSubjectOnly() {
        List<RecommendationTagResponse> created = service.create(new RecommendationTagCreateRequest(List.of(
                topic("di_truyen", "Di truyền học", "Gen, ADN, quy luật Menđen", null),
                topic("lai_mot_cap", " Lai một cặp tính trạng ", "  ", prefix + "di_truyen"))));

        assertThat(created).extracting(RecommendationTagResponse::code)
                .containsExactly(prefix + "di_truyen", prefix + "lai_mot_cap");
        RecommendationTagResponse child = created.get(1);
        assertThat(child.name()).isEqualTo("Lai một cặp tính trạng");
        assertThat(child.description()).isNull();
        assertThat(child.parentCode()).isEqualTo(prefix + "di_truyen");
        assertThat(child.subjectId()).isEqualTo(subjectId);
        assertThat(child.status()).isEqualTo(RecommendationTagStatus.ACTIVE);
        assertThat(enrichJobs()).as("only OPEN / MATCHED postings of the subject").containsExactly(openPosting);
    }

    @Test
    void aNewTopicChangesThePostingHashSoItIsNotSkippedAsUnchanged() {
        EnrichmentJobProcessor processor = new EnrichmentJobProcessor(jobRepository, tagRepository, resultRepository,
                queueService, textBuilder, aiServiceClient, entityManager, transactionManager, 25_000, 3, 0.6);
        when(aiServiceClient.enrich(any(), any()))
                .thenReturn(Optional.of(new EnrichResult("gpt-6-luna", List.of(), List.of())));
        service.create(new RecommendationTagCreateRequest(List.of(topic("di_truyen", "Di truyền học", null, null))));
        assertThat(runEnrichment(processor)).isEqualTo(Outcome.DONE);
        jdbc.update("DELETE FROM ai_enrichment_jobs");

        service.create(new RecommendationTagCreateRequest(List.of(topic("sinh_thai", "Sinh thái học", null, null))));
        Outcome afterNewTopic = runEnrichment(processor);

        assertThat(afterNewTopic).isEqualTo(Outcome.DONE);
        ArgumentCaptor<EnrichRequest> sent = ArgumentCaptor.forClass(EnrichRequest.class);
        org.mockito.Mockito.verify(aiServiceClient, org.mockito.Mockito.times(2)).enrich(sent.capture(), any());
        assertThat(sent.getAllValues().get(1).vocabulary().get("TOPIC")).extracting(VocabularyTag::code)
                .containsExactlyInAnyOrder(prefix + "di_truyen", prefix + "sinh_thai");
    }

    @Test
    void styleAndGoalTagsHaveNoSubjectAndDoNotReRunAnything() {
        List<RecommendationTagResponse> created = service.create(new RecommendationTagCreateRequest(List.of(
                new Item(RecommendationTagGroup.STYLE, null, prefix + "hai_huoc", "Hài hước", null, null),
                new Item(RecommendationTagGroup.GOAL, null, prefix + "thi_hsg", "Thi học sinh giỏi", null, null))));

        assertThat(created).allSatisfy(tag -> assertThat(tag.subjectId()).isNull());
        assertThat(enrichJobs()).isEmpty();
    }

    @Test
    void rejectsTheWholeRequestWhenOneItemIsInvalid() {
        assertRejected("TAG_CODE_ALREADY_EXISTS", topic("a_moi", "A", null, null),
                new Item(RecommendationTagGroup.STYLE, null, "kien_nhan", "Trùng với tag đã seed", null, null));
        assertRejected("TAG_CODE_ALREADY_EXISTS", topic("lap_lai", "A", null, null), topic("lap_lai", "B", null, null));
        assertRejected("SEGMENT_TAGS_ARE_FIXED",
                new Item(RecommendationTagGroup.SEGMENT, null, prefix + "xuat_sac", "Xuất sắc", null, null));
        assertRejected("SUBJECT_REQUIRED",
                new Item(RecommendationTagGroup.TOPIC, null, prefix + "khong_mon", "Không môn", null, null));
        assertRejected("SUBJECT_NOT_ALLOWED",
                new Item(RecommendationTagGroup.STYLE, subjectId, prefix + "co_mon", "Có môn", null, null));
        assertRejected("INVALID_SUBJECT",
                new Item(RecommendationTagGroup.TOPIC, UUID.randomUUID(), prefix + "mon_la", "Môn lạ", null, null));
        assertRejected("INVALID_TAG_PARENT", topic("con_mo_coi", "Con", null, "khong_ton_tai"));
        assertRejected("INVALID_TAG_PARENT", topic("con_khac_nhom", "Con", null, "kien_nhan"));
        assertRejected("INVALID_TAG_PARENT",
                topic("ong", "Ông", null, null), topic("cha", "Cha", null, prefix + "ong"),
                topic("chau", "Cháu", null, prefix + "cha"));

        entityManager.clear();
        assertThat(tagRepository.existsByCode(prefix + "a_moi")).as("nothing of a rejected request is kept").isFalse();
        assertThat(tagRepository.existsByCode(prefix + "ong")).isFalse();
    }

    @Test
    void renamingReEmbedsWhoeverCarriesTheTagWithoutCallingTheLlm() {
        UUID tagId = service.create(new RecommendationTagCreateRequest(
                List.of(topic("di_truyen", "Di truyền", null, null)))).get(0).id();
        insert("""
                INSERT INTO recommendation_entity_tags (id, entity_type, entity_id, tag_id, role, source)
                VALUES (?, 'TEACHING_REQUEST', ?, ?, 'STRENGTH', 'LLM')
                """, openPosting, tagId);
        jdbc.update("DELETE FROM ai_enrichment_jobs");
        entityManager.clear();

        service.update(tagId, new RecommendationTagUpdateRequest("Di truyền", "Chỉ đổi mô tả", null));
        assertThat(jobs()).as("the description is not part of the embedded text").isEmpty();

        RecommendationTagResponse renamed =
                service.update(tagId, new RecommendationTagUpdateRequest("Di truyền học", "Chỉ đổi mô tả", null));

        assertThat(renamed.code()).as("the code never changes").isEqualTo(prefix + "di_truyen");
        assertThat(renamed.name()).isEqualTo("Di truyền học");
        assertThat(jobs()).containsExactly("EMBED:TEACHING_REQUEST:" + openPosting);
    }

    @Test
    void switchingATopicOffHidesItFromUsersAndReEnrichesItsSubject() {
        UUID tagId = service.create(new RecommendationTagCreateRequest(
                List.of(topic("di_truyen", "Di truyền", null, null)))).get(0).id();
        jdbc.update("DELETE FROM ai_enrichment_jobs");

        service.changeStatus(tagId, RecommendationTagStatus.INACTIVE);

        assertThat(enrichJobs()).containsExactly(openPosting);
        assertThat(service.findSelectable(RecommendationTagGroup.TOPIC, subjectId)).isEmpty();
        assertThat(service.findForAdmin(RecommendationTagGroup.TOPIC, subjectId, null))
                .extracting(RecommendationTagResponse::status).containsExactly(RecommendationTagStatus.INACTIVE);
        jdbc.update("DELETE FROM ai_enrichment_jobs");

        service.changeStatus(tagId, RecommendationTagStatus.INACTIVE);
        assertThat(enrichJobs()).as("no change, no work").isEmpty();
        service.changeStatus(tagId, RecommendationTagStatus.ACTIVE);
        assertThat(enrichJobs()).containsExactly(openPosting);
    }

    @Test
    void levelTagsCannotBeSwitchedOff() {
        UUID segment = jdbc.queryForObject(
                "SELECT id FROM recommendation_tags WHERE tag_group = 'SEGMENT' AND code = 'mat_goc'", UUID.class);

        assertThatThrownBy(() -> service.changeStatus(segment, RecommendationTagStatus.INACTIVE))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("SEGMENT_TAGS_ARE_FIXED"));
    }

    @Test
    void usersSeeTopicsOnlyTogetherWithTheirSubject() {
        service.create(new RecommendationTagCreateRequest(List.of(topic("di_truyen", "Di truyền", null, null))));

        assertThat(codes(service.findSelectable(null, null))).contains("kien_nhan", "thi_vao_10", "mat_goc")
                .doesNotContain(prefix + "di_truyen");
        assertThat(codes(service.findSelectable(null, subjectId))).contains("kien_nhan", prefix + "di_truyen");
        assertThat(codes(service.findSelectable(RecommendationTagGroup.TOPIC, subjectId)))
                .containsExactly(prefix + "di_truyen");
        assertThat(service.findSelectable(RecommendationTagGroup.TOPIC, otherSubjectId)).isEmpty();
        assertThat(codes(service.findSelectable(RecommendationTagGroup.STYLE, null))).contains("kien_nhan")
                .doesNotContain("thi_vao_10");
    }

    @Test
    void suggestionsSendScrubbedSamplesAndKeepOnlyUsableNewCodesWithoutStoringAnything() {
        service.create(new RecommendationTagCreateRequest(List.of(topic("di_truyen", "Di truyền", null, null))));
        long before = tagRepository.count();
        when(aiServiceClient.suggestTags(any(), any())).thenReturn(Optional.of(new TagSuggestions("gpt-6-luna", List.of(
                new SuggestedTag(prefix + "sinh_thai", " Sinh thái học ", "Quần thể, quần xã", null),
                new SuggestedTag(prefix + "chuoi_thuc_an", "Chuỗi thức ăn", "", prefix + "sinh_thai"),
                new SuggestedTag(prefix + "adn", "ADN", "Cấu trúc ADN", prefix + "di_truyen"),
                new SuggestedTag(prefix + "mo_coi", "Mồ côi", "x", "cha_khong_ton_tai"),
                new SuggestedTag(prefix + "di_truyen", "Đã có", "x", null),
                new SuggestedTag(prefix + "sinh_thai", "Lặp lại", "x", null),
                new SuggestedTag("Mã Sai!", "Mã sai", "x", null),
                new SuggestedTag(prefix + "khong_ten", "  ", "x", null)))));

        RecommendationTagSuggestionResponse response = service.suggestTopics(subjectId, 7);

        assertThat(response.model()).isEqualTo("gpt-6-luna");
        assertThat(response.subjectName()).startsWith("Sinh-");
        assertThat(response.suggestions()).containsExactly(
                new Suggestion(prefix + "sinh_thai", "Sinh thái học", "Quần thể, quần xã", null),
                new Suggestion(prefix + "chuoi_thuc_an", "Chuỗi thức ăn", null, prefix + "sinh_thai"),
                new Suggestion(prefix + "adn", "ADN", "Cấu trúc ADN", prefix + "di_truyen"),
                new Suggestion(prefix + "mo_coi", "Mồ côi", "x", null));
        assertThat(tagRepository.count()).as("suggestions are never stored").isEqualTo(before);

        ArgumentCaptor<SuggestTagsRequest> sent = ArgumentCaptor.forClass(SuggestTagsRequest.class);
        org.mockito.Mockito.verify(aiServiceClient).suggestTags(sent.capture(), any());
        assertThat(sent.getValue().subject()).startsWith("Sinh-");
        assertThat(sent.getValue().maxTags()).isEqualTo(7);
        assertThat(sent.getValue().existing()).extracting(VocabularyTag::code).containsExactly(prefix + "di_truyen");
        assertThat(sent.getValue().samples()).as("live postings of this subject only, without contact details")
                .containsExactly("Sinh 9 di truyền, zalo");
    }

    @Test
    void suggestionsFailCleanlyWhenTheLlmIsUnavailableOrTheSubjectIsNotInTheCatalogue() {
        when(aiServiceClient.suggestTags(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggestTopics(subjectId, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("TAG_SUGGESTION_UNAVAILABLE"));
        assertThatThrownBy(() -> service.suggestTopics(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_SUBJECT"));
    }

    // ---------------------------------------------------------------- helpers

    private Item topic(String code, String name, String description, String parentCode) {
        return new Item(RecommendationTagGroup.TOPIC, subjectId, prefix + code, name, description, parentCode);
    }

    private void assertRejected(String code, Item... items) {
        assertThatThrownBy(() -> service.create(new RecommendationTagCreateRequest(Arrays.asList(items))))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
        // What a real request loses with its rolled-back transaction.
        entityManager.clear();
        jdbc.update("DELETE FROM recommendation_tags WHERE code LIKE ?", prefix + "%");
    }

    private Outcome runEnrichment(EnrichmentJobProcessor processor) {
        entityManager.flush();
        return processor.process(processor.claim(1_000).stream()
                .filter(job -> job.entityId().equals(openPosting)).findFirst().orElseThrow());
    }

    private List<String> codes(List<RecommendationTagResponse> tags) {
        return tags.stream().map(RecommendationTagResponse::code).toList();
    }

    private List<UUID> enrichJobs() {
        entityManager.flush();
        return jdbc.queryForList(
                "SELECT entity_id FROM ai_enrichment_jobs WHERE job_type = 'ENRICH' AND status = 'PENDING'", UUID.class);
    }

    private List<String> jobs() {
        entityManager.flush();
        return jdbc.queryForList("SELECT * FROM ai_enrichment_jobs").stream()
                .map((Map<String, Object> job) -> job.get("job_type") + ":" + job.get("entity_type") + ":"
                        + job.get("entity_id"))
                .toList();
    }

    private UUID posting(UUID subject, String title, String status) {
        return insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, title, teaching_mode, status)
                VALUES (?, ?, ?, ?, 'ONLINE', ?)
                """, tutorId, subject, title, status);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, citizen_id, email, password, status)
                VALUES (?, ?, ?, 'x', 'ACTIVE')
                """, id, "cid-" + id, id + "@example.com");
        return id;
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
