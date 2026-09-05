package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorStudentRequestMapper;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorStudentRequestService {

    private final TutorStudentRequestRepository tutorStudentRequestRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final StudentRepository studentRepository;
    private final TutorStudentRequestMapper tutorStudentRequestMapper;

    @Transactional(readOnly = true)
    public PageResponse<TutorStudentRequestResponse> findMine(
            UUID userId, UUID studyingRequestId, ApplicationStatus status, Pageable pageable) {
        Student student = findStudentByUserId(userId);
        ensureStudyingRequestBelongsToStudent(studyingRequestId, student.getId());

        Page<TutorStudentRequest> requests = status == null
                ? tutorStudentRequestRepository
                        .findAllByStudyingRequest_IdOrderByCreatedAtDesc(studyingRequestId, pageable)
                : tutorStudentRequestRepository
                        .findAllByStudyingRequest_IdAndStatusOrderByCreatedAtDesc(
                                studyingRequestId, status, pageable);

        return PageResponse.from(
                requests,
                requests.getContent().stream().map(tutorStudentRequestMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public TutorStudentRequestResponse findMineById(
            UUID userId, UUID studyingRequestId, UUID tutorRequestId) {
        Student student = findStudentByUserId(userId);
        ensureStudyingRequestBelongsToStudent(studyingRequestId, student.getId());
        return tutorStudentRequestMapper.toResponse(findRequest(tutorRequestId, studyingRequestId));
    }

    @Transactional
    public TutorStudentRequestResponse accept(
            UUID userId, UUID studyingRequestId, UUID tutorRequestId) {
        Student student = findStudentByUserId(userId);
        StudyingRequest studyingRequest = studyingRequestRepository
                .findByIdAndStudentIdForUpdate(studyingRequestId, student.getId())
                .orElseThrow(() -> studyingRequestNotFound(studyingRequestId));

        if (studyingRequest.getStatus() != RequestStatus.OPEN) {
            throw invalidTransition("Only OPEN studying requests can accept tutor requests");
        }

        TutorStudentRequest tutorRequest = findRequest(tutorRequestId, studyingRequestId);
        ensurePending(tutorRequest);

        long acceptedCount = tutorStudentRequestRepository.countByStudyingRequest_IdAndStatus(
                studyingRequestId, ApplicationStatus.ACCEPTED);
        Integer quantity = studyingRequest.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STUDYING_REQUEST_QUANTITY",
                    "Studying request quantity is invalid");
        }
        if (acceptedCount >= quantity) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STUDYING_REQUEST_QUANTITY_REACHED",
                    "Studying request has already reached its tutor quantity");
        }

        tutorRequest.setStatus(ApplicationStatus.ACCEPTED);
        TutorStudentRequest savedRequest = tutorStudentRequestRepository.saveAndFlush(tutorRequest);
        if (acceptedCount + 1 >= quantity) {
            studyingRequest.setStatus(RequestStatus.MATCHED);
            studyingRequestRepository.saveAndFlush(studyingRequest);
        }
        return tutorStudentRequestMapper.toResponse(savedRequest);
    }

    @Transactional
    public TutorStudentRequestResponse reject(
            UUID userId, UUID studyingRequestId, UUID tutorRequestId) {
        Student student = findStudentByUserId(userId);
        ensureStudyingRequestBelongsToStudent(studyingRequestId, student.getId());

        TutorStudentRequest tutorRequest = findRequest(tutorRequestId, studyingRequestId);
        ensurePending(tutorRequest);
        tutorRequest.setStatus(ApplicationStatus.REJECTED);
        return tutorStudentRequestMapper.toResponse(
                tutorStudentRequestRepository.saveAndFlush(tutorRequest));
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "STUDENT_PROFILE_REQUIRED",
                        "Only a student with a profile can manage tutor requests"));
    }

    private void ensureStudyingRequestBelongsToStudent(UUID studyingRequestId, UUID studentId) {
        if (!studyingRequestRepository.findByIdAndStudent_Id(studyingRequestId, studentId).isPresent()) {
            throw studyingRequestNotFound(studyingRequestId);
        }
    }

    private TutorStudentRequest findRequest(UUID tutorRequestId, UUID studyingRequestId) {
        return tutorStudentRequestRepository.findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TUTOR_STUDENT_REQUEST_NOT_FOUND",
                        "Tutor student request not found: " + tutorRequestId));
    }

    private void ensurePending(TutorStudentRequest tutorRequest) {
        if (tutorRequest.getStatus() != ApplicationStatus.PENDING) {
            throw invalidTransition("Only PENDING tutor requests can be processed");
        }
    }

    private ApiException studyingRequestNotFound(UUID studyingRequestId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "STUDYING_REQUEST_NOT_FOUND",
                "Studying request not found: " + studyingRequestId);
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION",
                message);
    }
}
