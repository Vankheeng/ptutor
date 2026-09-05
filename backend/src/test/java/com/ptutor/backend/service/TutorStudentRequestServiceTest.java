package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.TutorStudentRequestCreateRequest;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorStudentRequestMapper;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

@ExtendWith(MockitoExtension.class)
class TutorStudentRequestServiceTest {

    @Mock TutorStudentRequestRepository tutorStudentRequestRepository;
    @Mock StudyingRequestRepository studyingRequestRepository;
    @Mock TutorRepository tutorRepository;
    @Mock GradeRepository gradeRepository;
    @Mock StudentRepository studentRepository;

    private TutorStudentRequestService service;
    private UUID tutorUserId;
    private UUID tutorId;
    private UUID studentUserId;
    private UUID studentId;
    private UUID studyingRequestId;
    private UUID requestId;
    private UUID gradeId;

    @BeforeEach
    void setUp() {
        service = new TutorStudentRequestService(
                tutorStudentRequestRepository,
                studyingRequestRepository,
                tutorRepository,
                gradeRepository,
                studentRepository,
                Mappers.getMapper(TutorStudentRequestMapper.class));

        tutorUserId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        studentUserId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        studyingRequestId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        gradeId = UUID.randomUUID();

        lenient().when(tutorRepository.findByUser_Id(tutorUserId)).thenReturn(Optional.of(tutor()));
        lenient().when(studentRepository.findByUser_Id(studentUserId)).thenReturn(Optional.of(student()));
    }

