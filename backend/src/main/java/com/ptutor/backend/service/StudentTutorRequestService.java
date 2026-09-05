package com.ptutor.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.StudentTutorRequestCreateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudentTutorRequestMineResponse;
import com.ptutor.backend.dto.response.StudentTutorRequestResponse;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudentTutorRequestMapper;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.GradeTeachingRequestRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TeachingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentTutorRequestService {

    private final StudentTutorRequestRepository studentTutorRequestRepository;
    private final TeachingRequestRepository teachingRequestRepository;
    private final TutorRepository tutorRepository;
    private final StudentTutorRequestMapper studentTutorRequestMapper;
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final GradeTeachingRequestRepository gradeTeachingRequestRepository;

    @Transactional(readOnly = true)
    public List<StudentTutorRequestResponse> findMine(
            UUID userId, UUID teachingRequestId, ApplicationStatus status) {
        Tutor tutor = findTutorByUserId(userId);
        ensureTeachingRequestBelongsToTutor(teachingRequestId, tutor.getId());

        List<StudentTutorRequest> requests = status == null
                ? studentTutorRequestRepository
                        .findAllByTeachingRequest_IdOrderByCreatedAtDesc(teachingRequestId)
                : studentTutorRequestRepository
                        .findAllByTeachingRequest_IdAndStatusOrderByCreatedAtDesc(teachingRequestId, status);
        return requests.stream().map(studentTutorRequestMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StudentTutorRequestResponse findMineById(
        UUID userId, UUID teachingRequestId, UUID requestId) {
        Tutor tutor = findTutorByUserId(userId);
        ensureTeachingRequestBelongsToTutor(teachingRequestId, tutor.getId());
        return studentTutorRequestMapper.toResponse(findRequest(requestId, teachingRequestId));
    }

    @Transactional
    public StudentTutorRequestResponse updateStatus(
            UUID userId, UUID teachingRequestId, UUID requestId, ApplicationStatus targetStatus) {
        Tutor tutor = findTutorByUserId(userId);
        ensureTeachingRequestBelongsToTutor(teachingRequestId, tutor.getId());
        StudentTutorRequest request = findRequest(requestId, teachingRequestId);

        if (targetStatus != ApplicationStatus.ACCEPTED && targetStatus != ApplicationStatus.REJECTED) {
            throw invalidTransition("Only ACCEPTED or REJECTED status is allowed");
        }
        if (request.getStatus() != ApplicationStatus.PENDING) {
            throw invalidTransition("Only PENDING applications can be accepted or rejected");
        }

        TeachingRequest teachingRequest = request.getTeachingRequest();
        if (targetStatus == ApplicationStatus.ACCEPTED) {
            if (teachingRequest.getStatus() != RequestStatus.OPEN) {
                throw invalidTransition("Only OPEN teaching requests can accept student applications");
            }
            request.setStatus(ApplicationStatus.ACCEPTED);
            StudentTutorRequest savedRequest = studentTutorRequestRepository.saveAndFlush(request);
            long acceptedCount = studentTutorRequestRepository
                    .countByTeachingRequest_IdAndStatus(teachingRequestId, ApplicationStatus.ACCEPTED);
            if (acceptedCount >= teachingRequest.getQuantity()) {
                teachingRequest.setStatus(RequestStatus.MATCHED);
                teachingRequestRepository.saveAndFlush(teachingRequest);
            }
            return studentTutorRequestMapper.toResponse(savedRequest);
        } else {
            request.setStatus(ApplicationStatus.REJECTED);
        }

        return studentTutorRequestMapper.toResponse(studentTutorRequestRepository.saveAndFlush(request));
    }

    @Transactional
    public StudentTutorRequestMineResponse createForStudent(
            UUID userId, UUID teachingRequestId, StudentTutorRequestCreateRequest source) {
        Student student = findStudentByUserId(userId);
        TeachingRequest teachingRequest = teachingRequestRepository.findById(teachingRequestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TEACHING_REQUEST_NOT_FOUND",
                        "Teaching request not found: " + teachingRequestId));

        if (teachingRequest.getStatus() != RequestStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_TEACHING_REQUEST_STATUS",
                    "Only OPEN teaching requests can receive student applications");
        }

        Grade grade = gradeRepository.findById(source.gradeId())
                .filter(candidate -> candidate.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_GRADE",
                        "Grade is not available: " + source.gradeId()));

        if (!gradeTeachingRequestRepository.existsByTeachingRequest_IdAndGrade_Id(
                teachingRequestId, source.gradeId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_GRADE",
                    "Grade is not associated with the teaching request");
        }

        if (studentTutorRequestRepository.existsByStudent_IdAndTeachingRequest_IdAndStatus(
                student.getId(), teachingRequestId, ApplicationStatus.PENDING)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_STUDENT_TUTOR_REQUEST",
                    "A pending student tutor request already exists");
        }

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

        return studentTutorRequestMapper.toMineResponse(
                studentTutorRequestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentTutorRequestMineResponse> findMineForStudent(
            UUID userId, ApplicationStatus status, Pageable pageable) {
        Student student = findStudentByUserId(userId);
        Page<StudentTutorRequest> requests = status == null
                ? studentTutorRequestRepository.findAllByStudent_IdOrderByCreatedAtDesc(student.getId(), pageable)
                : studentTutorRequestRepository.findAllByStudent_IdAndStatusOrderByCreatedAtDesc(
                        student.getId(), status, pageable);

        List<StudentTutorRequestMineResponse> responses = requests.getContent().stream()
                .map(studentTutorRequestMapper::toMineResponse)
                .toList();
        return PageResponse.from(requests, responses);
    }

    @Transactional
    public StudentTutorRequestMineResponse cancelForStudent(
            UUID userId, UUID requestId, ApplicationStatus targetStatus) {
        if (targetStatus != ApplicationStatus.CANCELLED) {
            throw invalidStudentTransition("Students can only cancel their own requests");
        }

        Student student = findStudentByUserId(userId);
        StudentTutorRequest request = studentTutorRequestRepository
                .findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "STUDENT_TUTOR_REQUEST_NOT_FOUND",
                        "Student tutor request not found: " + requestId));

        if (request.getStatus() != ApplicationStatus.PENDING) {
            throw invalidStudentTransition("Only PENDING requests can be cancelled");
        }

        request.setStatus(ApplicationStatus.CANCELLED);
        return studentTutorRequestMapper.toMineResponse(
                studentTutorRequestRepository.saveAndFlush(request));
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "STUDENT_PROFILE_REQUIRED",
                        "Only a student can manage student tutor requests"));
    }

    private ApiException invalidStudentTransition(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION",
                message);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private StudentTutorRequest findRequest(UUID requestId, UUID teachingRequestId) {
        return studentTutorRequestRepository
                .findByIdAndTeachingRequest_Id(requestId, teachingRequestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "STUDENT_TUTOR_REQUEST_NOT_FOUND",
                        "Student tutor request not found: " + requestId));
    }

    private void ensureTeachingRequestBelongsToTutor(UUID teachingRequestId, UUID tutorId) {
        teachingRequestRepository.findByIdAndTutor_Id(teachingRequestId, tutorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TEACHING_REQUEST_NOT_FOUND",
                        "Teaching request not found: " + teachingRequestId));
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage student applications"));
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION",
                message);
    }
}
