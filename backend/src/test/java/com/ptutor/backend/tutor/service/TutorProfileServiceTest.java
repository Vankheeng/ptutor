package com.ptutor.backend.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.tutor.dto.TutorProfileResponse;
import com.ptutor.backend.tutor.dto.TutorSelfProfileResponse;

@ExtendWith(MockitoExtension.class)
class TutorProfileServiceTest {

    @Mock TutorRepository tutorRepository;

    private TutorProfileService tutorProfileService;
    private UUID tutorId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tutorProfileService = new TutorProfileService(tutorRepository);
        tutorId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void findByIdReturnsPublicTutorProfile() {
        Tutor tutor = tutor();
        when(tutorRepository.findById(tutorId)).thenReturn(Optional.of(tutor));

        TutorProfileResponse response = tutorProfileService.findById(tutorId);

        assertThat(response.tutorId()).isEqualTo(tutorId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.firstName()).isEqualTo("Nguyen");
        assertThat(response.lastName()).isEqualTo("An");
        assertThat(response.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(response.introduction()).isEqualTo("Experienced English tutor");
        assertThat(response.experienceYears()).isEqualTo(5);
        assertThat(response.averageRating()).isEqualByComparingTo("4.80");
    }

    @Test
    void findByIdThrowsNotFoundWhenTutorDoesNotExist() {
        when(tutorRepository.findById(tutorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tutorProfileService.findById(tutorId))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("TUTOR_NOT_FOUND");
                });
    }

    @Test
    void findMineReturnsPrivateFieldsForAuthenticatedTutor() {
        Tutor tutor = tutor();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));

        TutorSelfProfileResponse selfResponse = tutorProfileService.findMine(userId);

        assertThat(selfResponse.tutorId()).isEqualTo(tutorId);
        assertThat(selfResponse.userId()).isEqualTo(userId);
        assertThat(selfResponse.email()).isEqualTo("tutor@example.com");
        assertThat(selfResponse.phone()).isEqualTo("0900000000");
        assertThat(selfResponse.address().detailAddress()).isEqualTo("123 Tutor Street");
    }

    @Test
    void findMineThrowsNotFoundWhenTutorProfileDoesNotExist() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tutorProfileService.findMine(userId))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException exception = (ApiException) error;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("TUTOR_PROFILE_NOT_FOUND");
                });
    }

    private Tutor tutor() {
        User user = User.builder()
                .email("tutor@example.com")
                .firstName("Nguyen")
                .lastName("An")
                .phone("0900000000")
                .detailAddress("123 Tutor Street")
                .avatarUrl("https://cdn.example.com/avatar.png")
                .build();
        user.setId(userId);

        Tutor tutor = Tutor.builder()
                .user(user)
                .introduction("Experienced English tutor")
                .experienceYears(5)
                .education("Bachelor of English")
                .teachingStyleTags("Patient, practical")
                .teachingMethodology("Communicative learning")
                .strengthSubjects("English, IELTS")
                .targetStudentType("High school students")
                .averageRating(new BigDecimal("4.80"))
                .totalReviews(25)
                .completedContractsCount(30)
                .totalStudentsTaught(20)
                .acceptanceRate(new BigDecimal("95.00"))
                .avgResponseTimeHours(new BigDecimal("2.50"))
                .build();
        tutor.setId(tutorId);
        return tutor;
    }
}