    @Test
    void createsPendingProposalForOpenStudyingRequest() {
        when(studyingRequestRepository.findById(studyingRequestId))
                .thenReturn(Optional.of(studyingRequest(RequestStatus.OPEN)));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        when(tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatus(
                tutorId, studyingRequestId, ApplicationStatus.PENDING)).thenReturn(false);
        when(tutorStudentRequestRepository.saveAndFlush(any(TutorStudentRequest.class)))
                .thenAnswer(invocation -> {
                    TutorStudentRequest value = invocation.getArgument(0);
                    value.setId(requestId);
                    return value;
                });

        var response = service.create(tutorUserId, studyingRequestId, createRequest());

        assertThat(response.id()).isEqualTo(requestId);
        assertThat(response.tutorId()).isEqualTo(tutorId);
        assertThat(response.studyingRequestId()).isEqualTo(studyingRequestId);
        assertThat(response.gradeId()).isEqualTo(gradeId);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.preferredSchedule()).isEqualTo("Weekday evenings");
    }

    @Test
    void rejectsProposalForNonOpenStudyingRequest() {
        when(studyingRequestRepository.findById(studyingRequestId))
                .thenReturn(Optional.of(studyingRequest(RequestStatus.CLOSED)));

        assertThatThrownBy(() -> service.create(tutorUserId, studyingRequestId, createRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_OPEN");
                });
        verify(gradeRepository, never()).findById(any());
    }

    @Test
    void rejectsDuplicatePendingProposal() {
        when(studyingRequestRepository.findById(studyingRequestId))
                .thenReturn(Optional.of(studyingRequest(RequestStatus.OPEN)));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        when(tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatus(
                tutorId, studyingRequestId, ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.create(tutorUserId, studyingRequestId, createRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("DUPLICATE_TUTOR_STUDENT_REQUEST");
                });
        verify(tutorStudentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsOnlyCurrentTutorsProposalsWithStatusFilter() {
        TutorStudentRequest request = tutorStudentRequest(ApplicationStatus.PENDING);
        PageRequest pageable = PageRequest.of(0, 20);
        when(tutorStudentRequestRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, ApplicationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(request), pageable, 1));

        var response = service.findMine(tutorUserId, ApplicationStatus.PENDING, pageable);

        assertThat(response.content()).singleElement()
                .extracting(value -> value.id())
                .isEqualTo(requestId);
        verify(tutorStudentRequestRepository).findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, ApplicationStatus.PENDING, pageable);
    }

    @Test
    void cancelsOnlyPendingProposal() {
        TutorStudentRequest request = tutorStudentRequest(ApplicationStatus.PENDING);
        when(tutorStudentRequestRepository.findByIdAndTutor_Id(requestId, tutorId)).thenReturn(Optional.of(request));
        when(tutorStudentRequestRepository.saveAndFlush(request)).thenReturn(request);

        var response = service.cancel(tutorUserId, requestId);

        assertThat(request.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELLED);
        verify(tutorStudentRequestRepository).saveAndFlush(request);
    }

    @Test
    void cannotCancelProcessedProposal() {
        TutorStudentRequest request = tutorStudentRequest(ApplicationStatus.ACCEPTED);
        when(tutorStudentRequestRepository.findByIdAndTutor_Id(requestId, tutorId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(tutorUserId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION");
                });
        verify(tutorStudentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsOnlyTutorRequestsFromOwnedStudyingRequest() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest(RequestStatus.OPEN)));
        when(tutorStudentRequestRepository.findAllByStudyingRequest_IdAndStatusOrderByCreatedAtDesc(
                studyingRequestId, ApplicationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(tutorStudentRequest(ApplicationStatus.PENDING)), pageable, 1));

        var response = service.findMine(studentUserId, studyingRequestId, ApplicationStatus.PENDING, pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isZero();
        verify(tutorStudentRequestRepository)
                .findAllByStudyingRequest_IdAndStatusOrderByCreatedAtDesc(
                        studyingRequestId, ApplicationStatus.PENDING, pageable);
    }

    @Test
    void rejectsAccessToStudyingRequestOwnedByAnotherStudent() {
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findMineById(studentUserId, studyingRequestId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_FOUND");
                });
        verify(tutorStudentRequestRepository, never()).findByIdAndStudyingRequest_Id(any(), any());
    }

    @Test
    void acceptsPendingRequestAndKeepsStudyingRequestOpenWhenCapacityRemains() {
        StudyingRequest request = studyingRequest(RequestStatus.OPEN);
        request.setQuantity(2);
        TutorStudentRequest tutorRequest = tutorStudentRequest(request, ApplicationStatus.PENDING);
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(request));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(requestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(tutorStudentRequestRepository.saveAndFlush(tutorRequest)).thenReturn(tutorRequest);

        service.accept(studentUserId, studyingRequestId, requestId);

        assertThat(tutorRequest.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsPendingRequestAndMarksStudyingRequestMatchedAtCapacity() {
        StudyingRequest request = studyingRequest(RequestStatus.OPEN);
        request.setQuantity(1);
        TutorStudentRequest tutorRequest = tutorStudentRequest(request, ApplicationStatus.PENDING);
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(request));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(requestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(tutorStudentRequestRepository.saveAndFlush(tutorRequest)).thenReturn(tutorRequest);
        when(studyingRequestRepository.saveAndFlush(request)).thenReturn(request);

        service.accept(studentUserId, studyingRequestId, requestId);

        assertThat(tutorRequest.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHED);
        verify(studyingRequestRepository).saveAndFlush(request);
    }

    @Test
    void doesNotAcceptWhenQuantityHasAlreadyBeenReached() {
        StudyingRequest request = studyingRequest(RequestStatus.OPEN);
        TutorStudentRequest tutorRequest = tutorStudentRequest(request, ApplicationStatus.PENDING);
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(request));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(requestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(2L);

        assertThatThrownBy(() -> service.accept(studentUserId, studyingRequestId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_QUANTITY_REACHED");
                });
        verify(tutorStudentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPendingRequestWithoutChangingStudyingRequest() {
        StudyingRequest request = studyingRequest(RequestStatus.OPEN);
        TutorStudentRequest tutorRequest = tutorStudentRequest(request, ApplicationStatus.PENDING);
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.of(request));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(requestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.saveAndFlush(tutorRequest)).thenReturn(tutorRequest);

        service.reject(studentUserId, studyingRequestId, requestId);

        assertThat(tutorRequest.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotProcessAlreadyAcceptedRequest() {
        StudyingRequest request = studyingRequest(RequestStatus.OPEN);
        TutorStudentRequest tutorRequest = tutorStudentRequest(request, ApplicationStatus.ACCEPTED);
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.of(request));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(requestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));

        assertThatThrownBy(() -> service.reject(studentUserId, studyingRequestId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION");
                });
    }

    @Test
    void cannotAcceptWhenStudyingRequestIsNotOpen() {
        StudyingRequest request = studyingRequest(RequestStatus.CLOSED);
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.accept(studentUserId, studyingRequestId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION");
                });
        verify(tutorStudentRequestRepository, never()).findByIdAndStudyingRequest_Id(any(), any());
    }

    private TutorStudentRequestCreateRequest createRequest() {
        return new TutorStudentRequestCreateRequest(
                gradeId, new BigDecimal("150000"), TeachingMode.ONLINE,
                " Weekday evenings ", " I can help with exam preparation. ");
    }

    private Tutor tutor() {
        User user = User.builder()
                .email("tutor@example.com")
                .firstName("Tutor")
                .lastName("One")
                .build();
        Tutor tutor = Tutor.builder().user(user).build();
        tutor.setId(tutorId);
        return tutor;
    }

    private Student student() {
        User user = User.builder().email("student@example.com").build();
        user.setId(studentUserId);
        Student student = Student.builder().user(user).build();
        student.setId(studentId);
        return student;
    }

    private Grade grade() {
        Grade grade = Grade.builder().name("Grade 10").status(CatalogStatus.ACTIVE).build();
        grade.setId(gradeId);
        return grade;
    }

    private StudyingRequest studyingRequest(RequestStatus status) {
        Subject subject = Subject.builder().name("English").status(CatalogStatus.ACTIVE).build();
        subject.setId(UUID.randomUUID());
        StudyingRequest request = StudyingRequest.builder()
                .student(student())
                .subject(subject)
                .grade(grade())
                .quantity(2)
                .title("Need an English tutor")
                .learningMode(com.ptutor.backend.entity.enums.LearningMode.ONLINE)
                .status(status)
                .build();
        request.setId(studyingRequestId);
        return request;
    }

    private TutorStudentRequest tutorStudentRequest(ApplicationStatus status) {
        return tutorStudentRequest(studyingRequest(RequestStatus.OPEN), status);
    }

    private TutorStudentRequest tutorStudentRequest(StudyingRequest studyingRequest, ApplicationStatus status) {
        TutorStudentRequest request = TutorStudentRequest.builder()
                .tutor(tutor())
                .studyingRequest(studyingRequest)
                .grade(grade())
                .proposedPrice(new BigDecimal("150000"))
                .teachingMode(TeachingMode.ONLINE)
                .status(status)
                .build();
        request.setId(requestId);
        return request;
    }
}
