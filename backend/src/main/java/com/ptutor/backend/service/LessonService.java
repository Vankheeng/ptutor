package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.LessonRequest;
import com.ptutor.backend.dto.response.LessonResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.LessonMapper;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LessonService {

    private static final List<ComplaintStatus> BLOCKING_COMPLAINT_STATUSES =
            List.of(ComplaintStatus.PENDING, ComplaintStatus.IN_REVIEW);

    private final LessonRepository lessonRepository;
    private final ContractRepository contractRepository;
    private final TutorRepository tutorRepository;
    private final StudentRepository studentRepository;
    private final ComplaintRepository complaintRepository;
    private final LessonMapper lessonMapper;
    private final Clock clock;

    @Transactional
    public LessonResponse create(UUID userId, UUID contractId, LessonRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        Contract contract = findOwnedContract(contractId, tutor.getId());
        validateActiveContract(contract);
        validateSchedule(contract, source);

        Lesson lesson = Lesson.builder()
                .contract(contract)
                .title(normalize(source.title()))
                .date(source.date())
                .startTime(source.startTime())
                .endTime(source.endTime())
                .teachingMode(contract.getTeachingMode())
                .note(normalize(source.note()))
                .status(initialStatus(source.date(), source.endTime()))
                .build();
        return lessonMapper.toResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> findByContract(
            UUID userId, UUID contractId, LessonStatus status, Pageable pageable) {
        Tutor tutor = findTutorByUserId(userId);
        findOwnedContract(contractId, tutor.getId());
        Page<Lesson> lessons = status == null
                ? lessonRepository.findAllByContract_Id(contractId, pageable)
                : lessonRepository.findAllByContract_IdAndStatus(contractId, status, pageable);
        return PageResponse.from(lessons, lessons.getContent().stream().map(lessonMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public LessonResponse findById(UUID userId, UUID lessonId) {
        Tutor tutor = findTutorByUserId(userId);
        return lessonMapper.toResponse(findOwnedLesson(lessonId, tutor.getId()));
    }

    @Transactional
    public LessonResponse update(UUID userId, UUID lessonId, LessonRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        Lesson lesson = findOwnedLesson(lessonId, tutor.getId());
        Contract contract = lesson.getContract();
        validateActiveContract(contract);
        ensureScheduled(lesson, "updated");
        validateSchedule(contract, source);

        lesson.setTitle(normalize(source.title()));
        lesson.setDate(source.date());
        lesson.setStartTime(source.startTime());
        lesson.setEndTime(source.endTime());
        lesson.setNote(normalize(source.note()));
        return lessonMapper.toResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Transactional
    public LessonResponse updateStatus(UUID userId, UUID lessonId, LessonRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        Lesson lesson = findOwnedLesson(lessonId, tutor.getId());
        LessonStatus targetStatus = require(source.status(), "LESSON_STATUS_REQUIRED", "Status is required");

        if (targetStatus == LessonStatus.PENDING_CONFIRMATION) {
            ensureScheduled(lesson, "marked as taught");
            LocalDateTime lessonEnd = LocalDateTime.of(lesson.getDate(), lesson.getEndTime());
            if (lessonEnd.isAfter(LocalDateTime.now(clock))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_NOT_FINISHED",
                        "A lesson can only be marked as taught after it ends");
            }
        } else if (targetStatus == LessonStatus.CANCELLED) {
            if (lesson.getStatus() != LessonStatus.SCHEDULED
                    && lesson.getStatus() != LessonStatus.PENDING_CONFIRMATION) {
                throw invalidStatusTransition("Only SCHEDULED or PENDING_CONFIRMATION lessons can be cancelled");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_STATUS",
                    "Tutor can only mark a lesson as taught or cancel it");
        }

        lesson.setStatus(targetStatus);
        return lessonMapper.toResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Transactional
    public LessonResponse confirmByStudent(UUID userId, UUID lessonId) {
        Student student = findStudentByUserId(userId);
        Lesson lesson = findStudentLesson(lessonId, student.getId());
        ensurePendingConfirmation(lesson, "confirmed");
        ensureNoBlockingComplaint(lesson.getContract());

        lesson.setStatus(LessonStatus.CONFIRMED);
        return lessonMapper.toResponse(lessonRepository.saveAndFlush(lesson));
    }

    @Transactional
    public int completeExpiredPendingLessons(int graceDays) {
        LocalDateTime deadline = LocalDateTime.now(clock).minusDays(graceDays);
        List<Lesson> candidates = lessonRepository.findAllFinishedBefore(
                LessonStatus.PENDING_CONFIRMATION, deadline.toLocalDate(), deadline.toLocalTime());
        List<Lesson> completedLessons = candidates.stream()
                .filter(lesson -> !hasBlockingComplaint(lesson.getContract()))
                .toList();
        if (completedLessons.isEmpty()) {
            return 0;
        }
        completedLessons.forEach(lesson -> lesson.setStatus(LessonStatus.COMPLETED));
        lessonRepository.saveAll(completedLessons);
        return completedLessons.size();
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage lessons"));
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED",
                        "Only a student can confirm lessons"));
    }

    private Contract findOwnedContract(UUID contractId, UUID tutorId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> contractNotFound(contractId));
        if (contract.getTutor() == null || !tutorId.equals(contract.getTutor().getId())) {
            throw contractNotFound(contractId);
        }
        return contract;
    }

    private Lesson findOwnedLesson(UUID lessonId, UUID tutorId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> lessonNotFound(lessonId));
        Contract contract = lesson.getContract();
        if (contract == null || contract.getTutor() == null || !tutorId.equals(contract.getTutor().getId())) {
            throw lessonNotFound(lessonId);
        }
        return lesson;
    }

    private Lesson findStudentLesson(UUID lessonId, UUID studentId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> lessonNotFound(lessonId));
        Contract contract = lesson.getContract();
        if (contract == null || contract.getStudent() == null || !studentId.equals(contract.getStudent().getId())) {
            throw lessonNotFound(lessonId);
        }
        return lesson;
    }

    private void validateActiveContract(Contract contract) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_NOT_ACTIVE",
                    "Lessons can only be managed for an active contract");
        }
        if (contract.getTeachingMode() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_MODE_REQUIRED",
                    "Contract teaching mode is required");
        }
    }

    private void validateSchedule(Contract contract, LessonRequest source) {
        LocalDate date = require(source.date(), "LESSON_DATE_REQUIRED", "Date is required");
        LocalTime startTime = require(source.startTime(), "LESSON_START_TIME_REQUIRED", "Start time is required");
        LocalTime endTime = require(source.endTime(), "LESSON_END_TIME_REQUIRED", "End time is required");
        if (!startTime.isBefore(endTime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_TIME",
                    "Start time must be before end time");
        }
        if ((contract.getStartDate() != null && date.isBefore(contract.getStartDate()))
                || (contract.getEndDate() != null && date.isAfter(contract.getEndDate()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_OUTSIDE_CONTRACT_PERIOD",
                    "Lesson date must be within the contract period");
        }
    }

    private void ensureScheduled(Lesson lesson, String action) {
        if (lesson.getStatus() != LessonStatus.SCHEDULED) {
            throw invalidStatusTransition("Only SCHEDULED lessons can be " + action);
        }
    }

    private void ensurePendingConfirmation(Lesson lesson, String action) {
        if (lesson.getStatus() != LessonStatus.PENDING_CONFIRMATION) {
            throw invalidStatusTransition("Only PENDING_CONFIRMATION lessons can be " + action);
        }
    }

    private void ensureNoBlockingComplaint(Contract contract) {
        if (hasBlockingComplaint(contract)) {
            throw new ApiException(HttpStatus.CONFLICT, "LESSON_COMPLETION_ON_HOLD",
                    "Lesson completion is on hold while a complaint is being processed");
        }
    }

    private boolean hasBlockingComplaint(Contract contract) {
        return complaintRepository.existsByContract_IdAndStatusIn(contract.getId(), BLOCKING_COMPLAINT_STATUSES);
    }

    private LessonStatus initialStatus(LocalDate date, LocalTime endTime) {
        LocalDateTime lessonEnd = LocalDateTime.of(date, endTime);
        return lessonEnd.isAfter(LocalDateTime.now(clock))
                ? LessonStatus.SCHEDULED
                : LessonStatus.PENDING_CONFIRMATION;
    }

    private ApiException contractNotFound(UUID contractId) {
        return new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found: " + contractId);
    }

    private ApiException lessonNotFound(UUID lessonId) {
        return new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found: " + lessonId);
    }

    private ApiException invalidStatusTransition(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_LESSON_STATUS_TRANSITION", message);
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }

    private <T> T require(T value, String code, String message) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value;
    }
}
