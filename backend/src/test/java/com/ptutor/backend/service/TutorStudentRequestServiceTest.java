package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorStudentRequestMapper;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

@ExtendWith(MockitoExtension.class)
class TutorStudentRequestServiceTest {

    @Mock TutorStudentRequestRepository tutorStudentRequestRepository;
    @Mock StudyingRequestRepository studyingRequestRepository;
    @Mock StudentRepository studentRepository;
    @Mock TutorStudentRequestMapper tutorStudentRequestMapper;

    private TutorStudentRequestService service;
    private UUID userId;
    private UUID studentId;
    private UUID studyingRequestId;
    private UUID tutorRequestId;
    private Student student;
    private StudyingRequest studyingRequest;
    private TutorStudentRequest tutorRequest;

    @BeforeEach
    void setUp() {
        service = new TutorStudentRequestService(
                tutorStudentRequestRepository,
                studyingRequestRepository,
                studentRepository,
                tutorStudentRequestMapper);

        userId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        studyingRequestId = UUID.randomUUID();
        tutorRequestId = UUID.randomUUID();

        User studentUser = User.builder().email("student@example.com").build();
        studentUser.setId(userId);
        student = Student.builder().user(studentUser).build();
        student.setId(studentId);

        studyingRequest = StudyingRequest.builder()
                .student(student)
                .quantity(2)
                .status(RequestStatus.OPEN)
                .build();
        studyingRequest.setId(studyingRequestId);

        User tutorUser = User.builder()
                .email("tutor@example.com")
                .firstName("Tutor")
                .lastName("One")
                .build();
        Tutor tutor = Tutor.builder().user(tutorUser).build();
        tutor.setId(UUID.randomUUID());
        Grade grade = Grade.builder().name("Grade 10").build();
        grade.setId(UUID.randomUUID());
        tutorRequest = TutorStudentRequest.builder()
                .tutor(tutor)
                .grade(grade)
                .studyingRequest(studyingRequest)
                .teachingMode(TeachingMode.ONLINE)
                .status(ApplicationStatus.PENDING)
                .build();
        tutorRequest.setId(tutorRequestId);

        lenient().when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        lenient().when(tutorStudentRequestMapper.toResponse(any(TutorStudentRequest.class)))
                .thenReturn((TutorStudentRequestResponse) null);
    }

    @Test
    void listsOnlyTutorRequestsFromOwnedStudyingRequest() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
        when(tutorStudentRequestRepository.findAllByStudyingRequest_IdAndStatusOrderByCreatedAtDesc(
                studyingRequestId, ApplicationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(tutorRequest), pageable, 1));

        var response = service.findMine(userId, studyingRequestId, ApplicationStatus.PENDING, pageable);

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

        assertThatThrownBy(() -> service.findMineById(userId, studyingRequestId, tutorRequestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_FOUND");
                });
        verify(tutorStudentRequestRepository, never())
                .findByIdAndStudyingRequest_Id(any(), any());
    }

    @Test
    void acceptsPendingRequestAndKeepsStudyingRequestOpenWhenCapacityRemains() {
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(tutorStudentRequestRepository.saveAndFlush(tutorRequest)).thenReturn(tutorRequest);

        service.accept(userId, studyingRequestId, tutorRequestId);

        assertThat(tutorRequest.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(studyingRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsPendingRequestAndMarksStudyingRequestMatchedAtCapacity() {
        studyingRequest.setQuantity(1);
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(tutorStudentRequestRepository.saveAndFlush(tutorRequest)).thenReturn(tutorRequest);
        when(studyingRequestRepository.saveAndFlush(studyingRequest)).thenReturn(studyingRequest);

        service.accept(userId, studyingRequestId, tutorRequestId);

        assertThat(tutorRequest.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(studyingRequest.getStatus()).isEqualTo(RequestStatus.MATCHED);
        verify(studyingRequestRepository).saveAndFlush(studyingRequest);
    }

    @Test
    void doesNotAcceptWhenQuantityHasAlreadyBeenReached() {
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(2L);

        assertThatThrownBy(() -> service.accept(userId, studyingRequestId, tutorRequestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_QUANTITY_REACHED");
                });
        verify(tutorStudentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPendingRequestWithoutChangingStudyingRequest() {
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(tutorStudentRequestRepository.saveAndFlush(tutorRequest)).thenReturn(tutorRequest);

        service.reject(userId, studyingRequestId, tutorRequestId);

        assertThat(tutorRequest.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(studyingRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        verify(studyingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotProcessAlreadyAcceptedRequest() {
        tutorRequest.setStatus(ApplicationStatus.ACCEPTED);
        when(studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));

        assertThatThrownBy(() -> service.reject(userId, studyingRequestId, tutorRequestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION");
                });
    }

    @Test
    void cannotAcceptWhenStudyingRequestIsNotOpen() {
        studyingRequest.setStatus(RequestStatus.CLOSED);
        when(studyingRequestRepository.findByIdAndStudentIdForUpdate(studyingRequestId, studentId))
                .thenReturn(Optional.of(studyingRequest));

        assertThatThrownBy(() -> service.accept(userId, studyingRequestId, tutorRequestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION");
                });
        verify(tutorStudentRequestRepository, never())
                .findByIdAndStudyingRequest_Id(any(), any());
    }
}
