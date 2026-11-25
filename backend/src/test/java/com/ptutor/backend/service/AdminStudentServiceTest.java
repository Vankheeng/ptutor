package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
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

import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.mapper.StudentProfileMapper;
import com.ptutor.backend.repository.AdminStudentTransactionRepository;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class AdminStudentServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock ContractRepository contractRepository;
    @Mock LessonRepository lessonRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock AdminStudentTransactionRepository transactionRepository;
    @Mock StudentProfileMapper studentProfileMapper;
    @Mock ContractMapper contractMapper;

    private AdminStudentService service;
    private UUID userId;
    private Student student;

    @BeforeEach
    void setUp() {
        service = new AdminStudentService(
                studentRepository, contractRepository, lessonRepository, complaintRepository,
                transactionRepository, studentProfileMapper, contractMapper);
        userId = UUID.randomUUID();
        User user = User.builder().email("student@example.com").build();
        user.setId(userId);
        student = Student.builder().user(user).build();
        student.setId(UUID.randomUUID());
    }

    @Test
    void returnsLearningHistoryOnlyForExistingStudent() {
        var pageable = PageRequest.of(0, 20);
        Lesson lesson = lesson();
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(lessonRepository.findAllForStudentUser(userId, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(lesson), pageable, 1));
        when(lessonRepository.countByContract_Student_User_Id(userId)).thenReturn(1L);
        when(lessonRepository.countByContract_Student_User_IdAndStatus(userId, LessonStatus.SCHEDULED))
                .thenReturn(1L);

        var response = service.findLearningHistory(userId, null, null, pageable);

        assertThat(response.summary().total()).isEqualTo(1);
        assertThat(response.summary().scheduled()).isEqualTo(1);
        assertThat(response.lessons().content()).hasSize(1);
        assertThat(response.lessons().content().getFirst().tutorEmail()).isEqualTo("tutor@example.com");
    }

    @Test
    void rejectsContractThatDoesNotBelongToStudent() {
        UUID contractId = UUID.randomUUID();
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findContract(userId, contractId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Contract not found");
    }

    @Test
    void delegatesTransactionFilteringToDatabaseQuery() {
        var pageable = PageRequest.of(1, 10);
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        when(transactionRepository.findAll(
                userId, FinancialTransactionSource.PAYMENT, "PAID", "TUITION_PAYMENT", "VNPAY", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.findTransactions(
                userId, FinancialTransactionSource.PAYMENT, "PAID", "TUITION_PAYMENT", "VNPAY", pageable);

        verify(transactionRepository).findAll(
                userId, FinancialTransactionSource.PAYMENT, "PAID", "TUITION_PAYMENT", "VNPAY", pageable);
    }

    @Test
    void returnsStudentNotFoundForNonStudentTarget() {
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findContracts(userId, ContractStatus.ACTIVE, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Student not found");
    }

    private Lesson lesson() {
        User tutorUser = User.builder()
                .email("tutor@example.com")
                .firstName("Tutor")
                .lastName("One")
                .build();
        tutorUser.setId(UUID.randomUUID());
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
                .status(ContractStatus.ACTIVE)
                .build();
        contract.setId(UUID.randomUUID());
        Lesson lesson = Lesson.builder()
                .contract(contract)
                .title("Algebra")
                .date(LocalDate.of(2026, 9, 27))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 30))
                .teachingMode(TeachingMode.ONLINE)
                .status(LessonStatus.SCHEDULED)
                .build();
        lesson.setId(UUID.randomUUID());
        return lesson;
    }
}
