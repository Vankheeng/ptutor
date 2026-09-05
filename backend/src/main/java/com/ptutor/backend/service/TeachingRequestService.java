package com.ptutor.backend.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.GradeTeachingRequest;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.TeachingRequestAvailability;
import com.ptutor.backend.entity.TeachingRequestDistrict;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TeachingRequestMapper;
import com.ptutor.backend.mapper.TeachingRequestMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.dto.request.TeachingRequestRequest;
import com.ptutor.backend.dto.response.TeachingRequestResponse;
import com.ptutor.backend.repository.TeachingRequestRepository;
import com.ptutor.backend.repository.TeachingRequestStudentRequestCount;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeachingRequestService {

    private final TeachingRequestRepository teachingRequestRepository;
    private final StudentTutorRequestRepository studentTutorRequestRepository;
    private final TeachingRequestMapper teachingRequestMapper;
    private final TutorRepository tutorRepository;
    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final DistrictRepository districtRepository;

    @Transactional
    public TeachingRequestResponse create(UUID userId, TeachingRequestRequest request) {
        Tutor tutor = findTutorByUserId(userId);
        Subject subject = resolveSubject(request);
        String customSubjectName = normalize(request.customSubjectName());

        TeachingRequest teachingRequest = TeachingRequest.builder()
                .tutor(tutor)
                .subject(subject)
                .customSubjectName(subject == null ? customSubjectName : null)
                .title(normalize(request.title()))
                .note(normalize(request.note()))
                .quantity(request.quantity())
                .detailAddress(normalize(request.detailAddress()))
                .expectedPrice(request.expectedPrice())
                .teachingMode(request.teachingMode())
                .preferredSchedule(normalize(request.preferredSchedule()))
                .description(normalize(request.description()))
                .status(RequestStatus.DRAFT)
                .build();

        TeachingRequest saved = teachingRequestRepository.saveAndFlush(teachingRequest);
        replaceAssociations(saved, request);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TeachingRequestResponse> findMine(UUID userId, RequestStatus status) {
        Tutor tutor = findTutorByUserId(userId);
        List<TeachingRequest> requests = status == null
                ? teachingRequestRepository.findAllByTutor_IdOrderByCreatedAtDesc(tutor.getId())
                : teachingRequestRepository.findAllByTutor_IdAndStatusOrderByCreatedAtDesc(tutor.getId(), status);
        return toResponses(requests);
    }

    @Transactional(readOnly = true)
    public TeachingRequestResponse findMineById(UUID userId, UUID requestId) {
        Tutor tutor = findTutorByUserId(userId);
        return toResponse(teachingRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> requestNotFound(requestId)));
    }

    @Transactional
    public TeachingRequestResponse update(UUID userId, UUID requestId, TeachingRequestRequest request) {
        Tutor tutor = findTutorByUserId(userId);
        TeachingRequest teachingRequest = teachingRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> requestNotFound(requestId));
        boolean draft = teachingRequest.getStatus() == RequestStatus.DRAFT;
        if (!draft && teachingRequest.getStatus() != RequestStatus.OPEN
                && teachingRequest.getStatus() != RequestStatus.PENDING_REVIEW) {
            throw invalidTransition("Only DRAFT, OPEN or PENDING_REVIEW requests can be updated");
        }

        Subject subject = resolveSubject(request);
        teachingRequest.setSubject(subject);
        teachingRequest.setCustomSubjectName(subject == null ? normalize(request.customSubjectName()) : null);
        teachingRequest.setTitle(normalize(request.title()));
        teachingRequest.setNote(normalize(request.note()));
        teachingRequest.setQuantity(request.quantity());
        teachingRequest.setDetailAddress(normalize(request.detailAddress()));
        teachingRequest.setExpectedPrice(request.expectedPrice());
        teachingRequest.setTeachingMode(request.teachingMode());
        teachingRequest.setPreferredSchedule(normalize(request.preferredSchedule()));
        teachingRequest.setDescription(normalize(request.description()));
        teachingRequest.setStatus(draft
                ? RequestStatus.DRAFT
                : subject == null ? RequestStatus.PENDING_REVIEW : RequestStatus.OPEN);
        clearReviewMetadata(teachingRequest);

        TeachingRequest saved = teachingRequestRepository.saveAndFlush(teachingRequest);
        replaceAssociations(saved, request);
        return toResponse(saved);
    }

    @Transactional
    public TeachingRequestResponse updateStatus(UUID userId, UUID requestId, RequestStatus targetStatus) {
        Tutor tutor = findTutorByUserId(userId);
        TeachingRequest teachingRequest = teachingRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> requestNotFound(requestId));
        if ((targetStatus != RequestStatus.OPEN && targetStatus != RequestStatus.CLOSED)
                || (teachingRequest.getStatus() != RequestStatus.OPEN
                        && teachingRequest.getStatus() != RequestStatus.CLOSED)) {
            throw invalidTransition("Only OPEN and CLOSED status transitions are allowed");
        }

        teachingRequest.setStatus(targetStatus);
        return toResponse(teachingRequestRepository.saveAndFlush(teachingRequest));
    }

    @Transactional
    public TeachingRequestResponse cancel(UUID userId, UUID requestId) {
        Tutor tutor = findTutorByUserId(userId);
        TeachingRequest teachingRequest = teachingRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> requestNotFound(requestId));
        if (teachingRequest.getStatus() != RequestStatus.DRAFT
                && teachingRequest.getStatus() != RequestStatus.OPEN
                && teachingRequest.getStatus() != RequestStatus.CLOSED
                && teachingRequest.getStatus() != RequestStatus.PENDING_REVIEW) {
            throw invalidTransition("Only DRAFT, OPEN, CLOSED or PENDING_REVIEW requests can be cancelled");
        }

        teachingRequest.setStatus(RequestStatus.CANCELLED);
        return toResponse(teachingRequestRepository.saveAndFlush(teachingRequest));
    }

    /**
     * Activates a draft after the payment flow has confirmed a successful payment.
     * This method is intentionally not exposed as a public API yet.
     */
    @Transactional
    public TeachingRequestResponse activateAfterPayment(UUID requestId) {
        TeachingRequest teachingRequest = teachingRequestRepository.findById(requestId)
                .orElseThrow(() -> requestNotFound(requestId));
        if (teachingRequest.getStatus() != RequestStatus.DRAFT) {
            throw invalidTransition("Only DRAFT requests can be activated after payment");
        }

        teachingRequest.setStatus(teachingRequest.getSubject() == null
                ? RequestStatus.PENDING_REVIEW
                : RequestStatus.OPEN);
        clearReviewMetadata(teachingRequest);
        return toResponse(teachingRequestRepository.saveAndFlush(teachingRequest));
    }

    @Transactional(readOnly = true)
    public List<TeachingRequestResponse> findVisible(UserRole role) {
        List<TeachingRequest> requests = isStaff(role)
                ? teachingRequestRepository.findAllByOrderByCreatedAtDesc()
                : teachingRequestRepository.findAllByStatusOrderByCreatedAtDesc(RequestStatus.OPEN);
        return toResponses(requests);
    }

    @Transactional(readOnly = true)
    public TeachingRequestResponse findVisibleById(UUID requestId, UserRole role) {
        TeachingRequest teachingRequest = teachingRequestRepository.findById(requestId)
                .orElseThrow(() -> requestNotFound(requestId));
        if (!isStaff(role) && teachingRequest.getStatus() != RequestStatus.OPEN) {
            throw requestNotFound(requestId);
        }
        return toResponse(teachingRequest);
    }

    private void replaceAssociations(TeachingRequest request, TeachingRequestRequest source) {
        List<Grade> grades = resolveGrades(source.gradeIds());
        List<District> districts = resolveDistricts(source.districtIds());

        request.getGradeAssociations().clear();
        request.getGradeAssociations().addAll(grades.stream()
                .map(grade -> GradeTeachingRequest.builder().grade(grade).teachingRequest(request).build())
                .toList());
        request.getDistrictAssociations().clear();
        request.getDistrictAssociations().addAll(districts.stream()
                .map(district -> TeachingRequestDistrict.builder().district(district).teachingRequest(request).build())
                .toList());
        request.getAvailabilities().clear();
        if (source.availabilities() != null && !source.availabilities().isEmpty()) {
            request.getAvailabilities().addAll(source.availabilities().stream()
                    .map(availability -> TeachingRequestAvailability.builder()
                            .teachingRequest(request)
                            .dayOfWeek(availability.dayOfWeek())
                            .startTime(availability.startTime())
                            .endTime(availability.endTime())
                            .build())
                    .toList());
        }
    }

    private Subject resolveSubject(TeachingRequestRequest request) {
        String customSubjectName = normalize(request.customSubjectName());
        boolean hasSubjectId = request.subjectId() != null;
        boolean hasCustomSubject = customSubjectName != null;
        if (hasSubjectId == hasCustomSubject) {
            throw badRequest("SUBJECT_SOURCE_REQUIRED", "Exactly one subject source must be provided");
        }
        if (!hasSubjectId) {
            return null;
        }

        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> badRequest("INVALID_SUBJECT", "Subject not found"));
        if (subject.getStatus() != CatalogStatus.ACTIVE) {
            throw badRequest("INVALID_SUBJECT", "Subject is not active");
        }
        return subject;
    }

    private List<Grade> resolveGrades(List<UUID> ids) {
        return distinctIds(ids).stream()
                .map(id -> gradeRepository.findById(id)
                        .filter(grade -> grade.getStatus() == CatalogStatus.ACTIVE)
                        .orElseThrow(() -> badRequest("INVALID_GRADE", "Grade not found or inactive: " + id)))
                .toList();
    }

    private List<District> resolveDistricts(List<UUID> ids) {
        return distinctIds(ids).stream()
                .map(id -> districtRepository.findById(id)
                        .orElseThrow(() -> badRequest("INVALID_DISTRICT", "District not found: " + id)))
                .toList();
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream().distinct().toList();
    }

    private TeachingRequestResponse toResponse(TeachingRequest request) {
        return toResponses(List.of(request)).getFirst();
    }

    private List<TeachingRequestResponse> toResponses(List<TeachingRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<UUID, TeachingRequestStudentRequestCount> countsByTeachingRequestId = studentTutorRequestRepository
                .countByTeachingRequestIds(
                        requests.stream().map(TeachingRequest::getId).toList(),
                        ApplicationStatus.PENDING)
                .stream()
                .collect(Collectors.toMap(
                        TeachingRequestStudentRequestCount::getTeachingRequestId,
                        count -> count));

        return requests.stream()
                .map(request -> toResponse(request, countsByTeachingRequestId.get(request.getId())))
                .toList();
    }

    private TeachingRequestResponse toResponse(
            TeachingRequest request,
            TeachingRequestStudentRequestCount studentRequestCount) {
        List<TeachingRequestResponse.Reference> grades = request.getGradeAssociations().stream()
                .map(teachingRequestMapper::toReference)
                .toList();
        List<TeachingRequestResponse.Reference> districts = request.getDistrictAssociations().stream()
                .map(teachingRequestMapper::toReference)
                .toList();
        List<TeachingRequestResponse.Availability> availabilities = request.getAvailabilities().stream()
                .map(teachingRequestMapper::toAvailability)
                .toList();
        long totalCount = studentRequestCount == null ? 0 : studentRequestCount.getStudentRequestCount();
        long pendingCount = studentRequestCount == null ? 0 : studentRequestCount.getPendingStudentRequestCount();
        return teachingRequestMapper.toResponse(
                request, grades, districts, availabilities, totalCount, pendingCount);
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage teaching requests"));
    }

    private boolean isStaff(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.EMPLOYEE;
    }

    private void clearReviewMetadata(TeachingRequest request) {
        request.setReviewedBy((Employee) null);
        request.setReviewedAt(null);
        request.setRejectionReason(null);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private ApiException requestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TEACHING_REQUEST_NOT_FOUND",
                "Teaching request not found: " + requestId);
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TEACHING_REQUEST_STATUS_TRANSITION", message);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
