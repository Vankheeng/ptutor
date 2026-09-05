package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.StudyingRequestRequest;
import com.ptutor.backend.dto.request.StudyingRequestUpdateRequest;
import com.ptutor.backend.dto.response.StudyingRequestResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Province;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudyingRequestMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestAvailabilityRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

@ExtendWith(MockitoExtension.class)
class StudyingRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock StudyingRequestRepository studyingRequestRepository;
    @Mock StudyingRequestAvailabilityRepository availabilityRepository;
    @Mock TutorStudentRequestRepository tutorStudentRequestRepository;
    @Mock StudentRepository studentRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock GradeRepository gradeRepository;
    @Mock DistrictRepository districtRepository;

    private StudyingRequestService service;
    private UUID userId;
    private UUID studentId;
    private UUID subjectId;
    private UUID gradeId;
    private UUID districtId;
    private Student student;
    private Subject subject;
    private Grade grade;
    private District district;

    @BeforeEach
    void setUp() {
        service = new StudyingRequestService(
                studyingRequestRepository,
                availabilityRepository,
                tutorStudentRequestRepository,
                Mappers.getMapper(StudyingRequestMapper.class),
                studentRepository,
                subjectRepository,
                gradeRepository,
                districtRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        userId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        districtId = UUID.randomUUID();

        User user = User.builder().email("student@example.com").build();
        user.setId(userId);
        student = Student.builder().user(user).build();
        student.setId(studentId);
        subject = Subject.builder().name("English").status(CatalogStatus.ACTIVE).build();
        subject.setId(subjectId);
        grade = Grade.builder().name("Grade 10").level(10).status(CatalogStatus.ACTIVE).build();
        grade.setId(gradeId);
        Province province = Province.builder().name("Ha Noi").build();
        province.setId(UUID.randomUUID());
        district = District.builder().name("Cau Giay").province(province).build();
        district.setId(districtId);

        lenient().when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        lenient().when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        lenient().when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade));
        lenient().when(districtRepository.findById(districtId)).thenReturn(Optional.of(district));
    }

    @Test
    void createBuildsDraftRequestWithAvailabilities() {
        when(studyingRequestRepository.saveAndFlush(any(StudyingRequest.class)))
                .thenAnswer(invocation -> {
                    StudyingRequest request = invocation.getArgument(0);
                    request.setId(UUID.randomUUID());
                    request.setCreatedAt(NOW_LOCAL);
                    request.setUpdatedAt(NOW_LOCAL);
                    return request;
                });

        StudyingRequestResponse response = service.create(userId, request());

        assertThat(response.status()).isEqualTo(RequestStatus.DRAFT);
        assertThat(response.subjectId()).isEqualTo(subjectId);
        assertThat(response.gradeId()).isEqualTo(gradeId);
        assertThat(response.districtId()).isEqualTo(districtId);
        assertThat(response.availabilities()).hasSize(1);
        verify(availabilityRepository).flush();
    }

    @Test
    void createRejectsUserWithoutStudentProfile() {
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("STUDENT_PROFILE_REQUIRED");
                });
        verify(subjectRepository, never()).findById(any());
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsInactiveSubject() {
        subject.setStatus(CatalogStatus.INACTIVE);

        assertThatThrownBy(() -> service.create(userId, request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_SUBJECT");
                });
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsInactiveGrade() {
        grade.setStatus(CatalogStatus.INACTIVE);

        assertThatThrownBy(() -> service.create(userId, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_GRADE"));
    }

    @Test
    void createRejectsUnknownDistrict() {
        when(districtRepository.findById(districtId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_DISTRICT"));
    }

    @Test
    void createRejectsInvalidPriceRange() {
        StudyingRequestRequest invalid = new StudyingRequestRequest(
                subjectId, gradeId, "Find a tutor", null, null, districtId, null,
                java.math.BigDecimal.valueOf(200), java.math.BigDecimal.valueOf(100),
                null, LearningMode.ONLINE, null, null);

        assertThatThrownBy(() -> service.create(userId, invalid))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PRICE_RANGE"));
    }

    @Test
    void createRejectsInvalidAvailability() {
        StudyingRequestRequest invalid = new StudyingRequestRequest(
                subjectId, gradeId, "Find a tutor", null, null, null, null,
                null, null, null, LearningMode.ONLINE, null,
                List.of(new StudyingRequestRequest.Availability(8, LocalTime.of(19, 0), LocalTime.of(18, 0))));

        assertThatThrownBy(() -> service.create(userId, invalid))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_AVAILABILITY"));
    }

    @Test
    void findMineByIdReturnsDetailedOwnedRequest() {
        StudyingRequest request = existingRequest(RequestStatus.OPEN);
        when(studyingRequestRepository.findDetailedByIdAndStudentId(request.getId(), studentId))
                .thenReturn(Optional.of(request));

        StudyingRequestResponse response = service.findMineById(userId, request.getId());

        assertThat(response.id()).isEqualTo(request.getId());
        assertThat(response.availabilities()).hasSize(1);
        verify(studyingRequestRepository).findDetailedByIdAndStudentId(request.getId(), studentId);
    }

    @Test
    void findMineByIdDoesNotReturnAnotherStudentsRequest() {
        UUID requestId = UUID.randomUUID();
        when(studyingRequestRepository.findDetailedByIdAndStudentId(requestId, studentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findMineById(userId, requestId))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_FOUND"));
    }

    @Test
    void findMineReturnsPagedRequestsForCurrentStudentOnly() {
        StudyingRequest request = existingRequest(RequestStatus.OPEN);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(studyingRequestRepository.findAllByStudent_IdOrderByCreatedAtDesc(studentId, pageable))
                .thenReturn(new PageImpl<>(List.of(request), pageable, 1));
        when(availabilityRepository
                .findAllByStudyingRequest_IdInOrderByStudyingRequest_IdAscDayOfWeekAscStartTimeAsc(
                        List.of(request.getId())))
                .thenReturn(request.getAvailabilities());

        var response = service.findMine(userId, null, pageable);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo(request.getId());
        verify(studyingRequestRepository).findAllByStudent_IdOrderByCreatedAtDesc(studentId, pageable);
    }

    @Test
    void findMineRejectsUnsupportedStatusFilter() {
        assertThatThrownBy(() -> service.findMine(
                userId,
                RequestStatus.PENDING_REVIEW,
                PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_STUDYING_REQUEST_STATUS");
                });
        verify(studyingRequestRepository, never())
                .findAllByStudent_IdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void updateChangesOnlyProvidedFields() {
        StudyingRequest request = existingRequest(RequestStatus.OPEN);
        request.setDescription("Keep this description");
        request.setMaxPrice(java.math.BigDecimal.valueOf(200));
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        StudyingRequestUpdateRequest update = new StudyingRequestUpdateRequest(
                null, null, "Updated title", null, null, null, null,
                null, null, null, null, null, null);

        StudyingRequestResponse response = service.update(userId, request.getId(), update);

        assertThat(request.getTitle()).isEqualTo("Updated title");
        assertThat(request.getDescription()).isEqualTo("Keep this description");
        assertThat(request.getMaxPrice()).isEqualByComparingTo("200");
        assertThat(response.title()).isEqualTo("Updated title");
        verify(studyingRequestRepository).saveAndFlush(request);
    }

    @Test
    void updateRejectsEmptyPatch() {
        StudyingRequestUpdateRequest update = new StudyingRequestUpdateRequest(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(userId, UUID.randomUUID(), update))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("EMPTY_UPDATE_REQUEST");
                });
        verify(studentRepository, never()).findByUser_Id(any());
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"MATCHED", "CLOSED", "CANCELLED"})
    void updateRejectsNonEditableStatuses(RequestStatus status) {
        StudyingRequest request = existingRequest(status);
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));

        StudyingRequestUpdateRequest update = new StudyingRequestUpdateRequest(
                null, null, "Updated title", null, null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(userId, request.getId(), update))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_STUDYING_REQUEST_STATUS_TRANSITION");
                });
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStatusChangesOpenToClosed() {
        StudyingRequest request = existingRequest(RequestStatus.OPEN);
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        StudyingRequestResponse response = service.updateStatus(userId, request.getId(), RequestStatus.CLOSED);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(response.status()).isEqualTo(RequestStatus.CLOSED);
    }

    @Test
    void updateStatusRejectsDraftActivationThroughPublicApi() {
        StudyingRequest request = existingRequest(RequestStatus.DRAFT);
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.updateStatus(userId, request.getId(), RequestStatus.OPEN))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_STUDYING_REQUEST_STATUS_TRANSITION");
                });
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"DRAFT", "OPEN", "MATCHED", "CLOSED"})
    void cancelAllowsEveryApprovedCancellableStatus(RequestStatus status) {
        StudyingRequest request = existingRequest(status);
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        StudyingRequestResponse response = service.cancel(userId, request.getId());

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(response.status()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void cancelUpdatesPendingAndAcceptedTutorOffersButKeepsRejectedHistory() {
        StudyingRequest request = existingRequest(RequestStatus.MATCHED);
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        service.cancel(userId, request.getId());

        verify(tutorStudentRequestRepository).cancelActiveByStudyingRequestId(
                request.getId(),
                List.of(ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED),
                ApplicationStatus.CANCELLED,
                NOW_LOCAL);
    }

    @Test
    void cancelRejectsAlreadyCancelledRequest() {
        StudyingRequest request = existingRequest(RequestStatus.CANCELLED);
        when(studyingRequestRepository.findByIdAndStudent_Id(request.getId(), studentId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(userId, request.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_STUDYING_REQUEST_STATUS_TRANSITION");
                });
        verify(tutorStudentRequestRepository, never()).cancelActiveByStudyingRequestId(
                any(), any(), any(), any());
    }

    @Test
    void activateAfterPaymentChangesDraftToOpen() {
        StudyingRequest request = existingRequest(RequestStatus.DRAFT);
        when(studyingRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        assertThat(service.activateAfterPayment(request.getId()).status()).isEqualTo(RequestStatus.OPEN);
    }

    private StudyingRequestRequest request() {
        return new StudyingRequestRequest(
                subjectId, gradeId, "  Find an English tutor  ", "Description", "Note", districtId,
                "Address", java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(200),
                "Improve speaking", LearningMode.ONLINE, "Evenings",
                List.of(new StudyingRequestRequest.Availability(2, LocalTime.of(18, 0), LocalTime.of(20, 0))));
    }

    private StudyingRequest existingRequest(RequestStatus status) {
        StudyingRequest request = StudyingRequest.builder()
                .student(student)
                .subject(subject)
                .grade(grade)
                .district(district)
                .title("Find an English tutor")
                .learningMode(LearningMode.ONLINE)
                .status(status)
                .build();
        request.setId(UUID.randomUUID());
        request.setCreatedAt(NOW_LOCAL);
        request.setUpdatedAt(NOW_LOCAL);
        request.getAvailabilities().add(com.ptutor.backend.entity.StudyingRequestAvailability.builder()
                .studyingRequest(request)
                .dayOfWeek(2)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .build());
        return request;
    }
}
