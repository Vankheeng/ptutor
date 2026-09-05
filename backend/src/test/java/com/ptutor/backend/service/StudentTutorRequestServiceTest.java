package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudentTutorRequestMapper;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;

@ExtendWith(MockitoExtension.class)
class StudentTutorRequestServiceTest {

    @Mock StudentTutorRequestRepository studentTutorRequestRepository;
    @Mock TeachingRequestRepository teachingRequestRepository;
    @Mock TutorRepository tutorRepository;

    private StudentTutorRequestService service;
    private UUID userId;
    private UUID tutorId;
    private UUID teachingRequestId;
    private UUID applicationId;

    @BeforeEach
    void setUp() {
        service = new StudentTutorRequestService(
                studentTutorRequestRepository,
                teachingRequestRepository,
                tutorRepository,
                Mappers.getMapper(StudentTutorRequestMapper.class));
        userId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        teachingRequestId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
    }

    @Test
    void acceptsPendingApplicationAndMovesTeachingRequestToMatchedWhenCapacityIsFilled() {
        TeachingRequest teachingRequest = teachingRequest(RequestStatus.OPEN);
        StudentTutorRequest application = application(teachingRequest, ApplicationStatus.PENDING);
        when(teachingRequestRepository.findByIdAndTutor_Id(teachingRequestId, tutorId))
                .thenReturn(Optional.of(teachingRequest));
        when(studentTutorRequestRepository
                .findByIdAndTeachingRequest_Id(applicationId, teachingRequestId))
                .thenReturn(Optional.of(application));
        when(studentTutorRequestRepository.countByTeachingRequest_IdAndStatus(
                teachingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(1L);
        when(teachingRequestRepository.saveAndFlush(teachingRequest)).thenReturn(teachingRequest);
        when(studentTutorRequestRepository.saveAndFlush(application)).thenReturn(application);

        var response = service.updateStatus(
                userId, teachingRequestId, applicationId, ApplicationStatus.ACCEPTED);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(teachingRequest.getStatus()).isEqualTo(RequestStatus.MATCHED);
        assertThat(response.nextStep()).isEqualTo("CONTRACT_SIGNING");
        verify(teachingRequestRepository).saveAndFlush(teachingRequest);
        verify(studentTutorRequestRepository).saveAndFlush(application);
    }

    @Test
    void acceptsPendingApplicationAndKeepsTeachingRequestOpenWhenCapacityRemains() {
        TeachingRequest teachingRequest = teachingRequest(RequestStatus.OPEN);
        teachingRequest.setQuantity(2);
        StudentTutorRequest application = application(teachingRequest, ApplicationStatus.PENDING);
        when(teachingRequestRepository.findByIdAndTutor_Id(teachingRequestId, tutorId))
                .thenReturn(Optional.of(teachingRequest));
        when(studentTutorRequestRepository
                .findByIdAndTeachingRequest_Id(applicationId, teachingRequestId))
                .thenReturn(Optional.of(application));
        when(studentTutorRequestRepository.countByTeachingRequest_IdAndStatus(
                teachingRequestId, ApplicationStatus.ACCEPTED)).thenReturn(1L);
        when(studentTutorRequestRepository.saveAndFlush(application)).thenReturn(application);

        var response = service.updateStatus(
                userId, teachingRequestId, applicationId, ApplicationStatus.ACCEPTED);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(teachingRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.nextStep()).isEqualTo("CONTRACT_SIGNING");
        verify(teachingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPendingApplicationWithoutChangingTeachingRequest() {
        TeachingRequest teachingRequest = teachingRequest(RequestStatus.OPEN);
        StudentTutorRequest application = application(teachingRequest, ApplicationStatus.PENDING);
        when(teachingRequestRepository.findByIdAndTutor_Id(teachingRequestId, tutorId))
                .thenReturn(Optional.of(teachingRequest));
        when(studentTutorRequestRepository
                .findByIdAndTeachingRequest_Id(applicationId, teachingRequestId))
                .thenReturn(Optional.of(application));
        when(studentTutorRequestRepository.saveAndFlush(application)).thenReturn(application);

        service.updateStatus(userId, teachingRequestId, applicationId, ApplicationStatus.REJECTED);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(teachingRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        verify(teachingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotUpdateAnAlreadyProcessedApplication() {
        TeachingRequest teachingRequest = teachingRequest(RequestStatus.OPEN);
        StudentTutorRequest application = application(teachingRequest, ApplicationStatus.ACCEPTED);
        when(teachingRequestRepository.findByIdAndTutor_Id(teachingRequestId, tutorId))
                .thenReturn(Optional.of(teachingRequest));
        when(studentTutorRequestRepository
                .findByIdAndTeachingRequest_Id(applicationId, teachingRequestId))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.updateStatus(
                userId, teachingRequestId, applicationId, ApplicationStatus.REJECTED))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION");
                });
    }

    private Tutor tutor() {
        Tutor tutor = Tutor.builder().build();
        tutor.setId(tutorId);
        return tutor;
    }

    private TeachingRequest teachingRequest(RequestStatus status) {
        TeachingRequest request = TeachingRequest.builder()
                .tutor(tutor())
                .title("Find students")
                .quantity(1)
                .teachingMode(TeachingMode.ONLINE)
                .status(status)
                .build();
        request.setId(teachingRequestId);
        return request;
    }

    private StudentTutorRequest application(TeachingRequest teachingRequest, ApplicationStatus status) {
        User user = User.builder().email("student@example.com").build();
        Student student = Student.builder().user(user).build();
        student.setId(UUID.randomUUID());
        Grade grade = Grade.builder().name("Grade 9").build();
        grade.setId(UUID.randomUUID());
        StudentTutorRequest application = StudentTutorRequest.builder()
                .student(student)
                .grade(grade)
                .teachingRequest(teachingRequest)
                .status(status)
                .build();
        application.setId(applicationId);
        return application;
    }
}
