package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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

import com.ptutor.backend.dto.request.LessonRequest;
import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.LessonMapper;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock LessonRepository lessonRepository;
    @Mock ContractRepository contractRepository;
    @Mock TutorRepository tutorRepository;
    @Mock StudentRepository studentRepository;
    @Mock ComplaintRepository complaintRepository;

    private LessonService service;
    private UUID userId;
    private UUID tutorId;
    private UUID studentId;
    private UUID contractId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        service = new LessonService(
                lessonRepository,
                contractRepository,
                tutorRepository,
                studentRepository,
                complaintRepository,
                testMapper(),
                Clock.fixed(Instant.parse("2026-09-12T21:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsScheduledLessonForFutureSchedule() {
        stubTutor();
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract()));
        when(lessonRepository.saveAndFlush(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            lesson.setId(UUID.randomUUID());
            return lesson;
        });

        LessonResponse response = service.create(userId, contractId, createRequest());

        assertThat(response.contractId()).isEqualTo(contractId);
        assertThat(response.status()).isEqualTo(LessonStatus.SCHEDULED);
        assertThat(response.teachingMode()).isEqualTo(TeachingMode.ONLINE);
    }

    @Test
    void createsPendingConfirmationLessonForFinishedSchedule() {
        stubTutor();
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract()));
        when(lessonRepository.saveAndFlush(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson lesson = invocation.getArgument(0);
            lesson.setId(UUID.randomUUID());
            return lesson;
        });

        LessonResponse response = service.create(userId, contractId, new LessonRequest(
                "Finished lesson",
                LocalDate.of(2026, 9, 12),
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                null,
                null));

        assertThat(response.status()).isEqualTo(LessonStatus.PENDING_CONFIRMATION);
    }

    @Test
    void listsLessonsByContractAndStatus() {
        stubTutor();
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract()));
        Lesson lesson = lesson(UUID.randomUUID(), LessonStatus.SCHEDULED);
        PageRequest pageable = PageRequest.of(0, 20);
        when(lessonRepository.findAllByContract_IdAndStatus(contractId, LessonStatus.SCHEDULED, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(lesson), pageable, 1));

        var response = service.findByContract(userId, contractId, LessonStatus.SCHEDULED, pageable);

        assertThat(response.content()).singleElement().extracting(LessonResponse::id).isEqualTo(lesson.getId());
        verify(lessonRepository).findAllByContract_IdAndStatus(contractId, LessonStatus.SCHEDULED, pageable);
    }

    @Test
    void updatesOnlyScheduledLesson() {
        stubTutor();
        UUID lessonId = UUID.randomUUID();
        Lesson lesson = lesson(lessonId, LessonStatus.SCHEDULED);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.saveAndFlush(lesson)).thenReturn(lesson);

        LessonResponse response = service.update(userId, lessonId, updateRequest());

        assertThat(response.title()).isEqualTo("Updated lesson");
        assertThat(response.startTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void cannotUpdateNonScheduledLesson() {
        stubTutor();
        UUID lessonId = UUID.randomUUID();
        when(lessonRepository.findById(lessonId))
                .thenReturn(Optional.of(lesson(lessonId, LessonStatus.PENDING_CONFIRMATION)));

        assertThatThrownBy(() -> service.update(userId, lessonId, updateRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_LESSON_STATUS_TRANSITION");
                });
    }

    @Test
    void marksFinishedScheduledLessonAsPendingConfirmation() {
        stubTutor();
        UUID lessonId = UUID.randomUUID();
        Lesson lesson = lesson(lessonId, LessonStatus.SCHEDULED);
        lesson.setDate(LocalDate.of(2026, 9, 12));
        lesson.setEndTime(LocalTime.of(19, 0));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.saveAndFlush(lesson)).thenReturn(lesson);

        LessonResponse response = service.updateStatus(
                userId, lessonId,
                new LessonRequest(null, null, null, null, null, LessonStatus.PENDING_CONFIRMATION));

        assertThat(response.status()).isEqualTo(LessonStatus.PENDING_CONFIRMATION);
    }

    @Test
    void studentConfirmsPendingLessonWhenThereIsNoBlockingComplaint() {
        UUID lessonId = UUID.randomUUID();
        Lesson lesson = lesson(lessonId, LessonStatus.PENDING_CONFIRMATION);
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student()));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(complaintRepository.existsByContract_IdAndStatusIn(eq(contractId), any())).thenReturn(false);
        when(lessonRepository.saveAndFlush(lesson)).thenReturn(lesson);

        LessonResponse response = service.confirmByStudent(userId, lessonId);

        assertThat(response.status()).isEqualTo(LessonStatus.CONFIRMED);
    }

    @Test
    void studentCannotConfirmWhenContractHasBlockingComplaint() {
        UUID lessonId = UUID.randomUUID();
        Lesson lesson = lesson(lessonId, LessonStatus.PENDING_CONFIRMATION);
        when(studentRepository.findByUser_Id(userId)).thenReturn(Optional.of(student()));
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(complaintRepository.existsByContract_IdAndStatusIn(eq(contractId), any())).thenReturn(true);

        assertThatThrownBy(() -> service.confirmByStudent(userId, lessonId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("LESSON_COMPLETION_ON_HOLD");
                });
    }

    @Test
    void autoCompletesExpiredPendingLessonsWithoutBlockingComplaint() {
        Lesson lesson = lesson(UUID.randomUUID(), LessonStatus.PENDING_CONFIRMATION);
        lesson.setDate(LocalDate.of(2026, 9, 9));
        lesson.setEndTime(LocalTime.of(20, 0));
        when(lessonRepository.findAllFinishedBefore(
                LessonStatus.PENDING_CONFIRMATION, LocalDate.of(2026, 9, 9), LocalTime.of(21, 0)))
                .thenReturn(java.util.List.of(lesson));
        when(complaintRepository.existsByContract_IdAndStatusIn(eq(contractId), any())).thenReturn(false);

        int completedCount = service.completeExpiredPendingLessons(3);

        assertThat(completedCount).isEqualTo(1);
        assertThat(lesson.getStatus()).isEqualTo(LessonStatus.COMPLETED);
        verify(lessonRepository).saveAll(java.util.List.of(lesson));
    }

    @Test
    void doesNotAutoCompleteWhenContractHasBlockingComplaint() {
        Lesson lesson = lesson(UUID.randomUUID(), LessonStatus.PENDING_CONFIRMATION);
        lesson.setDate(LocalDate.of(2026, 9, 9));
        lesson.setEndTime(LocalTime.of(20, 0));
        when(lessonRepository.findAllFinishedBefore(
                LessonStatus.PENDING_CONFIRMATION, LocalDate.of(2026, 9, 9), LocalTime.of(21, 0)))
                .thenReturn(java.util.List.of(lesson));
        when(complaintRepository.existsByContract_IdAndStatusIn(eq(contractId), any())).thenReturn(true);

        int completedCount = service.completeExpiredPendingLessons(3);

        assertThat(completedCount).isZero();
        assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PENDING_CONFIRMATION);
    }

    private LessonRequest createRequest() {
        return new LessonRequest(
                "Lesson 1",
                LocalDate.of(2026, 9, 13),
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
                "Prepare chapter 1",
                null);
    }

    private LessonRequest updateRequest() {
        return new LessonRequest(
                "Updated lesson",
                LocalDate.of(2026, 9, 13),
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                "Updated note",
                null);
    }

    private Tutor tutor() {
        User user = User.builder().email("tutor@example.com").build();
        user.setId(userId);
        Tutor tutor = Tutor.builder().user(user).build();
        tutor.setId(tutorId);
        return tutor;
    }

    private void stubTutor() {
        when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
    }

    private Student student() {
        User user = User.builder().email("student@example.com").build();
        user.setId(userId);
        Student student = Student.builder().user(user).build();
        student.setId(studentId);
        return student;
    }

    private Contract contract() {
        Contract contract = Contract.builder()
                .student(student())
                .tutor(tutor())
                .teachingMode(TeachingMode.ONLINE)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .build();
        contract.setId(contractId);
        return contract;
    }

    private Lesson lesson(UUID lessonId, LessonStatus status) {
        Lesson lesson = Lesson.builder()
                .contract(contract())
                .title("Lesson 1")
                .date(LocalDate.of(2026, 9, 12))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .teachingMode(TeachingMode.ONLINE)
                .note("Prepare chapter 1")
                .status(status)
                .build();
        lesson.setId(lessonId);
        return lesson;
    }

    private LessonMapper testMapper() {
        return lesson -> new LessonResponse(
                lesson.getId(),
                lesson.getContract().getId(),
                lesson.getTitle(),
                lesson.getDate(),
                lesson.getStartTime(),
                lesson.getEndTime(),
                lesson.getTeachingMode(),
                lesson.getStatus(),
                lesson.getNote(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }
}
