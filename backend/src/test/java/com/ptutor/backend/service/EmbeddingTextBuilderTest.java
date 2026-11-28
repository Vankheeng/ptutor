package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.GradeTeachingRequest;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;

class EmbeddingTextBuilderTest {

    private final EmbeddingTextBuilder builder = new EmbeddingTextBuilder();

    @Test
    void teachingRequestTextFollowsThePostingThenTutorTemplate() {
        TeachingRequest request = teachingRequest(fullTutor());

        assertThat(builder.forTeachingRequest(request)).isEqualTo("""
                Môn: Toán. dạy: Lớp 8, Lớp 9. Tin đăng: Luyện thi vào 10. Ôn theo chuyên đề. Học thử 1 buổi
                Giới thiệu: 5 năm dạy THCS. Học vấn: ĐH Sư phạm Hà Nội
                Phương pháp: Luyện đề, sửa lỗi trình bày. Thế mạnh: Hình học, Đại số
                Đối tượng: Học sinh mất gốc. Phong cách: Kiên nhẫn, kỹ tính""");
    }

    @Test
    void studyingRequestTextFollowsThePostingThenStudentTemplate() {
        Student student = Student.builder()
                .goalsDescription("Đỗ trường chuyên")
                .weakPoints("Hình học không gian")
                .currentLevel("Trung bình khá")
                .learningStyle("Thích làm nhiều bài tập")
                .personalityTags("Ít nói")
                .build();
        StudyingRequest request = StudyingRequest.builder()
                .student(student)
                .subject(Subject.builder().name("Toán").build())
                .grade(Grade.builder().name("Lớp 9").build())
                .learningGoals("Thi vào 10 đạt 8 điểm")
                .title("Tìm gia sư Toán 9")
                .description("Học 2 buổi mỗi tuần")
                .build();

        assertThat(builder.forStudyingRequest(request)).isEqualTo("""
                Môn: Toán. Lớp 9. Mục tiêu: Thi vào 10 đạt 8 điểm. Tin đăng: Tìm gia sư Toán 9. Học 2 buổi mỗi tuần
                Học sinh: Đỗ trường chuyên. Điểm yếu: Hình học không gian. Trình độ: Trung bình khá
                Phong cách mong muốn: Thích làm nhiều bài tập; Ít nói""");
    }

    @Test
    void profileTextHasNoPostingPart() {
        assertThat(builder.forTutor(fullTutor())).startsWith("Giới thiệu: 5 năm dạy THCS").doesNotContain("Tin đăng");
        assertThat(builder.forStudent(Student.builder().weakPoints("Ngữ pháp").build()))
                .isEqualTo("Điểm yếu: Ngữ pháp");
    }

    @Test
    void leavesOutEmptyFieldsAndEmptyLines() {
        TeachingRequest request = TeachingRequest.builder()
                .customSubjectName("  Cờ vua ")
                .title("Dạy cờ vua thiếu nhi")
                .description("   ")
                .tutor(Tutor.builder().introduction("").education("Kiện tướng quốc gia").build())
                .build();

        assertThat(builder.forTeachingRequest(request)).isEqualTo("""
                Môn: Cờ vua. Tin đăng: Dạy cờ vua thiếu nhi
                Học vấn: Kiện tướng quốc gia""");
    }

    @Test
    void returnsEmptyTextWhenThereIsNothingToEmbed() {
        assertThat(builder.forTutor(Tutor.builder().build())).isEmpty();
        assertThat(builder.forStudent(Student.builder().introduction("không dùng trường này").build())).isEmpty();
    }

    @Test
    void neverIncludesNameOrContactDetailsOfTheUser() {
        User user = User.builder()
                .firstName("Nguyễn").lastName("Văn An").email("an.nguyen@example.com").phone("0912345678")
                .detailAddress("12 Hàng Bài").build();
        Tutor tutor = Tutor.builder().user(user).introduction("Dạy Toán").build();

        assertThat(builder.forTutor(tutor)).isEqualTo("Giới thiệu: Dạy Toán");
    }

    @Test
    void removesPhoneNumbersAndEmailsTypedIntoFreeText() {
        Tutor tutor = Tutor.builder()
                .introduction("Liên hệ 0912 345 678 hoặc +84912345678, mail gia.su+toan@mail.edu.vn nhé")
                .education("Tốt nghiệp năm 2019, 8.5 IELTS, dạy 120 học sinh")
                .build();

        String text = builder.forTutor(tutor);

        assertThat(text).doesNotContain("0912").doesNotContain("84912345678").doesNotContain("@");
        assertThat(text).contains("Liên hệ").contains("2019").contains("8.5 IELTS").contains("120 học sinh");
    }

    @Test
    void collapsesWhitespaceAndAvoidsDoubleFullStops() {
        Tutor tutor = Tutor.builder().introduction("Dạy   Toán\n\nvà Lý.").education("ĐHSP.").build();

        assertThat(builder.forTutor(tutor)).isEqualTo("Giới thiệu: Dạy Toán và Lý. Học vấn: ĐHSP");
    }

    @Test
    void truncatesVeryLongText() {
        Tutor tutor = Tutor.builder().introduction("a".repeat(10_000)).build();

        assertThat(builder.forTutor(tutor)).hasSize(EmbeddingTextBuilder.MAX_CHARS);
    }

    @Test
    void hashIsStableSha256AndChangesWithTheText() {
        assertThat(builder.hash("Toán 9"))
                .hasSize(64)
                .isEqualTo(builder.hash("Toán 9"))
                .isNotEqualTo(builder.hash("Toán 8"));
        assertThat(builder.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private Tutor fullTutor() {
        return Tutor.builder()
                .introduction("5 năm dạy THCS")
                .education("ĐH Sư phạm Hà Nội")
                .teachingMethodology("Luyện đề, sửa lỗi trình bày")
                .strengthSubjects("Hình học, Đại số")
                .targetStudentType("Học sinh mất gốc")
                .teachingStyleTags("Kiên nhẫn, kỹ tính")
                .build();
    }

    private TeachingRequest teachingRequest(Tutor tutor) {
        TeachingRequest request = TeachingRequest.builder()
                .tutor(tutor)
                .subject(Subject.builder().name("Toán").build())
                .title("Luyện thi vào 10")
                .description("Ôn theo chuyên đề.")
                .note("Học thử 1 buổi")
                .build();
        request.getGradeAssociations().addAll(List.of(
                GradeTeachingRequest.builder().teachingRequest(request).grade(Grade.builder().name("Lớp 8").build())
                        .build(),
                GradeTeachingRequest.builder().teachingRequest(request).grade(Grade.builder().name("Lớp 9").build())
                        .build()));
        return request;
    }
}
