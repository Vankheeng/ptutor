package com.ptutor.backend.tutor.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.tutor.dto.TutorStudentRequestRequest;
import com.ptutor.backend.tutor.dto.TutorStudentRequestResponse;
import com.ptutor.backend.tutor.repository.TutorStudentRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorStudentRequestService {

    private final TutorStudentRequestRepository tutorStudentRequestRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final TutorRepository tutorRepository;
    private final GradeRepository gradeRepository;

    @Transactional
    public TutorStudentRequestResponse create(UUID userId, TutorStudentRequestRequest request) {
        Tutor tutor = findTutorByUserId(userId);
        StudyingRequest studyingRequest = studyingRequestRepository.findById(request.studyingRequestId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "STUDYING_REQUEST_NOT_FOUND",
                        "Studying request not found: " + request.studyingRequestId()));
        if (studyingRequest.getStatus() != RequestStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STUDYING_REQUEST_NOT_OPEN",
                    "Only an OPEN studying request can receive tutor proposals");
        }

        Grade grade = gradeRepository.findById(request.gradeId())
                .filter(candidate -> candidate.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_GRADE",
                        "Grade not found or inactive: " + request.gradeId()));
        if (studyingRequest.getGrade() == null
                || !request.gradeId().equals(studyingRequest.getGrade().getId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GRADE_MISMATCH",
                    "The proposal grade must match the studying request grade");
        }
        if (tutorStudentRequestRepository.existsByTutor_IdAndStudyingRequest_IdAndStatusNot(
                tutor.getId(), request.studyingRequestId(), ApplicationStatus.CANCELLED)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TUTOR_PROPOSAL_ALREADY_EXISTS",
                    "A tutor proposal already exists for this studying request");
        }

        TutorStudentRequest proposal = TutorStudentRequest.builder()
                .tutor(tutor)
                .studyingRequest(studyingRequest)
                .grade(grade)
                .proposedPrice(request.proposedPrice())
                .teachingMode(request.teachingMode())
                .preferredSchedule(normalize(request.preferredSchedule()))
                .message(normalize(request.message()))
                .status(ApplicationStatus.PENDING)
                .build();

        return toResponse(tutorStudentRequestRepository.saveAndFlush(proposal));
    }

    @Transactional(readOnly = true)
    public List<TutorStudentRequestResponse> findMine(UUID userId, ApplicationStatus status) {
        Tutor tutor = findTutorByUserId(userId);
        List<TutorStudentRequest> proposals = status == null
                ? tutorStudentRequestRepository.findAllByTutor_IdOrderByCreatedAtDesc(tutor.getId())
                : tutorStudentRequestRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(
                        tutor.getId(), status);
        return proposals.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TutorStudentRequestResponse findMineById(UUID userId, UUID proposalId) {
        Tutor tutor = findTutorByUserId(userId);
        return toResponse(tutorStudentRequestRepository.findByIdAndTutor_Id(proposalId, tutor.getId())
                .orElseThrow(() -> proposalNotFound(proposalId)));
    }

    @Transactional
    public TutorStudentRequestResponse cancel(UUID userId, UUID proposalId) {
        Tutor tutor = findTutorByUserId(userId);
        TutorStudentRequest proposal = tutorStudentRequestRepository.findByIdAndTutor_Id(proposalId, tutor.getId())
                .orElseThrow(() -> proposalNotFound(proposalId));
        if (proposal.getStatus() != ApplicationStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_TUTOR_PROPOSAL_STATUS_TRANSITION",
                    "Only PENDING tutor proposals can be cancelled");
        }

        proposal.setStatus(ApplicationStatus.CANCELLED);
        return toResponse(tutorStudentRequestRepository.saveAndFlush(proposal));
    }

    private TutorStudentRequestResponse toResponse(TutorStudentRequest proposal) {
        return TutorStudentRequestResponse.from(proposal);
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage tutor proposals"));
    }

    private ApiException proposalNotFound(UUID proposalId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "TUTOR_PROPOSAL_NOT_FOUND",
                "Tutor proposal not found: " + proposalId);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
