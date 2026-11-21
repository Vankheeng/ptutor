package com.ptutor.backend.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.exception.ApiException;

import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;

/** Creates the concrete lesson rows from a contract's structured schedule. */
@Service
@RequiredArgsConstructor
public class ContractLessonProvisioningService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern LEGACY_DAY = Pattern.compile(
            "\\b(?:thu\\s*(?:[2-7]|hai|ba|tu|nam|sau|bay)|t[2-7]|chu\\s*nhat|cn|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b");
    private static final Pattern LEGACY_TIME = Pattern.compile(
            "(\\d{1,2}:\\d{2})\\s*(?:-|–|—|to)\\s*(\\d{1,2}:\\d{2})");
    private static final String PENDING_LESSON_NOTE = "Lịch học được tạo sẵn, chờ hợp đồng được ký.";

    private final LessonRepository lessonRepository;
    private final ContractPaymentInstallmentRepository installmentRepository;
    private final ContractPaymentInstallmentService installmentService;

    @Transactional
    public void syncPendingLessons(Contract contract) {
        if (contract.getStatus() != ContractStatus.PENDING) return;

        List<ScheduleSlot> slots = scheduleSlots(contract);
        Set<String> expected = new HashSet<>();
        for (LocalDate date = contract.getStartDate(); !date.isAfter(contract.getEndDate()); date = date.plusDays(1)) {
            for (ScheduleSlot slot : slots) {
                if (slot.dayOfWeek() == date.getDayOfWeek().getValue()) {
                    expected.add(key(date, slot.startTime().toString(), slot.endTime().toString()));
                }
            }
        }
        List<Lesson> obsolete = lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contract.getId()).stream()
                .filter(lesson -> !expected.contains(key(lesson.getDate(), lesson.getStartTime().toString(), lesson.getEndTime().toString())))
                .toList();
        if (!obsolete.isEmpty()) {
            lessonRepository.deleteAll(obsolete);
            lessonRepository.flush();
        }
        provision(contract);
    }

    @Transactional
    public void provision(Contract contract) {
        if (contract == null || contract.getStartDate() == null || contract.getEndDate() == null
                || (contract.getStatus() != ContractStatus.PENDING && contract.getStatus() != ContractStatus.ACTIVE)
                || contract.getTotalLession() == null || contract.getTotalLession() <= 0) {
            return;
        }
        List<ScheduleSlot> slots = scheduleSlots(contract);
        if (slots.isEmpty()) return;

        Set<String> existing = new HashSet<>(lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contract.getId())
                .stream().map(lesson -> key(lesson.getDate(), lesson.getStartTime().toString(), lesson.getEndTime().toString())).toList());
        List<Lesson> created = new java.util.ArrayList<>();
        for (LocalDate date = contract.getStartDate(); !date.isAfter(contract.getEndDate()); date = date.plusDays(1)) {
            for (ScheduleSlot slot : slots) {
                if (slot.dayOfWeek() != date.getDayOfWeek().getValue()) continue;
                if (existing.size() >= contract.getTotalLession()) break;
                String key = key(date, slot.startTime().toString(), slot.endTime().toString());
                if (existing.contains(key)) continue;
                LocalDate candidateDate = date;
                boolean conflictsWithParticipants = contract.getStudent() != null && contract.getStudent().getUser() != null
                        && contract.getTutor() != null && contract.getTutor().getUser() != null
                        && lessonRepository.existsScheduleConflict(
                        contract.getStudent().getUser().getId(), contract.getTutor().getUser().getId(),
                        candidateDate, slot.startTime(), slot.endTime(), null);
                if (conflictsWithParticipants
                        || created.stream().anyMatch(lesson -> overlaps(
                                lesson.getDate(), lesson.getStartTime(), lesson.getEndTime(),
                                candidateDate, slot.startTime(), slot.endTime()))) {
                    continue;
                }
                created.add(Lesson.builder().contract(contract).title("Buổi học " + contract.getSubject().getName())
                        .date(date).startTime(slot.startTime()).endTime(slot.endTime())
                        .teachingMode(contract.getTeachingMode()).status(LessonStatus.SCHEDULED)
                        .note(contract.getStatus() == ContractStatus.PENDING ? PENDING_LESSON_NOTE : null)
                        .build());
                existing.add(key);
            }
            if (existing.size() >= contract.getTotalLession()) break;
        }
        if (!created.isEmpty()) lessonRepository.saveAll(created);
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            installmentService.ensureForActiveContract(contract);
            List<Lesson> lessons = lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contract.getId());
            List<Lesson> activated = lessons.stream()
                    .filter(lesson -> PENDING_LESSON_NOTE.equals(lesson.getNote()))
                    .toList();
            activated.forEach(lesson -> lesson.setNote(null));
            if (!activated.isEmpty()) lessonRepository.saveAll(activated);
            for (Lesson lesson : lessons) {
                // V40 links lessons directly to their installment. Do not remap an
                // already linked lesson by its current calendar date; it may have
                // been rescheduled after the original payment period was settled.
                if (lesson.getPaymentInstallment() == null
                        && installmentRepository.findByLesson_Id(lesson.getId()).isEmpty()) {
                    installmentService.assignNextInstallment(contract, lesson);
                }
            }
        }
    }

    /** Validates the lessons that would be generated before a new contract is persisted. */
    public void validateScheduleConflicts(Contract contract) {
        validateScheduleConflicts(contract, true);
    }

    public void validateScheduleConflicts(Contract contract, boolean creatorIsTutor) {
        List<String> messages = findScheduleConflictMessages(contract, creatorIsTutor);
        if (!messages.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "LESSON_SCHEDULE_CONFLICT", messages.get(0));
        }
    }

    public List<String> findScheduleConflictMessages(Contract contract, boolean creatorIsTutor) {
        List<String> messages = new ArrayList<>();
        if (contract == null || contract.getStartDate() == null || contract.getEndDate() == null
                || contract.getStudent() == null || contract.getTutor() == null
                || contract.getTotalLession() == null || contract.getTotalLession() <= 0) {
            return messages;
        }
        List<ScheduleSlot> slots = scheduleSlots(contract);
        int generated = 0;
        for (LocalDate date = contract.getStartDate(); !date.isAfter(contract.getEndDate()); date = date.plusDays(1)) {
            for (ScheduleSlot slot : slots) {
                if (slot.dayOfWeek() != date.getDayOfWeek().getValue()) continue;
                if (generated >= contract.getTotalLession()) return messages;
                List<Lesson> conflicts = lessonRepository.findScheduleConflicts(
                        contract.getStudent().getUser().getId(), contract.getTutor().getUser().getId(),
                        date, slot.startTime(), slot.endTime(), null);
                if (conflicts != null && !conflicts.isEmpty()) {
                    for (Lesson conflict : conflicts) {
                        if (conflict.getContract() != null
                                && conflict.getContract().getId() != null
                                && conflict.getContract().getId().equals(contract.getId())) {
                            continue;
                        }
                        String message = conflictMessage(contract, conflict, creatorIsTutor);
                        if (!messages.contains(message)) messages.add(message);
                    }
                }
                generated++;
            }
        }
        return messages;
    }

    private String conflictMessage(Contract contract, Lesson conflict, boolean creatorIsTutor) {
        String title = conflict.getTitle() == null || conflict.getTitle().isBlank()
                ? "Buổi học" : conflict.getTitle();
        String schedule = conflict.getDate().format(DISPLAY_DATE) + " " + conflict.getStartTime() + " - " + conflict.getEndTime();
        boolean studentConflict = conflict.getContract() != null && conflict.getContract().getStudent() != null
                && conflict.getContract().getStudent().getUser() != null
                && conflict.getContract().getStudent().getUser().getId().equals(contract.getStudent().getUser().getId());
        boolean tutorConflict = conflict.getContract() != null && conflict.getContract().getTutor() != null
                && conflict.getContract().getTutor().getId().equals(contract.getTutor().getId());
        // When both participants are the same as the proposed contract, do not
        // disclose the existing lesson details; it is shared schedule data.
        boolean creatorConflict = creatorIsTutor ? tutorConflict && !studentConflict : studentConflict && !tutorConflict;
        if (creatorConflict) {
            String owner = creatorIsTutor ? "Gia sư" : "Học viên";
            return owner + " đang có buổi học \"" + title + "\" trùng lịch (" + schedule
                    + "). Vui lòng điều chỉnh lịch trước khi tạo hợp đồng.";
        }
        String counterpart = creatorIsTutor ? "học viên" : "gia sư";
        return "Lịch của " + counterpart + " bị trùng vào ngày/giờ " + schedule
                + ". Vui lòng chọn khung giờ khác.";
    }

    private List<ScheduleSlot> scheduleSlots(Contract contract) {
        if (contract.getSchedules() != null && !contract.getSchedules().isEmpty()) {
            return contract.getSchedules().stream()
                    .filter(value -> value.getDayOfWeek() != null && value.getStartTime() != null && value.getEndTime() != null)
                    .map(value -> new ScheduleSlot(value.getDayOfWeek(), value.getStartTime(), value.getEndTime()))
                    .toList();
        }
        String preferred = contract.getPreferredSchedule();
        if (preferred == null || preferred.isBlank()) return List.of();
        String normalized = Normalizer.normalize(preferred.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        Matcher dayMatcher = LEGACY_DAY.matcher(normalized);
        List<LegacyDay> days = new ArrayList<>();
        while (dayMatcher.find()) {
            int weekday = weekdayNumber(dayMatcher.group());
            if (weekday > 0) days.add(new LegacyDay(dayMatcher.start(), weekday));
        }
        Matcher timeMatcher = LEGACY_TIME.matcher(normalized);
        List<ScheduleSlot> slots = new ArrayList<>();
        int previousTimeEnd = 0;
        while (timeMatcher.find()) {
            LocalTime start;
            LocalTime end;
            try {
                start = parseTime(timeMatcher.group(1));
                end = parseTime(timeMatcher.group(2));
            } catch (RuntimeException invalidTime) {
                previousTimeEnd = timeMatcher.end();
                continue;
            }
            if (end.isAfter(start)) {
                for (LegacyDay day : days) {
                    if (day.position() >= previousTimeEnd && day.position() < timeMatcher.start()) {
                        slots.add(new ScheduleSlot(day.dayOfWeek(), start, end));
                    }
                }
            }
            previousTimeEnd = timeMatcher.end();
        }
        return slots;
    }

    private int weekdayNumber(String raw) {
        String value = raw.replaceAll("\\s+", " ").trim();
        if (value.matches("(?:thu\\s*|t)[2-7]")) return Character.digit(value.charAt(value.length() - 1), 10) - 1;
        return switch (value) {
            case "thu hai", "monday" -> 1;
            case "thu ba", "tuesday" -> 2;
            case "thu tu", "wednesday" -> 3;
            case "thu nam", "thursday" -> 4;
            case "thu sau", "friday" -> 5;
            case "thu bay", "saturday" -> 6;
            case "chu nhat", "cn", "sunday" -> 7;
            default -> 0;
        };
    }

    private LocalTime parseTime(String value) {
        String[] parts = value.split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private record ScheduleSlot(int dayOfWeek, LocalTime startTime, LocalTime endTime) {}

    private record LegacyDay(int position, int dayOfWeek) {}

    private String key(LocalDate date, String start, String end) { return date + "|" + start + "|" + end; }

    private boolean overlaps(
            LocalDate leftDate, LocalTime leftStart, LocalTime leftEnd,
            LocalDate rightDate, LocalTime rightStart, LocalTime rightEnd) {
        return leftDate.equals(rightDate)
                && leftStart.isBefore(rightEnd)
                && leftEnd.isAfter(rightStart);
    }
}
