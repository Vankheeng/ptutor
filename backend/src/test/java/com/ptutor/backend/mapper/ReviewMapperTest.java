package com.ptutor.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ptutor.backend.entity.Review;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;

class ReviewMapperTest {

    private final ReviewMapper reviewMapper = Mappers.getMapper(ReviewMapper.class);

    @Test
    void mapsTutorDetailsAndAnonymizesStudentName() {
        Student student = Student.builder()
                .user(User.builder().firstName("Mai").lastName("Phương").build())
                .build();
        Tutor tutor = Tutor.builder()
                .user(User.builder().firstName("Ngọc").lastName("Anh")
                        .avatarUrl("https://example.com/tutor.jpg").build())
                .build();
        Review review = Review.builder().student(student).tutor(tutor).rating(5).comment("Rất hài lòng").build();

        var response = reviewMapper.toPublicResponse(review);

        assertThat(response.displayName()).isEqualTo("Mai P.");
        assertThat(response.tutorName()).isEqualTo("Ngọc Anh");
        assertThat(response.tutorAvatarUrl()).isEqualTo("https://example.com/tutor.jpg");
        assertThat(response.rating()).isEqualTo(5);
    }
}
