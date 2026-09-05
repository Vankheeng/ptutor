package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.TutorStudentRequestCreateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorStudentRequestMapper;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorStudentRequestService {

    private final TutorStudentRequestRepository tutorStudentRequestRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final TutorRepository tutorRepository;
    private final GradeRepository gradeRepository;
    private final TutorStudentRequestMapper tutorStudentRequestMapper;

    @Transactional
    public TutorStudentRequestResponse create(
            UUID userId, UUID studyingRequestId, TutorStudentRequestCreateRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        StudyingRequest studyingRequest = findOpenStudyingRequest(studyingRequestId);
        Grade grade = gradeRepository.findById(source.gradeId())
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_GRADE", "Grade not found or inactive"));

        if (tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatus(
                tutor.getId(), studyingRequestId, ApplicationStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_TUTOR_STUDENT_REQUEST",
                    "A pending teaching proposal already exists for this studying request");
        }

        TutorStudentRequest request = TutorStudentRequest.builder()
                .tutor(tutor)
                .studyingRequest(studyingRequest)
                .grade(grade)
                .proposedPrice(source.proposedPrice())
                .teachingMode(source.teachingMode())
                .preferredSchedule(normalize(source.preferredSchedule()))
                .message(normalize(source.message()))
                .status(ApplicationStatus.PENDING)
                .build();
        return tutorStudentRequestMapper.toResponse(tutorStudentRequestRepository.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public PageResponse<TutorStudentRequestResponse> findMine(
            UUID userId, ApplicationStatus status, Pageable pageable) {
        Tutor tutor = findTutorByUserId(userId);
        Page<TutorStudentRequest> requests = status == null
                ? tutorStudentRequestRepository.findAllByTutor_IdOrderByCreatedAtDesc(tutor.getId(), pageable)
                : tutorStudentRequestRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                        tutor.getId(), status, pageable);
        return PageResponse.from(requests, requests.getContent().stream()
                .map(tutorStudentRequestMapper::toResponse)
                .toList());
    }

    @Transactional
    public TutorStudentRequestResponse cancel(UUID userId, UUID requestId) {
        Tutor tutor = findTutorByUserId(userId);
        TutorStudentRequest request = tutorStudentRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> requestNotFound(requestId));
        if (request.getStatus() != ApplicationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION",
                    "Only PENDING teaching proposals can be cancelled");
        }

        request.setStatus(ApplicationStatus.CANCELLED);
        return tutorStudentRequestMapper.toResponse(tutorStudentRequestRepository.saveAndFlush(request));
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage teaching proposals"));
    }

    private StudyingRequest findOpenStudyingRequest(UUID studyingRequestId) {
        StudyingRequest request = studyingRequestRepository.findById(studyingRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDYING_REQUEST_NOT_FOUND",
                        "Studying request not found: " + studyingRequestId));
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDYING_REQUEST_NOT_OPEN",
                    "Teaching proposals can only be sent to OPEN studying requests");
        }
        return request;
    }

    private ApiException requestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_STUDENT_REQUEST_NOT_FOUND",
                "Tutor student request not found: " + requestId);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
