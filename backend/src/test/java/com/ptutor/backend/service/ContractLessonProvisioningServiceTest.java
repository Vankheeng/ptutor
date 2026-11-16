package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractSchedule;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.LessonRepository;

@ExtendWith(MockitoExtension.class)
class ContractLessonProvisioningServiceTest {

    @Mock LessonRepository lessonRepository;
    @Mock ContractPaymentInstallmentRepository installmentRepository;
    @Mock ContractPaymentInstallmentService installmentService;

    @Test
    void changingPendingScheduleRemovesOldLessonsAndCreatesNewLessons() {
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .status(ContractStatus.PENDING)
                .subject(Subject.builder().name("Toán").build())
                .startDate(LocalDate.of(2026, 9, 2))
                .endDate(LocalDate.of(2026, 9, 9))
                .totalLession(2)
                .build();
        contract.setId(contractId);
        contract.getSchedules().add(ContractSchedule.builder().contract(contract).dayOfWeek(3)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0)).build());
        Lesson oldLesson = Lesson.builder().contract(contract).date(LocalDate.of(2026, 9, 2))
                .startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(17, 0)).build();
        when(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contractId))
                .thenReturn(List.of(oldLesson), List.of());

        new ContractLessonProvisioningService(lessonRepository, installmentRepository, installmentService)
                .syncPendingLessons(contract);

        verify(lessonRepository).deleteAll(List.of(oldLesson));
        verify(lessonRepository).flush();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lesson>> created = ArgumentCaptor.forClass(List.class);
        verify(lessonRepository).saveAll(created.capture());
        assertThat(created.getValue()).hasSize(2);
        assertThat(created.getValue()).extracting(Lesson::getStartTime).containsOnly(LocalTime.of(18, 0));
    }

    @Test
    void createsSavedLessonsFromLegacyScheduleForActiveContract() {
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .status(ContractStatus.ACTIVE)
                .subject(Subject.builder().name("Toán").build())
                .startDate(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 12))
                .totalLession(3)
                .preferredSchedule("Thu 2 va Thu 5, 19:00-20:00")
                .build();
        contract.setId(contractId);
        when(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contractId))
                .thenReturn(List.of(), List.of());

        new ContractLessonProvisioningService(lessonRepository, installmentRepository, installmentService)
                .provision(contract);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lesson>> created = ArgumentCaptor.forClass(List.class);
        verify(lessonRepository).saveAll(created.capture());
        assertThat(created.getValue()).extracting(Lesson::getDate).containsExactly(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12));
        assertThat(created.getValue()).extracting(Lesson::getStartTime).containsOnly(LocalTime.of(19, 0));
        verify(installmentService).ensureForActiveContract(contract);
    }

    @Test
    void clearsPendingSignatureNoteWhenContractBecomesActive() {
        UUID contractId = UUID.randomUUID();
        Contract contract = Contract.builder()
                .status(ContractStatus.ACTIVE)
                .subject(Subject.builder().name("Toán").build())
                .startDate(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 6))
                .totalLession(1)
                .preferredSchedule("Thu 2, 19:00-20:00")
                .build();
        contract.setId(contractId);
        Lesson lesson = Lesson.builder().contract(contract).date(LocalDate.of(2026, 10, 5))
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(20, 0))
                .note("Lịch học được tạo sẵn, chờ hợp đồng được ký.").build();
        when(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contractId))
                .thenReturn(List.of(lesson), List.of(lesson));

        new ContractLessonProvisioningService(lessonRepository, installmentRepository, installmentService)
                .provision(contract);

        assertThat(lesson.getNote()).isNull();
        verify(lessonRepository).saveAll(List.of(lesson));
    }

    @Test
    void doesNotCreateNewLessonsForCompletedContract() {
        Contract contract = Contract.builder()
                .status(ContractStatus.COMPLETED)
                .startDate(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 12))
                .totalLession(3)
                .preferredSchedule("Thu 2 va Thu 5, 19:00-20:00")
                .build();

        new ContractLessonProvisioningService(lessonRepository, installmentRepository, installmentService)
                .provision(contract);

        verifyNoInteractions(lessonRepository);
    }
}
