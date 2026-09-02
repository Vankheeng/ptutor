package com.ptutor.backend.tutor.service;

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
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.tutor.dto.TutorStudentRequestRequest;
import com.ptutor.backend.tutor.dto.TutorStudentRequestResponse;
import com.ptutor.backend.tutor.repository.TutorStudentRequestRepository;

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
    private UUID gradeId;

    @BeforeEach
    void setUp() {
        service = new TutorStudentRequestService(
                tutorStudentRequestRepository, studyingRequestRepository, tutorRepository, gradeRepository);
        userId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        studyingRequestId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
    }

    @Test
    void createsPendingProposalForOpenStudyingRequest() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.OPEN);
        when(studyingRequestRepository.findById(studyingRequestId)).thenReturn(Optional.of(studyingRequest));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        when(tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatusNot(
                tutorId, studyingRequestId, ApplicationStatus.CANCELLED)).thenReturn(false);
        when(tutorStudentRequestRepository.saveAndFlush(any(TutorStudentRequest.class)))
                .thenAnswer(invocation -> {
                    TutorStudentRequest proposal = invocation.getArgument(0);
                    proposal.setId(UUID.randomUUID());
                    return proposal;
                });

        TutorStudentRequestResponse response = service.create(userId, proposalRequest());

        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.studyingRequestId()).isEqualTo(studyingRequestId);
        assertThat(response.gradeId()).isEqualTo(gradeId);
        assertThat(response.proposedPrice()).isEqualByComparingTo("150000");
        assertThat(response.message()).isEqualTo("I can help the student prepare for exams.");
    }

    @Test
    void rejectsProposalForNonOpenStudyingRequest() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.CLOSED);
        when(studyingRequestRepository.findById(studyingRequestId)).thenReturn(Optional.of(studyingRequest));

        assertThatThrownBy(() -> service.create(userId, proposalRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_OPEN");
                });

        verifyNoGradeLookup();
    }

    @Test
    void rejectsProposalWithDifferentGrade() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.OPEN);
        Grade anotherGrade = Grade.builder().name("Grade 10").status(CatalogStatus.ACTIVE).build();
        anotherGrade.setId(UUID.randomUUID());
        studyingRequest.setGrade(anotherGrade);
        when(studyingRequestRepository.findById(studyingRequestId)).thenReturn(Optional.of(studyingRequest));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(anotherGrade));

        assertThatThrownBy(() -> service.create(userId, proposalRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("GRADE_MISMATCH");
                });
    }

    @Test
    void rejectsDuplicateNonCancelledProposal() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.OPEN);
        when(studyingRequestRepository.findById(studyingRequestId)).thenReturn(Optional.of(studyingRequest));
        when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        when(tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatusNot(
                tutorId, studyingRequestId, ApplicationStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, proposalRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("TUTOR_PROPOSAL_ALREADY_EXISTS");
                });

        verify(tutorStudentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsOwnProposalsByStatus() {
        when(tutorStudentRequestRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                tutorId, ApplicationStatus.PENDING)).thenReturn(List.of());

        assertThat(service.findMine(userId, ApplicationStatus.PENDING)).isEmpty();

        verify(tutorStudentRequestRepository)
                .findAllByTutor_IdAndStatusOrderByCreatedAtDesc(tutorId, ApplicationStatus.PENDING);
    }

    @Test
    void cancelsPendingOwnProposal() {
        TutorStudentRequest proposal = proposal(ApplicationStatus.PENDING);
        UUID proposalId = proposal.getId();
        when(tutorStudentRequestRepository.findByIdAndTutor_Id(proposalId, tutorId))
                .thenReturn(Optional.of(proposal));
        when(tutorStudentRequestRepository.saveAndFlush(proposal)).thenReturn(proposal);

        TutorStudentRequestResponse response = service.cancel(userId, proposalId);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    @Test
    void rejectsCancellingAcceptedProposal() {
        TutorStudentRequest proposal = proposal(ApplicationStatus.ACCEPTED);
        UUID proposalId = proposal.getId();
        when(tutorStudentRequestRepository.findByIdAndTutor_Id(proposalId, tutorId))
                .thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.cancel(userId, proposalId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TUTOR_PROPOSAL_STATUS_TRANSITION");
                });
    }

    private TutorStudentRequestRequest proposalRequest() {
        return new TutorStudentRequestRequest(
                studyingRequestId,
                gradeId,
                new BigDecimal("150000"),
                TeachingMode.ONLINE,
                "Weekday evenings",
                "I can help the student prepare for exams.");
    }

    private TutorStudentRequest proposal(ApplicationStatus status) {
        TutorStudentRequest proposal = TutorStudentRequest.builder()
                .tutor(tutor())
                .studyingRequest(studyingRequest(RequestStatus.OPEN))
                .grade(grade())
                .proposedPrice(new BigDecimal("150000"))
                .teachingMode(TeachingMode.ONLINE)
                .status(status)
                .build();
        proposal.setId(UUID.randomUUID());
        return proposal;
    }

    private StudyingRequest studyingRequest(RequestStatus status) {
        Subject subject = Subject.builder().name("Mathematics").status(CatalogStatus.ACTIVE).build();
        subject.setId(UUID.randomUUID());
        Grade grade = grade();
        StudyingRequest studyingRequest = StudyingRequest.builder()
                .student(Student.builder().build())
                .subject(subject)
                .grade(grade)
                .title("Need a mathematics tutor")
                .status(status)
                .build();
        studyingRequest.setId(studyingRequestId);
        return studyingRequest;
    }

    private Grade grade() {
        Grade grade = Grade.builder().name("Grade 9").status(CatalogStatus.ACTIVE).build();
        grade.setId(gradeId);
        return grade;
    }

    private Tutor tutor() {
        Tutor tutor = Tutor.builder().build();
        tutor.setId(tutorId);
        return tutor;
    }

    private void verifyNoGradeLookup() {
        verify(gradeRepository, never()).findById(any());
    }
}
