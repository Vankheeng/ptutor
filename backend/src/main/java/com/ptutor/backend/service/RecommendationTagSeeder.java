package com.ptutor.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.RecommendationTag;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.repository.RecommendationTagRepository;
import com.ptutor.backend.repository.SubjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seeds the tag vocabulary used by enrichment (A1) and by the search filter chips (design 04, 4.2).
 *
 * <p>Idempotent like {@link CatalogDataInitializer}: a tag is only inserted when its code does not
 * exist yet, so an administrator can rename, deactivate or extend the vocabulary afterwards.
 * TOPIC tags are created only for subjects that exist in the catalogue.</p>
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class RecommendationTagSeeder implements ApplicationRunner {

    private static final List<Seed> STYLE = List.of(
            new Seed("kien_nhan", "Kiên nhẫn, nhẹ nhàng", null),
            new Seed("nghiem_khac", "Nghiêm khắc, kỷ luật", null),
            new Seed("vui_ve", "Vui vẻ, tạo hứng thú", null),
            new Seed("giai_thich_ky", "Giải thích kỹ bản chất", null),
            new Seed("cham_chac", "Đi chậm, chắc nền tảng", null),
            new Seed("luyen_tap_nhieu", "Nhiều bài tập, luyện đề", null),
            new Seed("tu_duy", "Rèn tư duy, giải bài khó", null),
            new Seed("ke_hoach_ro", "Có lộ trình, kiểm tra định kỳ", null),
            new Seed("tuong_tac_cong_nghe", "Dùng công cụ trực tuyến, tài liệu số", null),
            new Seed("giao_tiep", "Tăng phản xạ giao tiếp (môn ngoại ngữ)", null));

    private static final List<Seed> SEGMENT = List.of(
            new Seed("mat_goc", "Mất gốc", null),
            new Seed("trung_binh", "Trung bình", null),
            new Seed("kha", "Khá", null),
            new Seed("gioi", "Giỏi", null),
            new Seed("can_kem_cap", "Cần kèm cặp sát sao", null));

    private static final List<Seed> GOAL = List.of(
            new Seed("cai_thien_diem", "Cải thiện điểm trên lớp", null),
            new Seed("thi_vao_10", "Thi vào lớp 10", null),
            new Seed("thi_chuyen", "Thi vào trường chuyên", null),
            new Seed("thi_tot_nghiep_thpt", "Thi tốt nghiệp THPT", null),
            new Seed("thi_dai_hoc", "Thi đại học", null),
            new Seed("hoc_sinh_gioi", "Thi học sinh giỏi", null),
            new Seed("chung_chi_ngoai_ngu", "Thi chứng chỉ ngoại ngữ", null),
            new Seed("hoc_vuot", "Học vượt chương trình", null));

    /** Codes are unique across subjects, so a code alone identifies a topic. Parents come first. */
    private static final List<SubjectTopics> TOPICS = List.of(
            new SubjectTopics("Toán", List.of(
                    new Seed("dai_so", "Đại số", null),
                    new Seed("phuong_trinh_he_phuong_trinh", "Phương trình, hệ phương trình", "dai_so"),
                    new Seed("ham_so_do_thi", "Hàm số, đồ thị", "dai_so"),
                    new Seed("hinh_hoc_phang", "Hình học phẳng", null),
                    new Seed("chung_minh", "Bài chứng minh", "hinh_hoc_phang"),
                    new Seed("hinh_hoc_khong_gian", "Hình học không gian", null),
                    new Seed("giai_tich", "Giải tích (lớp 11–12)", null),
                    new Seed("xac_suat_thong_ke", "Xác suất, thống kê", null))),
            new SubjectTopics("Tiếng Anh", List.of(
                    new Seed("ngu_phap", "Ngữ pháp", null),
                    new Seed("tu_vung", "Từ vựng", null),
                    new Seed("doc_hieu", "Đọc hiểu", null),
                    new Seed("nghe", "Nghe", null),
                    new Seed("noi", "Nói", null),
                    new Seed("viet", "Viết", null),
                    new Seed("phat_am", "Phát âm", null))),
            new SubjectTopics("Ngữ văn", List.of(
                    new Seed("doc_hieu_van_ban", "Đọc hiểu văn bản", null),
                    new Seed("nghi_luan_xa_hoi", "Nghị luận xã hội", null),
                    new Seed("nghi_luan_van_hoc", "Nghị luận văn học", null),
                    new Seed("tieng_viet_ngu_phap", "Tiếng Việt, ngữ pháp", null),
                    new Seed("ky_nang_viet_van", "Kỹ năng viết, diễn đạt", null),
                    new Seed("tac_pham_van_hoc", "Phân tích tác phẩm văn học", null))),
            new SubjectTopics("Vật lý", List.of(
                    new Seed("co_hoc", "Cơ học", null),
                    new Seed("nhiet_hoc", "Nhiệt học", null),
                    new Seed("dien_hoc", "Điện học, điện từ", null),
                    new Seed("quang_hoc", "Quang học", null),
                    new Seed("dao_dong_song", "Dao động, sóng", null),
                    new Seed("vat_ly_hat_nhan", "Vật lý hạt nhân", null))),
            new SubjectTopics("Hóa học", List.of(
                    new Seed("hoa_vo_co", "Hoá vô cơ", null),
                    new Seed("hoa_huu_co", "Hoá hữu cơ", null),
                    new Seed("can_bang_phuong_trinh", "Cân bằng phương trình, tính theo phương trình", null),
                    new Seed("dung_dich_nong_do", "Dung dịch, nồng độ", null),
                    new Seed("phan_ung_oxi_hoa_khu", "Phản ứng oxi hoá – khử", null),
                    new Seed("bai_tap_nhan_biet", "Nhận biết, tách chất", null))));

    private final RecommendationTagRepository tagRepository;
    private final SubjectRepository subjectRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(RecommendationTagGroup.STYLE, null, STYLE);
        seed(RecommendationTagGroup.SEGMENT, null, SEGMENT);
        seed(RecommendationTagGroup.GOAL, null, GOAL);
        TOPICS.forEach(topics -> subjectRepository.findByNameIgnoreCase(topics.subjectName())
                .ifPresent(subject -> seed(RecommendationTagGroup.TOPIC, subject, topics.tags())));
    }

    private void seed(RecommendationTagGroup group, Subject subject, List<Seed> seeds) {
        for (Seed seed : seeds) {
            if (find(group, seed.code()).isPresent()) {
                continue;
            }
            RecommendationTag parent = seed.parentCode() == null ? null : find(group, seed.parentCode()).orElse(null);
            tagRepository.save(RecommendationTag.builder()
                    .tagGroup(group).subject(subject).parent(parent).code(seed.code()).name(seed.name()).build());
        }
    }

    /** Any status counts: a tag an administrator deactivated must not be re-created. */
    private Optional<RecommendationTag> find(RecommendationTagGroup group, String code) {
        return tagRepository.findFirstByTagGroupAndCode(group, code);
    }

    private record Seed(String code, String name, String parentCode) {
    }

    private record SubjectTopics(String subjectName, List<Seed> tags) {
    }
}
