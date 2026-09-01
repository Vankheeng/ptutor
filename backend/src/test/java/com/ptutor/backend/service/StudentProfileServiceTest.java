package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.request.UpdateStudentProfileRequest;
import com.ptutor.backend.dto.response.StudentProfileResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.Gender;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudentProfileMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.ProvinceRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock ProvinceRepository provinceRepository;
    @Mock DistrictRepository districtRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private StudentProfileService studentProfileService;
    private UUID userId;
    private UUID studentId;
    private UUID provinceId;
    private UUID districtId;
    private Province province;
    private District district;
    private User user;
    private Student student;

    @BeforeEach
    void setUp() {
        StudentProfileMapper mapper = Mappers.getMapper(StudentProfileMapper.class);
        studentProfileService = new StudentProfileService(
                studentRepository, provinceRepository, districtRepository, mapper, currentUserProvider);
        userId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        provinceId = UUID.randomUUID();
        districtId = UUID.randomUUID();

        province = Province.builder().name("Ha Noi").build();
        province.setId(provinceId);
        district = District.builder().name("Cau Giay").province(province).build();
        district.setId(districtId);
        user = User.builder()
                .email("student@example.com")
                .firstName("Nguyen")
                .lastName("An")
                .phone("0900000000")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(2005, 1, 1))
                .detailAddress("Old address")
                .district(district)
                .build();
        user.setId(userId);
        student = Student.builder()
                .user(user)
                .introduction("Old introduction")
                .currentLevel("BEGINNER")
                .build();
        student.setId(studentId);
    }

    @Test
    void getCurrentProfileReturnsOwnStudentProfileAndAddress() {
        stubCurrentStudent();

        StudentProfileResponse response = studentProfileService.getCurrentProfile();

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.studentId()).isEqualTo(studentId);
        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.address().districtId()).isEqualTo(districtId);
        assertThat(response.address().provinceId()).isEqualTo(provinceId);
        assertThat(response.introduction()).isEqualTo("Old introduction");
        verify(studentRepository).findProfileByUserId(userId);
    }

    @Test
    void getCurrentProfileRejectsMissingStudentProfile() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(studentRepository.findProfileByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(studentProfileService::getCurrentProfile)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDENT_PROFILE_NOT_FOUND"));
    }

    @Test
    void updateCurrentProfileChangesOnlyProvidedFields() {
        stubCurrentStudent();
        UpdateStudentProfileRequest request = request("Nguyen Van", null, null, "New introduction");

        StudentProfileResponse response = studentProfileService.updateCurrentProfile(request);

        assertThat(user.getFirstName()).isEqualTo("Nguyen Van");
        assertThat(user.getLastName()).isEqualTo("An");
        assertThat(user.getPhone()).isEqualTo("0900000000");
        assertThat(student.getIntroduction()).isEqualTo("New introduction");
        assertThat(student.getCurrentLevel()).isEqualTo("BEGINNER");
        assertThat(response.firstName()).isEqualTo("Nguyen Van");
    }

    @Test
    void updateCurrentProfileRejectsEmptyRequest() {
        assertThatThrownBy(() -> studentProfileService.updateCurrentProfile(request(null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("EMPTY_UPDATE_REQUEST"));
    }

    @Test
    void updateCurrentProfileChangesValidatedAddress() {
        stubCurrentStudent();
        Province newProvince = Province.builder().name("Da Nang").build();
        newProvince.setId(UUID.randomUUID());
        District newDistrict = District.builder().name("Hai Chau").province(newProvince).build();
        newDistrict.setId(UUID.randomUUID());
        when(provinceRepository.findById(newProvince.getId())).thenReturn(Optional.of(newProvince));
        when(districtRepository.findById(newDistrict.getId())).thenReturn(Optional.of(newDistrict));

        studentProfileService.updateCurrentProfile(
                request(null, newProvince.getId(), newDistrict.getId(), null));

        assertThat(user.getDistrict()).isEqualTo(newDistrict);
    }

    @Test
    void updateCurrentProfileRejectsIncompleteAddressIds() {
        stubCurrentStudent();

        assertThatThrownBy(() -> studentProfileService.updateCurrentProfile(
                request(null, provinceId, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ADDRESS_IDS_REQUIRED_TOGETHER"));
    }

    @Test
    void updateCurrentProfileRejectsUnknownProvince() {
        stubCurrentStudent();
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentProfileService.updateCurrentProfile(
                request(null, provinceId, districtId, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PROVINCE"));
    }

    @Test
    void updateCurrentProfileRejectsUnknownDistrict() {
        stubCurrentStudent();
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentProfileService.updateCurrentProfile(
                request(null, provinceId, districtId, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_DISTRICT"));
    }

    @Test
    void updateCurrentProfileRejectsDistrictFromAnotherProvince() {
        stubCurrentStudent();
        Province anotherProvince = Province.builder().build();
        anotherProvince.setId(UUID.randomUUID());
        District mismatchedDistrict = District.builder().province(anotherProvince).build();
        mismatchedDistrict.setId(districtId);
        when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        when(districtRepository.findById(districtId)).thenReturn(Optional.of(mismatchedDistrict));

        assertThatThrownBy(() -> studentProfileService.updateCurrentProfile(
                request(null, provinceId, districtId, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("DISTRICT_PROVINCE_MISMATCH"));
    }

    private void stubCurrentStudent() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(studentRepository.findProfileByUserId(userId)).thenReturn(Optional.of(student));
    }

    private UpdateStudentProfileRequest request(
            String firstName, UUID requestedProvinceId, UUID requestedDistrictId, String introduction) {
        return new UpdateStudentProfileRequest(
                firstName, null, null, null, null, null, null,
                requestedProvinceId, requestedDistrictId, introduction,
                null, null, null, null, null);
    }
}
