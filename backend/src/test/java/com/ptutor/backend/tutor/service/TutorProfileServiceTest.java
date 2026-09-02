package com.ptutor.backend.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.Gender;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.tutor.dto.TutorProfileResponse;
import com.ptutor.backend.tutor.dto.TutorSelfProfileResponse;
import com.ptutor.backend.tutor.dto.UpdateTutorProfileRequest;

@ExtendWith(MockitoExtension.class)
class TutorProfileServiceTest {

    @Mock TutorRepository tutorRepository;
    @Mock ProvinceRepository provinceRepository;
    @Mock DistrictRepository districtRepository;

    private TutorProfileService tutorProfileService;
    private UUID tutorId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tutorProfileService = new TutorProfileService(tutorRepository, provinceRepository, districtRepository);
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

    @Test
    void updateMineChangesPersonalAndProfessionalFields() {
        Tutor tutor = tutor();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));

        TutorSelfProfileResponse response = tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                "Tran", null, "0911111111", Gender.FEMALE, LocalDate.of(1995, 5, 20), null,
                "456 New Street", null, null, "Updated introduction", 8, "Master of English",
                "Patient", "Communicative learning", "English, IELTS", "University students"));

        assertThat(response.firstName()).isEqualTo("Tran");
        assertThat(response.lastName()).isEqualTo("An");
        assertThat(response.phone()).isEqualTo("0911111111");
        assertThat(response.gender()).isEqualTo(Gender.FEMALE);
        assertThat(response.introduction()).isEqualTo("Updated introduction");
        assertThat(response.experienceYears()).isEqualTo(8);
        assertThat(response.education()).isEqualTo("Master of English");
        assertThat(response.address().detailAddress()).isEqualTo("456 New Street");
        assertThat(response.averageRating()).isEqualByComparingTo("4.80");
        verify(tutorRepository).findByUser_Id(userId);
    }

    @Test
    void updateMineChangesAddressWhenProvinceAndDistrictMatch() {
        Tutor tutor = tutor();
        UUID provinceId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        Province province = Province.builder().name("Hà Nội").build();
        province.setId(provinceId);
        District district = District.builder().name("Ba Đình").province(province).build();
        district.setId(districtId);
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.of(district));

        tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                null, null, null, null, null, null, null, provinceId, districtId,
                null, null, null, null, null, null, null));

        assertThat(tutor.getUser().getDistrict()).isSameAs(district);
    }

    @Test
    void updateMineRejectsEmptyRequest() {
        assertThatThrownBy(() -> tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("EMPTY_UPDATE_REQUEST");
                });
    }

    @Test
    void updateMineRejectsIncompleteAddress() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));

        assertThatThrownBy(() -> tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                null, null, null, null, null, null, null, UUID.randomUUID(), null,
                null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("ADDRESS_IDS_REQUIRED_TOGETHER");
                });
    }

    @Test
    void updateMineRejectsUnknownProvince() {
        Tutor tutor = tutor();
        UUID provinceId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                null, null, null, null, null, null, null, provinceId, districtId,
                null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_PROVINCE");
                });
    }

    @Test
    void updateMineRejectsUnknownDistrict() {
        Tutor tutor = tutor();
        UUID provinceId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        Province province = Province.builder().name("Hà Nội").build();
        province.setId(provinceId);
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                null, null, null, null, null, null, null, provinceId, districtId,
                null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_DISTRICT");
                });
    }

    @Test
    void updateMineRejectsDistrictFromAnotherProvince() {
        Tutor tutor = tutor();
        UUID provinceId = UUID.randomUUID();
        UUID otherProvinceId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        Province province = Province.builder().name("Hà Nội").build();
        province.setId(provinceId);
        Province otherProvince = Province.builder().name("Hồ Chí Minh").build();
        otherProvince.setId(otherProvinceId);
        District district = District.builder().name("District").province(otherProvince).build();
        district.setId(districtId);
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor));
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.of(district));

        assertThatThrownBy(() -> tutorProfileService.updateMine(userId, new UpdateTutorProfileRequest(
                null, null, null, null, null, null, null, provinceId, districtId,
                null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("DISTRICT_PROVINCE_MISMATCH");
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
