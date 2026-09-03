package com.ptutor.backend.studenttutorrequest.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.studenttutorrequest.dto.StudentTutorRequestCreateRequest;
import com.ptutor.backend.studenttutorrequest.dto.StudentTutorRequestResponse;
import com.ptutor.backend.studenttutorrequest.repository.StudentTutorRequestRepository;
import com.ptutor.backend.tutor.repository.GradeTeachingRequestRepository;
import com.ptutor.backend.tutor.repository.TeachingRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentTutorRequestService {

    private final StudentTutorRequestRepository requestRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final TeachingRequestRepository teachingRequestRepository;
    private final GradeRepository gradeRepository;
    private final GradeTeachingRequestRepository gradeAssociationRepository;

    @Transactional
    public StudentTutorRequestResponse create(UUID userId, StudentTutorRequestCreateRequest source) {
        Student student = findStudentByUserId(userId);
        TeachingRequest teachingRequest = teachingRequestRepository.findById(source.teachingRequestId())
                .orElseThrow(() -> teachingRequestNotFound(source.teachingRequestId()));

        if (teachingRequest.getStatus() != RequestStatus.OPEN) {
            throw badRequest("TEACHING_REQUEST_NOT_OPEN", "Only an open teaching request can receive applications");
        }
        if (teachingRequest.getTeachingMode() != TeachingMode.valueOf(source.learningMode().name())) {
            throw badRequest("LEARNING_MODE_MISMATCH",
                    "The application learning mode must match the teaching request mode");
        }
        if (teachingRequest.getTutor().getUser().getId().equals(userId)) {
            throw badRequest("CANNOT_APPLY_TO_OWN_REQUEST", "A tutor cannot apply to their own teaching request");
        }

        Grade grade = gradeRepository.findById(source.gradeId())
                .filter(item -> item.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> badRequest("INVALID_GRADE", "Grade not found or inactive"));
        if (!gradeAssociationRepository.existsByTeachingRequest_IdAndGrade_Id(
                teachingRequest.getId(), grade.getId())) {
            throw badRequest("GRADE_NOT_IN_TEACHING_REQUEST", "The selected grade is not offered in this teaching request");
        }

        requestRepository.findByStudent_IdAndTeachingRequest_Id(student.getId(), teachingRequest.getId())
                .filter(existing -> existing.getStatus() != ApplicationStatus.CANCELLED)
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
                            "You already have an active application for this teaching request");
                });

        StudentTutorRequest request = StudentTutorRequest.builder()
                .student(student)
                .grade(grade)
                .teachingRequest(teachingRequest)
                .proposedPrice(source.proposedPrice())
                .learningMode(source.learningMode())
                .preferredSchedule(normalize(source.preferredSchedule()))
                .message(normalize(source.message()))
                .status(ApplicationStatus.PENDING)
                .build();
        return toResponse(requestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public List<StudentTutorRequestResponse> findMine(UUID userId, ApplicationStatus status) {
        Student student = findStudentByUserId(userId);
        List<StudentTutorRequest> requests = status == null
                ? requestRepository.findAllByStudent_IdOrderByCreatedAtDesc(student.getId())
                : requestRepository.findAllByStudent_IdAndStatusOrderByCreatedAtDesc(student.getId(), status);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StudentTutorRequestResponse findMineById(UUID userId, UUID requestId) {
        Student student = findStudentByUserId(userId);
        return toResponse(requestRepository.findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> applicationNotFound(requestId)));
    }

    @Transactional
    public StudentTutorRequestResponse cancel(UUID userId, UUID requestId) {
        Student student = findStudentByUserId(userId);
        StudentTutorRequest request = requestRepository.findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> applicationNotFound(requestId));
        if (request.getStatus() != ApplicationStatus.PENDING
                && request.getStatus() != ApplicationStatus.ACCEPTED) {
            throw invalidTransition("Only PENDING or ACCEPTED applications can be cancelled");
        }
        request.setStatus(ApplicationStatus.CANCELLED);
        return toResponse(requestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public List<StudentTutorRequestResponse> findIncoming(UUID userId, ApplicationStatus status) {
        Tutor tutor = findTutorByUserId(userId);
        List<StudentTutorRequest> requests = status == null
                ? requestRepository.findAllByTeachingRequest_Tutor_IdOrderByCreatedAtDesc(tutor.getId())
                : requestRepository.findAllByTeachingRequest_Tutor_IdAndStatusOrderByCreatedAtDesc(tutor.getId(), status);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StudentTutorRequestResponse findIncomingById(UUID userId, UUID requestId) {
        Tutor tutor = findTutorByUserId(userId);
        return toResponse(requestRepository.findByIdAndTeachingRequest_Tutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> applicationNotFound(requestId)));
    }

    @Transactional
    public StudentTutorRequestResponse updateStatus(UUID userId, UUID requestId, ApplicationStatus targetStatus) {
        Tutor tutor = findTutorByUserId(userId);
        StudentTutorRequest request = requestRepository.findByIdAndTeachingRequest_Tutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> applicationNotFound(requestId));
        if (targetStatus != ApplicationStatus.ACCEPTED && targetStatus != ApplicationStatus.REJECTED) {
            throw badRequest("INVALID_APPLICATION_STATUS", "Tutor can only accept or reject an application");
        }
        if (request.getStatus() != ApplicationStatus.PENDING) {
            throw invalidTransition("Only PENDING applications can be accepted or rejected");
        }

        request.setStatus(targetStatus);
        if (targetStatus == ApplicationStatus.ACCEPTED) {
            request.getTeachingRequest().setStatus(RequestStatus.MATCHED);
            teachingRequestRepository.save(request.getTeachingRequest());
        }
        return toResponse(requestRepository.saveAndFlush(request));
    }

    private StudentTutorRequestResponse toResponse(StudentTutorRequest request) {
        return StudentTutorRequestResponse.from(request);
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED",
                        "Only a student can manage student-tutor applications"));
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage student-tutor applications"));
    }

    private ApiException teachingRequestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TEACHING_REQUEST_NOT_FOUND",
                "Teaching request not found: " + requestId);
    }

    private ApiException applicationNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_TUTOR_REQUEST_NOT_FOUND",
                "Student-tutor request not found: " + requestId);
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_APPLICATION_STATUS_TRANSITION", message);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
