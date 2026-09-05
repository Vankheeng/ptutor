package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.StudentTutorRequestCreateRequest;
import com.ptutor.backend.dto.response.StudentTutorRequestMineResponse;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudentTutorRequestMapper;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.GradeTeachingRequestRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;

@ExtendWith(MockitoExtension.class)
class StudentTutorRequestStudentServiceTest {

    @Mock StudentTutorRequestRepository requestRepository;
    @Mock TeachingRequestRepository teachingRequestRepository;
    @Mock StudentRepository studentRepository;
    @Mock GradeRepository gradeRepository;
    @Mock GradeTeachingRequestRepository gradeTeachingRequestRepository;
    @Mock TutorRepository tutorRepository;
    @Mock StudentTutorRequestMapper mapper;

    private StudentTutorRequestService service;
    private UUID userId;
    private UUID studentId;
    private UUID teachingRequestId;
    private UUID gradeId;
    private UUID requestId;
    private Student student;
    private TeachingRequest teachingRequest;
    private Grade grade;

    @BeforeEach
    void setUp() {
        service = new StudentTutorRequestService(
                requestRepository,
                teachingRequestRepository,
                tutorRepository,
                mapper,
                studentRepository,
                gradeRepository,
                gradeTeachingRequestRepository);
        userId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        teachingRequestId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        student = Student.builder().build();
        student.setId(studentId);
        teachingRequest = TeachingRequest.builder().status(RequestStatus.OPEN).build();
        teachingRequest.setId(teachingRequestId);
        grade = Grade.builder().status(CatalogStatus.ACTIVE).build();
        grade.setId(gradeId);
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
    }

    @Test
    void createsPendingRequestForOpenTeachingRequest() {
        StudentTutorRequestCreateRequest source = request();
        StudentTutorRequestMineResponse response = org.mockito.Mockito.mock(StudentTutorRequestMineResponse.class);

        when(teachingRequestRepository.findById(teachingRequestId)).thenReturn(Optional.of(teachingRequest));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade));
        when(gradeTeachingRequestRepository.existsByTeachingRequest_IdAndGrade_Id(teachingRequestId, gradeId))
                .thenReturn(true);
        when(requestRepository.existsByStudent_IdAndTeachingRequest_IdAndStatus(
                studentId, teachingRequestId, ApplicationStatus.PENDING)).thenReturn(false);
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentTutorRequest saved = invocation.getArgument(0);
            saved.setId(requestId);
            return saved;
        });
        when(mapper.toMineResponse(any())).thenReturn(response);

        assertThat(service.createForStudent(userId, teachingRequestId, source)).isSameAs(response);
        var saved = org.mockito.ArgumentCaptor.forClass(StudentTutorRequest.class);
        verify(requestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(saved.getValue().getLearningMode()).isEqualTo(LearningMode.ONLINE);
    }

    @Test
    void rejectsCreationWhenTeachingRequestIsNotOpen() {
        teachingRequest.setStatus(RequestStatus.CLOSED);
        when(teachingRequestRepository.findById(teachingRequestId)).thenReturn(Optional.of(teachingRequest));

        assertThatThrownBy(() -> service.createForStudent(userId, teachingRequestId, request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TEACHING_REQUEST_STATUS");
                });
        verify(gradeRepository, never()).findById(any());
    }

    @Test
    void rejectsDuplicatePendingRequest() {
        when(teachingRequestRepository.findById(teachingRequestId)).thenReturn(Optional.of(teachingRequest));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade));
        when(gradeTeachingRequestRepository.existsByTeachingRequest_IdAndGrade_Id(teachingRequestId, gradeId))
                .thenReturn(true);
        when(requestRepository.existsByStudent_IdAndTeachingRequest_IdAndStatus(
                studentId, teachingRequestId, ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.createForStudent(userId, teachingRequestId, request()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("DUPLICATE_STUDENT_TUTOR_REQUEST"));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsOnlyRequestsBelongingToCurrentStudent() {
        StudentTutorRequest request = StudentTutorRequest.builder().student(student).status(ApplicationStatus.PENDING).build();
        request.setId(requestId);
        StudentTutorRequestMineResponse response = org.mockito.Mockito.mock(StudentTutorRequestMineResponse.class);
        var page = new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1);
        when(requestRepository.findAllByStudent_IdOrderByCreatedAtDesc(studentId, page.getPageable()))
                .thenReturn(page);
        when(mapper.toMineResponse(request)).thenReturn(response);

        var result = service.findMineForStudent(userId, null, page.getPageable());

        assertThat(result.content()).containsExactly(response);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(requestRepository).findAllByStudent_IdOrderByCreatedAtDesc(studentId, page.getPageable());
    }

    @Test
    void cancelsOnlyPendingRequestOwnedByCurrentStudent() {
        StudentTutorRequest request = StudentTutorRequest.builder()
                .student(student).status(ApplicationStatus.PENDING).build();
        request.setId(requestId);
        StudentTutorRequestMineResponse response = org.mockito.Mockito.mock(StudentTutorRequestMineResponse.class);
        when(requestRepository.findByIdAndStudent_Id(requestId, studentId)).thenReturn(Optional.of(request));
        when(requestRepository.saveAndFlush(request)).thenReturn(request);
        when(mapper.toMineResponse(request)).thenReturn(response);

        assertThat(service.cancelForStudent(userId, requestId, ApplicationStatus.CANCELLED)).isSameAs(response);
        assertThat(request.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    @Test
    void refusesToCancelAlreadyProcessedRequest() {
        StudentTutorRequest request = StudentTutorRequest.builder()
                .student(student).status(ApplicationStatus.ACCEPTED).build();
        when(requestRepository.findByIdAndStudent_Id(requestId, studentId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancelForStudent(userId, requestId, ApplicationStatus.CANCELLED))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION");
                });
        verify(requestRepository, never()).saveAndFlush(any());
    }

    private StudentTutorRequestCreateRequest request() {
        return new StudentTutorRequestCreateRequest(
                gradeId, BigDecimal.valueOf(150_000), LearningMode.ONLINE, "Monday evening", "I want to study");
    }
}
