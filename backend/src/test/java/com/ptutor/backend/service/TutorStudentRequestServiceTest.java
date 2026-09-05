package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorStudentRequestMapper;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

@ExtendWith(MockitoExtension.class)
class TutorStudentRequestServiceTest {

    @Mock TutorStudentRequestRepository tutorStudentRequestRepository;
    @Mock StudyingRequestRepository studyingRequestRepository;
    @Mock TutorRepository tutorRepository;
    @Mock GradeRepository gradeRepository;

    private TutorStudentRequestService service;
    private UUID userId;
    private UUID tutorId;
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
                Mappers.getMapper(TutorStudentRequestMapper.class));
        userId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        studyingRequestId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
    }

    @Test
    void createsPendingProposalForOpenStudyingRequest() {
        when(studyingRequestRepository.findById(studyingRequestId)).thenReturn(Optional.of(studyingRequest(RequestStatus.OPEN)));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        when(tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatus(
                tutorId, studyingRequestId, ApplicationStatus.PENDING)).thenReturn(false);
        when(tutorStudentRequestRepository.saveAndFlush(any(TutorStudentRequest.class)))
                .thenAnswer(invocation -> {
                    TutorStudentRequest value = invocation.getArgument(0);
                    value.setId(requestId);
                    return value;
                });

        var response = service.create(userId, studyingRequestId, createRequest());

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

        assertThatThrownBy(() -> service.create(userId, studyingRequestId, createRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_OPEN");
                });
        verify(gradeRepository, never()).findById(any());
    }

    @Test
    void rejectsDuplicatePendingProposal() {
        when(studyingRequestRepository.findById(studyingRequestId)).thenReturn(Optional.of(studyingRequest(RequestStatus.OPEN)));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        when(tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatus(
                tutorId, studyingRequestId, ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, studyingRequestId, createRequest()))
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
                .thenReturn(new PageImpl<>(java.util.List.of(request), pageable, 1));

        var response = service.findMine(userId, ApplicationStatus.PENDING, pageable);

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

        var response = service.cancel(userId, requestId);

        assertThat(request.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELLED);
        verify(tutorStudentRequestRepository).saveAndFlush(request);
    }

    @Test
    void cannotCancelProcessedProposal() {
        TutorStudentRequest request = tutorStudentRequest(ApplicationStatus.ACCEPTED);
        when(tutorStudentRequestRepository.findByIdAndTutor_Id(requestId, tutorId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(userId, requestId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION");
                });
        verify(tutorStudentRequestRepository, never()).saveAndFlush(any());
    }

    private TutorStudentRequestCreateRequest createRequest() {
        return new TutorStudentRequestCreateRequest(
                gradeId, new BigDecimal("150000"), TeachingMode.ONLINE,
                " Weekday evenings ", " I can help with exam preparation. ");
    }

    private Tutor tutor() {
        Tutor tutor = Tutor.builder().build();
        tutor.setId(tutorId);
        return tutor;
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
                .subject(subject)
                .grade(grade())
                .title("Need an English tutor")
                .learningMode(com.ptutor.backend.entity.enums.LearningMode.ONLINE)
                .status(status)
                .build();
        request.setId(studyingRequestId);
        return request;
    }

    private TutorStudentRequest tutorStudentRequest(ApplicationStatus status) {
        TutorStudentRequest request = TutorStudentRequest.builder()
                .tutor(tutor())
                .studyingRequest(studyingRequest(RequestStatus.OPEN))
                .grade(grade())
                .proposedPrice(new BigDecimal("150000"))
                .teachingMode(TeachingMode.ONLINE)
                .status(status)
                .build();
        request.setId(requestId);
        return request;
    }
}
