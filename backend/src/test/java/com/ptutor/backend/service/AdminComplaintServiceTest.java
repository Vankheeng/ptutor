package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.PaymentPeriod;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class AdminComplaintServiceTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock LessonRepository lessonRepository;
    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private AdminComplaintService service;
    private UUID reviewerUserId;
    private UUID complaintId;
    private Employee reviewer;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-26T03:00:00Z"), ZoneOffset.UTC);
        service = new AdminComplaintService(
                complaintRepository, evidenceRepository, employeeRepository, lessonRepository,
                installmentRepository, paymentRepository, eventPublisher, clock);
        reviewerUserId = UUID.randomUUID();
        complaintId = UUID.randomUUID();
        User reviewerUser = user(reviewerUserId, "Employee", "One", "employee@ptutor.com");
        reviewer = Employee.builder().user(reviewerUser).role(EmployeeRole.ADMIN).build();
        reviewer.setId(UUID.randomUUID());
    }

    @Test
    void startsReviewAndAssignsAuthenticatedEmployee() {
        Complaint complaint = complaint(ComplaintStatus.PENDING, null);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        stubRelatedData(complaint.getContract().getId());

        var response = service.startReview(reviewerUserId, complaintId);

        assertThat(response.status()).isEqualTo(ComplaintStatus.IN_REVIEW);
        assertThat(response.reviewer().employeeId()).isEqualTo(reviewer.getId());
        assertThat(complaint.getEmployee()).isEqualTo(reviewer);
    }

    @Test
    void requestsEvidenceAndPublishesNotification() {
        Complaint complaint = complaint(ComplaintStatus.IN_REVIEW, reviewer);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        stubRelatedData(complaint.getContract().getId());

        var response = service.requestEvidence(reviewerUserId, complaintId, "  Provide a payment receipt  ");

        assertThat(response.status()).isEqualTo(ComplaintStatus.AWAITING_EVIDENCE);
        assertThat(response.resolution()).isEqualTo("Provide a payment receipt");
        ArgumentCaptor<NotificationDomainEvent> captor = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(NotificationEventType.COMPLAINT_EVIDENCE_REQUESTED);
        assertThat(captor.getValue().recipientUserId()).isEqualTo(complaint.getUser().getId());
    }

    @Test
    void rejectsDecisionByEmployeeWhoIsNotAssigned() {
        Complaint complaint = complaint(ComplaintStatus.IN_REVIEW, reviewer);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.accept(UUID.randomUUID(), complaintId, "Valid complaint"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("COMPLAINT_REVIEW_FORBIDDEN");
                });
    }

    @Test
    void rejectsDecisionWhileWaitingForEvidence() {
        Complaint complaint = complaint(ComplaintStatus.AWAITING_EVIDENCE, reviewer);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.reject(reviewerUserId, complaintId, "Insufficient evidence"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_COMPLAINT_STATUS_TRANSITION");
                });
    }

    @Test
    void acceptsComplaintAndSetsServerResolutionTime() {
        Complaint complaint = complaint(ComplaintStatus.IN_REVIEW, reviewer);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        stubRelatedData(complaint.getContract().getId());

        var response = service.accept(reviewerUserId, complaintId, "Complaint is valid");

        assertThat(response.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(response.resolvedAt()).isEqualTo("2026-09-26T03:00:00");
        verify(eventPublisher).publishEvent(any(NotificationDomainEvent.class));
    }

    @Test
    void adminReassignsComplaintWithoutChangingItsState() {
        Complaint complaint = complaint(ComplaintStatus.AWAITING_EVIDENCE, reviewer);
        complaint.setResolution("Provide a payment receipt");
        User targetUser = user(UUID.randomUUID(), "Employee", "Two", "employee2@ptutor.com");
        Employee targetEmployee = Employee.builder()
                .user(targetUser)
                .role(EmployeeRole.EMPLOYEE)
                .jobFunction(EmployeeJobFunction.COMPLAINT_HANDLER)
                .build();
        targetEmployee.setId(UUID.randomUUID());
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));
        when(employeeRepository.findById(targetEmployee.getId())).thenReturn(Optional.of(targetEmployee));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        stubRelatedData(complaint.getContract().getId());

        var response = service.reassign(reviewerUserId, complaintId, targetEmployee.getId());

        assertThat(response.status()).isEqualTo(ComplaintStatus.AWAITING_EVIDENCE);
        assertThat(response.resolution()).isEqualTo("Provide a payment receipt");
        assertThat(response.reviewer().employeeId()).isEqualTo(targetEmployee.getId());
        assertThat(complaint.getEmployee()).isEqualTo(targetEmployee);
    }

    @Test
    void regularEmployeeCannotReassignComplaint() {
        reviewer.setRole(EmployeeRole.EMPLOYEE);
        Complaint complaint = complaint(ComplaintStatus.IN_REVIEW, reviewer);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(employeeRepository.findByUser_Id(reviewerUserId)).thenReturn(Optional.of(reviewer));

        assertThatThrownBy(() -> service.reassign(reviewerUserId, complaintId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getCode()).isEqualTo("COMPLAINT_REASSIGN_FORBIDDEN");
                });
    }

    private void stubRelatedData(UUID contractId) {
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)))
                .thenReturn(List.of());
        when(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contractId)).thenReturn(List.of());
        when(installmentRepository.findAllByContract_IdOrderBySequenceNumberAsc(contractId)).thenReturn(List.of());
        when(paymentRepository.findAllForContract(any(), any())).thenReturn(List.of());
    }

    private Complaint complaint(ComplaintStatus status, Employee employee) {
        User studentUser = user(UUID.randomUUID(), "Student", "User", "student@example.com");
        User tutorUser = user(UUID.randomUUID(), "Tutor", "User", "tutor@example.com");
        Student student = Student.builder().user(studentUser).build();
        student.setId(UUID.randomUUID());
        Tutor tutor = Tutor.builder().user(tutorUser).build();
        tutor.setId(UUID.randomUUID());
        Subject subject = Subject.builder().name("Mathematics").build();
        subject.setId(UUID.randomUUID());
        Grade grade = Grade.builder().name("Grade 10").build();
        grade.setId(UUID.randomUUID());
        Contract contract = Contract.builder()
                .student(student)
                .tutor(tutor)
                .subject(subject)
                .grade(grade)
                .teachingMode(TeachingMode.ONLINE)
                .price(BigDecimal.valueOf(500000))
                .paymentPeriod(PaymentPeriod.MONTHLY)
                .totalLession(8)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .status(ContractStatus.ACTIVE)
                .build();
        contract.setId(UUID.randomUUID());
        Complaint complaint = Complaint.builder()
                .user(studentUser)
                .contract(contract)
                .employee(employee)
                .title("Lesson issue")
                .content("The lesson did not take place")
                .status(status)
                .build();
        complaint.setId(complaintId);
        return complaint;
    }

    private User user(UUID id, String firstName, String lastName, String email) {
        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return user;
    }
}
