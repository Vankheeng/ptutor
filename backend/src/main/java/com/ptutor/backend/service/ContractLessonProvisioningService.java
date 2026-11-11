package com.ptutor.backend.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractSchedule;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;

import lombok.RequiredArgsConstructor;

/** Creates the concrete lesson rows from a contract's structured schedule. */
@Service
@RequiredArgsConstructor
public class ContractLessonProvisioningService {

    private final LessonRepository lessonRepository;
    private final ContractPaymentInstallmentRepository installmentRepository;
    private final ContractPaymentInstallmentService installmentService;

    @Transactional
    public void provision(Contract contract) {
        if (contract == null || contract.getStartDate() == null || contract.getEndDate() == null
                || contract.getSchedules() == null || contract.getSchedules().isEmpty()
                || contract.getTotalLession() == null || contract.getTotalLession() <= 0) {
            return;
        }

        Set<String> existing = new HashSet<>(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contract.getId())
                .stream().map(lesson -> key(lesson.getDate(), lesson.getStartTime().toString(), lesson.getEndTime().toString())).toList());
        List<Lesson> created = new java.util.ArrayList<>();
        for (LocalDate date = contract.getStartDate(); !date.isAfter(contract.getEndDate()); date = date.plusDays(1)) {
            for (ContractSchedule schedule : contract.getSchedules()) {
                if (schedule.getDayOfWeek() == null || schedule.getDayOfWeek() != date.getDayOfWeek().getValue()) continue;
                if (existing.size() >= contract.getTotalLession()) break;
                String key = key(date, schedule.getStartTime().toString(), schedule.getEndTime().toString());
                if (existing.contains(key)) continue;
                created.add(Lesson.builder().contract(contract).title("Buổi học " + contract.getSubject().getName())
                        .date(date).startTime(schedule.getStartTime()).endTime(schedule.getEndTime())
                        .teachingMode(contract.getTeachingMode()).status(LessonStatus.SCHEDULED)
                        .note(contract.getStatus() == ContractStatus.PENDING ? "Lịch học được tạo sẵn, chờ hợp đồng được ký." : null)
                        .build());
                existing.add(key);
            }
            if (existing.size() >= contract.getTotalLession()) break;
        }
        if (!created.isEmpty()) lessonRepository.saveAll(created);
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            installmentService.ensureForActiveContract(contract);
            List<Lesson> lessons = lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contract.getId());
            for (Lesson lesson : lessons) {
                if (installmentRepository.findByLesson_Id(lesson.getId()).isEmpty()) {
                    installmentService.assignNextInstallment(contract, lesson);
                }
            }
        }
    }

    private String key(LocalDate date, String start, String end) { return date + "|" + start + "|" + end; }
}
