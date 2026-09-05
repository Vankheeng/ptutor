package com.ptutor.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.StudentTutorRequestResponse;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudentTutorRequestMapper;
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
