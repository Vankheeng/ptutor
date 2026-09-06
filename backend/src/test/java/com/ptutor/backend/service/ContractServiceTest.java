package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.ContractTermsRequest;
import com.ptutor.backend.dto.request.ContractUpdateRequest;
import com.ptutor.backend.dto.request.TutorContractCreateRequest;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.GradeTeachingRequestRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock ContractRepository contractRepository;
    @Mock StudentRepository studentRepository;
    @Mock TutorRepository tutorRepository;
    @Mock TutorStudentRequestRepository tutorStudentRequestRepository;
    @Mock StudentTutorRequestRepository studentTutorRequestRepository;
    @Mock GradeRepository gradeRepository;
    @Mock GradeTeachingRequestRepository gradeTeachingRequestRepository;
    @Mock ContractMapper contractMapper;
    @Mock ContractTimeProvider contractTimeProvider;

    private ContractService service;
    private UUID studentUserId;
    private UUID tutorUserId;
    private UUID studyingRequestId;
    private UUID teachingRequestId;
    private UUID tutorRequestId;
    private UUID studentRequestId;
    private Student student;
    private Tutor tutor;
    private Subject subject;
    private Grade grade;

    @BeforeEach
    void setUp() {
        service = new ContractService(
                contractRepository,
                studentRepository,
                tutorRepository,
                tutorStudentRequestRepository,
                studentTutorRequestRepository,
                gradeRepository,
                gradeTeachingRequestRepository,
                contractMapper,
                contractTimeProvider);

        studentUserId = UUID.randomUUID();
        tutorUserId = UUID.randomUUID();
        studyingRequestId = UUID.randomUUID();
        teachingRequestId = UUID.randomUUID();
        tutorRequestId = UUID.randomUUID();
        studentRequestId = UUID.randomUUID();

        student = student(studentUserId);
        tutor = tutor(tutorUserId);
        subject = subject();
        grade = grade();
    }

    @Test
    void studentCreatesPendingContractFromAcceptedTutorProposal() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.OPEN);
        TutorStudentRequest tutorRequest = TutorStudentRequest.builder()
                .tutor(tutor)
                .studyingRequest(studyingRequest)
                .grade(grade)
                .teachingMode(TeachingMode.ONLINE)
                .status(ApplicationStatus.ACCEPTED)
                .build();
        tutorRequest.setId(tutorRequestId);
        when(studentRepository.findByUser_Id(studentUserId)).thenReturn(Optional.of(student));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(contractRepository.existsByTutorStudentRequest_IdAndStatusNot(tutorRequestId, ContractStatus.CANCELLED))
                .thenReturn(false);
        when(contractRepository.saveAndFlush(any(Contract.class))).thenAnswer(invocation -> {
            Contract saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.createFromTutorStudentRequest(studentUserId, studyingRequestId, tutorRequestId, terms());

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).saveAndFlush(captor.capture());
        Contract saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.PENDING);
        assertThat(saved.getStudent()).isSameAs(student);
        assertThat(saved.getTutor()).isSameAs(tutor);
        assertThat(saved.getSubject()).isSameAs(subject);
        assertThat(saved.getGrade()).isSameAs(grade);
        assertThat(saved.getTeachingMode()).isEqualTo(TeachingMode.ONLINE);
        assertThat(saved.getCreatedBy()).isSameAs(student.getUser());
        assertThat(saved.getTutorStudentRequest()).isSameAs(tutorRequest);
    }

    @Test
    void rejectsStudentContractWhenSourceIsNotOpenOrMatched() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.CLOSED);
        TutorStudentRequest tutorRequest = TutorStudentRequest.builder()
                .tutor(tutor).studyingRequest(studyingRequest).grade(grade)
                .teachingMode(TeachingMode.ONLINE).status(ApplicationStatus.ACCEPTED).build();
        when(studentRepository.findByUser_Id(studentUserId)).thenReturn(Optional.of(student));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));

        assertThatThrownBy(() -> service.createFromTutorStudentRequest(
                studentUserId, studyingRequestId, tutorRequestId, terms()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_CONTRACT_SOURCE_STATUS");
                });
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateNonCancelledContractForSameTutorProposal() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.MATCHED);
        TutorStudentRequest tutorRequest = TutorStudentRequest.builder()
                .tutor(tutor).studyingRequest(studyingRequest).grade(grade)
                .teachingMode(TeachingMode.ONLINE).status(ApplicationStatus.ACCEPTED).build();
        when(studentRepository.findByUser_Id(studentUserId)).thenReturn(Optional.of(student));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));
        when(contractRepository.existsByTutorStudentRequest_IdAndStatusNot(tutorRequestId, ContractStatus.CANCELLED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createFromTutorStudentRequest(
                studentUserId, studyingRequestId, tutorRequestId, terms()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("DUPLICATE_CONTRACT"));
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void studentCannotCreateContractFromAnotherStudentsStudyingRequest() {
        Student otherStudent = student(UUID.randomUUID());
        StudyingRequest studyingRequest = StudyingRequest.builder()
                .student(otherStudent).subject(subject).grade(grade).quantity(1)
                .learningMode(LearningMode.ONLINE).status(RequestStatus.OPEN).build();
        studyingRequest.setId(studyingRequestId);
        TutorStudentRequest tutorRequest = TutorStudentRequest.builder()
                .tutor(tutor).studyingRequest(studyingRequest).grade(grade)
                .teachingMode(TeachingMode.ONLINE).status(ApplicationStatus.ACCEPTED).build();
        when(studentRepository.findByUser_Id(studentUserId)).thenReturn(Optional.of(student));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));

        assertThatThrownBy(() -> service.createFromTutorStudentRequest(
                studentUserId, studyingRequestId, tutorRequestId, terms()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("STUDYING_REQUEST_NOT_FOUND");
                });
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void studentProposalMustBeAcceptedBeforeContractCanBeCreated() {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.OPEN);
        TutorStudentRequest tutorRequest = TutorStudentRequest.builder()
                .tutor(tutor).studyingRequest(studyingRequest).grade(grade)
                .teachingMode(TeachingMode.ONLINE).status(ApplicationStatus.PENDING).build();
        when(studentRepository.findByUser_Id(studentUserId)).thenReturn(Optional.of(student));
        when(tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId))
                .thenReturn(Optional.of(tutorRequest));

        assertThatThrownBy(() -> service.createFromTutorStudentRequest(
                studentUserId, studyingRequestId, tutorRequestId, terms()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_CONTRACT_APPLICATION_STATUS"));
    }

    @Test
    void tutorCreatesContractFromAcceptedStudentApplicationWithOverrideGrade() {
        Grade overrideGrade = Grade.builder().name("Grade 11").status(CatalogStatus.ACTIVE).build();
        UUID overrideGradeId = UUID.randomUUID();
        overrideGrade.setId(overrideGradeId);
        TeachingRequest teachingRequest = teachingRequest(RequestStatus.OPEN);
        StudentTutorRequest studentRequest = StudentTutorRequest.builder()
                .student(student).teachingRequest(teachingRequest).grade(grade)
                .learningMode(LearningMode.OFFLINE).status(ApplicationStatus.ACCEPTED).build();
        studentRequest.setId(studentRequestId);
        when(tutorRepository.findByUser_Id(tutorUserId)).thenReturn(Optional.of(tutor));
        when(studentTutorRequestRepository.findByIdAndTeachingRequest_Id(studentRequestId, teachingRequestId))
                .thenReturn(Optional.of(studentRequest));
        when(contractRepository.existsByStudentTutorRequest_IdAndStatusNot(studentRequestId, ContractStatus.CANCELLED))
                .thenReturn(false);
        when(gradeRepository.findById(overrideGradeId)).thenReturn(Optional.of(overrideGrade));
        when(gradeTeachingRequestRepository.existsByTeachingRequest_IdAndGrade_Id(teachingRequestId, overrideGradeId))
                .thenReturn(true);
        when(contractRepository.saveAndFlush(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createFromStudentTutorRequest(
                tutorUserId,
                teachingRequestId,
                studentRequestId,
                new TutorContractCreateRequest(
                        overrideGradeId, BigDecimal.valueOf(250_000), "MONTHLY", 20,
                        "Monday evening", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 11, 2)));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getGrade()).isSameAs(overrideGrade);
        assertThat(captor.getValue().getTeachingMode()).isEqualTo(TeachingMode.OFFLINE);
        assertThat(captor.getValue().getCreatedBy()).isSameAs(tutor.getUser());
        assertThat(captor.getValue().getStudentTutorRequest()).isSameAs(studentRequest);
    }

    @Test
    void rejectsTutorContractFromCustomSubjectTeachingRequest() {
        TeachingRequest teachingRequest = teachingRequest(RequestStatus.OPEN);
        teachingRequest.setSubject(null);
        teachingRequest.setCustomSubjectName("Quantum tutoring");
        StudentTutorRequest studentRequest = StudentTutorRequest.builder()
                .student(student).teachingRequest(teachingRequest).grade(grade)
                .learningMode(LearningMode.ONLINE).status(ApplicationStatus.ACCEPTED).build();
        when(tutorRepository.findByUser_Id(tutorUserId)).thenReturn(Optional.of(tutor));
        when(studentTutorRequestRepository.findByIdAndTeachingRequest_Id(studentRequestId, teachingRequestId))
                .thenReturn(Optional.of(studentRequest));

        assertThatThrownBy(() -> service.createFromStudentTutorRequest(
                tutorUserId, teachingRequestId, studentRequestId, tutorTerms(null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CONTRACT_SUBJECT_REQUIRED"));
    }

    @Test
    void tutorCannotCreateContractFromAnotherTutorsTeachingRequest() {
        Tutor otherTutor = tutor(UUID.randomUUID());
        TeachingRequest teachingRequest = TeachingRequest.builder()
                .tutor(otherTutor).subject(subject).title("Math tutoring").quantity(1)
                .teachingMode(TeachingMode.ONLINE).status(RequestStatus.OPEN).build();
        teachingRequest.setId(teachingRequestId);
        StudentTutorRequest studentRequest = StudentTutorRequest.builder()
                .student(student).teachingRequest(teachingRequest).grade(grade)
                .learningMode(LearningMode.ONLINE).status(ApplicationStatus.ACCEPTED).build();
        when(tutorRepository.findByUser_Id(tutorUserId)).thenReturn(Optional.of(tutor));
        when(studentTutorRequestRepository.findByIdAndTeachingRequest_Id(studentRequestId, teachingRequestId))
                .thenReturn(Optional.of(studentRequest));

        assertThatThrownBy(() -> service.createFromStudentTutorRequest(
                tutorUserId, teachingRequestId, studentRequestId, tutorTerms(null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("TEACHING_REQUEST_NOT_FOUND");
                });
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void listsOnlyContractsReturnedForCurrentParticipant() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractRepository.findAllForParticipant(
                eq(studentUserId), eq(ContractStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1));

        var response = service.findMine(studentUserId, ContractStatus.PENDING, PageRequest.of(0, 20));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        verify(contractRepository).findAllForParticipant(
                eq(studentUserId), eq(ContractStatus.PENDING), any(Pageable.class));
    }

    @Test
    void hidesContractDetailsFromNonParticipants() {
        UUID contractId = UUID.randomUUID();
        when(contractRepository.findDetailedByIdAndParticipantUserId(contractId, studentUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findMineById(studentUserId, contractId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_NOT_FOUND");
                });
    }

    @Test
    void studentCreatorCanUpdateOnlyPriceAndScheduleWhilePending() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractRepository.findDetailedByIdAndParticipantUserIdForUpdate(contract.getId(), studentUserId))
                .thenReturn(Optional.of(contract));
        when(contractRepository.saveAndFlush(contract)).thenReturn(contract);

        service.update(studentUserId, contract.getId(), new ContractUpdateRequest(
                BigDecimal.valueOf(300_000), null, null, "Wednesday evening", null, null, null));

        assertThat(contract.getPrice()).isEqualByComparingTo("300000");
        assertThat(contract.getPreferredSchedule()).isEqualTo("Wednesday evening");
    }

    @Test
    void studentCreatorCannotChangeGrade() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractRepository.findDetailedByIdAndParticipantUserIdForUpdate(contract.getId(), studentUserId))
                .thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.update(studentUserId, contract.getId(), new ContractUpdateRequest(
                null, null, null, null, null, null, UUID.randomUUID())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_FIELD_NOT_EDITABLE");
                });
    }

    @Test
    void counterpartySignsPendingContractAtomically() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractTimeProvider.now()).thenReturn(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        when(contractRepository.findDetailedByIdAndParticipantUserId(contract.getId(), tutorUserId))
                .thenReturn(Optional.of(contract));
        when(contractRepository.activatePendingByCounterparty(
                eq(contract.getId()), eq(tutorUserId), eq(tutor.getUser()), eq(ContractStatus.PENDING),
                eq(ContractStatus.ACTIVE), any(), any())).thenReturn(1);

        service.sign(tutorUserId, contract.getId());

        verify(contractRepository).activatePendingByCounterparty(
                eq(contract.getId()), eq(tutorUserId), eq(tutor.getUser()), eq(ContractStatus.PENDING),
                eq(ContractStatus.ACTIVE), any(), any());
    }

    @Test
    void creatorCannotSignOwnContract() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractRepository.findDetailedByIdAndParticipantUserId(contract.getId(), studentUserId))
                .thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.sign(studentUserId, contract.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_COUNTERPARTY_REQUIRED");
                });
        verify(contractRepository, never()).activatePendingByCounterparty(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reportsConflictWhenAnotherRequestSignsFirst() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractTimeProvider.now()).thenReturn(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        when(contractRepository.findDetailedByIdAndParticipantUserId(contract.getId(), tutorUserId))
                .thenReturn(Optional.of(contract));
        when(contractRepository.activatePendingByCounterparty(
                eq(contract.getId()), eq(tutorUserId), eq(tutor.getUser()), eq(ContractStatus.PENDING),
                eq(ContractStatus.ACTIVE), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.sign(tutorUserId, contract.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_CONTRACT_STATUS_TRANSITION"));
    }

    @Test
    void creatorCancelsPendingContract() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractTimeProvider.now()).thenReturn(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        when(contractRepository.findDetailedByIdAndParticipantUserId(contract.getId(), studentUserId))
                .thenReturn(Optional.of(contract));
        when(contractRepository.cancelPendingByCreator(
                eq(contract.getId()), eq(studentUserId), eq(ContractStatus.PENDING), eq(ContractStatus.CANCELLED), any()))
                .thenReturn(1);

        service.cancel(studentUserId, contract.getId());

        verify(contractRepository).cancelPendingByCreator(
                eq(contract.getId()), eq(studentUserId), eq(ContractStatus.PENDING), eq(ContractStatus.CANCELLED), any());
    }

    @Test
    void counterpartyCannotCancelCreatorsPendingContract() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractRepository.findDetailedByIdAndParticipantUserId(contract.getId(), tutorUserId))
                .thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.cancel(tutorUserId, contract.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_CREATOR_REQUIRED");
                });
    }

    @Test
    void counterpartyRejectsPendingContract() {
        Contract contract = studentOriginContract(ContractStatus.PENDING);
        when(contractTimeProvider.now()).thenReturn(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        when(contractRepository.findDetailedByIdAndParticipantUserId(contract.getId(), tutorUserId))
                .thenReturn(Optional.of(contract));
        when(contractRepository.rejectPendingByCounterparty(
                eq(contract.getId()), eq(tutorUserId), eq(ContractStatus.PENDING), eq(ContractStatus.CANCELLED), any()))
                .thenReturn(1);

        service.reject(tutorUserId, contract.getId());

        verify(contractRepository).rejectPendingByCounterparty(
                eq(contract.getId()), eq(tutorUserId), eq(ContractStatus.PENDING), eq(ContractStatus.CANCELLED), any());
    }

    @Test
    void participantCanProposeRenewalForActiveContract() {
        Contract active = studentOriginContract(ContractStatus.ACTIVE);
        active.setEndDate(LocalDate.of(2026, 10, 1));
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractRepository.findDetailedByIdAndParticipantUserId(active.getId(), tutorUserId))
                .thenReturn(Optional.of(active));
        when(contractRepository.existsByRenewedFromContract_IdAndStatusNot(active.getId(), ContractStatus.CANCELLED))
                .thenReturn(false);
        when(contractRepository.saveAndFlush(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.renew(tutorUserId, active.getId(), new ContractTermsRequest(
                BigDecimal.valueOf(220_000), "MONTHLY", 16, "Weekend mornings",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1)));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).saveAndFlush(captor.capture());
        Contract renewal = captor.getValue();
        assertThat(renewal.getRenewedFromContract()).isSameAs(active);
        assertThat(renewal.getCreatedBy()).isSameAs(tutor.getUser());
        assertThat(renewal.getStatus()).isEqualTo(ContractStatus.PENDING);
        assertThat(renewal.getGrade()).isSameAs(active.getGrade());
    }

    @Test
    void rejectsDuplicatePendingOrActiveRenewal() {
        Contract active = studentOriginContract(ContractStatus.ACTIVE);
        active.setEndDate(LocalDate.of(2026, 10, 1));
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractRepository.findDetailedByIdAndParticipantUserId(active.getId(), tutorUserId))
                .thenReturn(Optional.of(active));
        when(contractRepository.existsByRenewedFromContract_IdAndStatusNot(active.getId(), ContractStatus.CANCELLED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.renew(tutorUserId, active.getId(), new ContractTermsRequest(
                BigDecimal.valueOf(220_000), "MONTHLY", 16, "Weekend mornings",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("DUPLICATE_CONTRACT_RENEWAL"));
        verify(contractRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRenewalThatOverlapsPreviousContract() {
        Contract active = studentOriginContract(ContractStatus.ACTIVE);
        active.setEndDate(LocalDate.of(2026, 10, 1));
        when(contractTimeProvider.today()).thenReturn(LocalDate.of(2026, 9, 1));
        when(contractRepository.findDetailedByIdAndParticipantUserId(active.getId(), tutorUserId))
                .thenReturn(Optional.of(active));
        when(contractRepository.existsByRenewedFromContract_IdAndStatusNot(active.getId(), ContractStatus.CANCELLED))
                .thenReturn(false);

        assertThatThrownBy(() -> service.renew(tutorUserId, active.getId(), new ContractTermsRequest(
                BigDecimal.valueOf(220_000), "MONTHLY", 16, "Weekend mornings",
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 12, 1))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_CONTRACT_DATE_RANGE"));
        verify(contractRepository, never()).saveAndFlush(any());
    }

    private ContractTermsRequest terms() {
        return new ContractTermsRequest(
                BigDecimal.valueOf(200_000), "MONTHLY", 16, "Monday and Wednesday evening",
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 11, 2));
    }

    private TutorContractCreateRequest tutorTerms(UUID gradeId) {
        return new TutorContractCreateRequest(
                gradeId, BigDecimal.valueOf(200_000), "MONTHLY", 16,
                "Monday and Wednesday evening", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 11, 2));
    }

    private Contract studentOriginContract(ContractStatus status) {
        StudyingRequest studyingRequest = studyingRequest(RequestStatus.MATCHED);
        TutorStudentRequest origin = TutorStudentRequest.builder()
                .tutor(tutor).studyingRequest(studyingRequest).grade(grade).teachingMode(TeachingMode.ONLINE)
                .status(ApplicationStatus.ACCEPTED).build();
        Contract contract = Contract.builder()
                .student(student).tutor(tutor).subject(subject).grade(grade).teachingMode(TeachingMode.ONLINE)
                .price(BigDecimal.valueOf(200_000)).paymentPeriod("MONTHLY").totalLession(16)
                .preferredSchedule("Monday evening").startDate(LocalDate.of(2026, 9, 2))
                .endDate(LocalDate.of(2026, 11, 2)).status(status).createdBy(student.getUser())
                .tutorStudentRequest(origin).build();
        contract.setId(UUID.randomUUID());
        return contract;
    }

    private StudyingRequest studyingRequest(RequestStatus status) {
        StudyingRequest request = StudyingRequest.builder()
                .student(student).subject(subject).grade(grade).quantity(1)
                .learningMode(LearningMode.ONLINE).status(status).build();
        request.setId(studyingRequestId);
        return request;
    }

    private TeachingRequest teachingRequest(RequestStatus status) {
        TeachingRequest request = TeachingRequest.builder()
                .tutor(tutor).subject(subject).title("Math tutoring").quantity(1)
                .teachingMode(TeachingMode.ONLINE).status(status).build();
        request.setId(teachingRequestId);
        return request;
    }

    private Student student(UUID userId) {
        User user = User.builder().email("student@example.com").build();
        user.setId(userId);
        Student value = Student.builder().user(user).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Tutor tutor(UUID userId) {
        User user = User.builder().email("tutor@example.com").build();
        user.setId(userId);
        Tutor value = Tutor.builder().user(user).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Subject subject() {
        Subject value = Subject.builder().name("Mathematics").status(CatalogStatus.ACTIVE).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Grade grade() {
        Grade value = Grade.builder().name("Grade 10").status(CatalogStatus.ACTIVE).build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
