package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.StudyingRequestRequest;
import com.ptutor.backend.dto.request.StudyingRequestUpdateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.StudyingRequestResponse;
import com.ptutor.backend.entity.District;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.StudyingRequestAvailability;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.StudyingRequestMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestAvailabilityRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudyingRequestService {

    private static final Set<RequestStatus> SUPPORTED_LIST_STATUSES = Set.of(
            RequestStatus.DRAFT,
            RequestStatus.OPEN,
            RequestStatus.MATCHED,
            RequestStatus.CLOSED,
            RequestStatus.CANCELLED);

    private static final List<ApplicationStatus> ACTIVE_TUTOR_REQUEST_STATUSES = List.of(
            ApplicationStatus.PENDING,
            ApplicationStatus.ACCEPTED);

    private final StudyingRequestRepository studyingRequestRepository;
    private final StudyingRequestAvailabilityRepository availabilityRepository;
    private final TutorStudentRequestRepository tutorStudentRequestRepository;
    private final StudyingRequestMapper studyingRequestMapper;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final GradeRepository gradeRepository;
    private final DistrictRepository districtRepository;
    private final Clock clock;

    @Transactional
    public StudyingRequestResponse create(UUID userId, StudyingRequestRequest request) {
        validateRequest(request);

        Student student = findStudentByUserId(userId);
        Subject subject = resolveSubject(request.subjectId());
        Grade grade = resolveGrade(request.gradeId());
        District district = resolveDistrict(request.districtId());

        StudyingRequest studyingRequest = StudyingRequest.builder()
                .student(student)
                .subject(subject)
                .grade(grade)
                .district(district)
                .title(normalize(request.title()))
                .description(normalize(request.description()))
                .note(normalize(request.note()))
                .detailAddress(normalize(request.detailAddress()))
                .minPrice(request.minPrice())
                .maxPrice(request.maxPrice())
                .learningGoals(normalize(request.learningGoals()))
                .learningMode(request.learningMode())
                .preferredSchedule(normalize(request.preferredSchedule()))
                .status(RequestStatus.DRAFT)
                .build();

        StudyingRequest saved = studyingRequestRepository.saveAndFlush(studyingRequest);
        replaceAvailabilities(saved, toAvailabilityValues(request.availabilities()));
        availabilityRepository.flush();
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudyingRequestResponse> findMine(
            UUID userId, RequestStatus status, Pageable pageable) {
        Student student = findStudentByUserId(userId);
        validateListStatus(status);

        Page<StudyingRequest> requests = status == null
                ? studyingRequestRepository.findAllByStudent_IdOrderByCreatedAtDesc(student.getId(), pageable)
                : studyingRequestRepository.findAllByStudent_IdAndStatusOrderByCreatedAtDesc(
                        student.getId(), status, pageable);

        return PageResponse.from(requests, toResponses(requests.getContent()));
    }

    @Transactional(readOnly = true)
    public StudyingRequestResponse findMineById(UUID userId, UUID requestId) {
        Student student = findStudentByUserId(userId);
        return toResponse(studyingRequestRepository.findDetailedByIdAndStudentId(requestId, student.getId())
                .orElseThrow(() -> requestNotFound(requestId)));
    }

    @Transactional
    public StudyingRequestResponse update(
            UUID userId, UUID requestId, StudyingRequestUpdateRequest request) {
        validateUpdateRequest(request);

        Student student = findStudentByUserId(userId);
        StudyingRequest studyingRequest = studyingRequestRepository
                .findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> requestNotFound(requestId));

        if (!isEditable(studyingRequest.getStatus())) {
            throw invalidTransition("Only DRAFT or OPEN requests can be updated");
        }

        Subject subject = request.subjectId() == null
                ? studyingRequest.getSubject()
                : resolveSubject(request.subjectId());
        Grade grade = request.gradeId() == null
                ? studyingRequest.getGrade()
                : resolveGrade(request.gradeId());
        District district = request.districtId() == null
                ? studyingRequest.getDistrict()
                : resolveDistrict(request.districtId());

        BigDecimal effectiveMinPrice = request.minPrice() == null
                ? studyingRequest.getMinPrice()
                : request.minPrice();
        BigDecimal effectiveMaxPrice = request.maxPrice() == null
                ? studyingRequest.getMaxPrice()
                : request.maxPrice();
        validatePriceRange(effectiveMinPrice, effectiveMaxPrice);

        studyingRequestMapper.updateEntity(request, studyingRequest);
        studyingRequest.setSubject(subject);
        studyingRequest.setGrade(grade);
        studyingRequest.setDistrict(district);

        if (request.availabilities() != null) {
            replaceAvailabilities(studyingRequest, toUpdateAvailabilityValues(request.availabilities()));
        }

        StudyingRequest saved = studyingRequestRepository.saveAndFlush(studyingRequest);
        if (request.availabilities() != null) {
            availabilityRepository.flush();
        }
        return toResponse(saved);
    }

    @Transactional
    public StudyingRequestResponse updateStatus(UUID userId, UUID requestId, RequestStatus targetStatus) {
        Student student = findStudentByUserId(userId);
        StudyingRequest studyingRequest = studyingRequestRepository
                .findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> requestNotFound(requestId));

        if ((targetStatus != RequestStatus.OPEN && targetStatus != RequestStatus.CLOSED)
                || (studyingRequest.getStatus() != RequestStatus.OPEN
                        && studyingRequest.getStatus() != RequestStatus.CLOSED)) {
            throw invalidTransition("Only OPEN and CLOSED status transitions are allowed");
        }

        studyingRequest.setStatus(targetStatus);
        return toResponse(studyingRequestRepository.saveAndFlush(studyingRequest));
    }

    @Transactional
    public StudyingRequestResponse cancel(UUID userId, UUID requestId) {
        Student student = findStudentByUserId(userId);
        StudyingRequest studyingRequest = studyingRequestRepository
                .findByIdAndStudent_Id(requestId, student.getId())
                .orElseThrow(() -> requestNotFound(requestId));

        if (!isCancellable(studyingRequest.getStatus())) {
            throw invalidTransition("Only DRAFT, OPEN, MATCHED or CLOSED requests can be cancelled");
        }

        studyingRequest.setStatus(RequestStatus.CANCELLED);
        StudyingRequest saved = studyingRequestRepository.saveAndFlush(studyingRequest);
        tutorStudentRequestRepository.cancelActiveByStudyingRequestId(
                requestId,
                ACTIVE_TUTOR_REQUEST_STATUSES,
                ApplicationStatus.CANCELLED,
                LocalDateTime.now(clock));
        return toResponse(saved);
    }

    /**
     * Activates a draft after a future payment flow confirms a successful payment.
     * This method is intentionally not exposed as an HTTP endpoint yet.
     */
    @Transactional
    public StudyingRequestResponse activateAfterPayment(UUID requestId) {
        StudyingRequest studyingRequest = studyingRequestRepository.findById(requestId)
                .orElseThrow(() -> requestNotFound(requestId));
        if (studyingRequest.getStatus() != RequestStatus.DRAFT) {
            throw invalidTransition("Only DRAFT requests can be activated after payment");
        }

        studyingRequest.setStatus(RequestStatus.OPEN);
        return toResponse(studyingRequestRepository.saveAndFlush(studyingRequest));
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "STUDENT_PROFILE_REQUIRED",
                        "Only a student with a profile can manage studying requests"));
    }

    private Subject resolveSubject(UUID subjectId) {
        return subjectRepository.findById(subjectId)
                .filter(subject -> subject.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> badRequest("INVALID_SUBJECT", "Subject not found or inactive"));
    }

    private Grade resolveGrade(UUID gradeId) {
        return gradeRepository.findById(gradeId)
                .filter(grade -> grade.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> badRequest("INVALID_GRADE", "Grade not found or inactive"));
    }

    private District resolveDistrict(UUID districtId) {
        if (districtId == null) {
            return null;
        }
        return districtRepository.findById(districtId)
                .orElseThrow(() -> badRequest("INVALID_DISTRICT", "District not found"));
    }

    private void replaceAvailabilities(
            StudyingRequest studyingRequest,
            List<AvailabilityValue> sourceAvailabilities) {
        studyingRequest.getAvailabilities().clear();
        if (sourceAvailabilities == null) {
            return;
        }
        studyingRequest.getAvailabilities().addAll(sourceAvailabilities.stream()
                .map(availability -> StudyingRequestAvailability.builder()
                        .studyingRequest(studyingRequest)
                        .dayOfWeek(availability.dayOfWeek())
                        .startTime(availability.startTime())
                        .endTime(availability.endTime())
                        .build())
                .toList());
    }

    private void validateRequest(StudyingRequestRequest request) {
        if (request == null
                || request.subjectId() == null
                || request.gradeId() == null
                || request.learningMode() == null
                || request.title() == null
                || request.title().isBlank()) {
            throw badRequest("INVALID_STUDYING_REQUEST", "Subject, grade, title and learning mode are required");
        }
        if (request.minPrice() != null && request.minPrice().compareTo(BigDecimal.ZERO) < 0
                || request.maxPrice() != null && request.maxPrice().compareTo(BigDecimal.ZERO) < 0
                || request.minPrice() != null && request.maxPrice() != null
                        && request.minPrice().compareTo(request.maxPrice()) > 0) {
            throw badRequest("INVALID_PRICE_RANGE", "Prices must be non-negative and minimum must not exceed maximum");
        }
        if (request.availabilities() != null) {
            request.availabilities().stream()
                    .map(this::toAvailabilityValue)
                    .forEach(this::validateAvailability);
        }
    }

    private void validateUpdateRequest(StudyingRequestUpdateRequest request) {
        if (request == null || request.isEmpty()) {
            throw badRequest("EMPTY_UPDATE_REQUEST", "At least one field must be provided for update");
        }
        if (request.availabilities() != null) {
            request.availabilities().stream()
                    .map(this::toAvailabilityValue)
                    .forEach(this::validateAvailability);
        }
    }

    private void validateAvailability(AvailabilityValue availability) {
        if (availability == null
                || availability.dayOfWeek() == null
                || availability.dayOfWeek() < 1
                || availability.dayOfWeek() > 7
                || availability.startTime() == null
                || availability.endTime() == null
                || !availability.endTime().isAfter(availability.startTime())) {
            throw badRequest("INVALID_AVAILABILITY", "Availability day and time range are invalid");
        }
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0
                || maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw badRequest("INVALID_PRICE_RANGE", "Prices must be non-negative and minimum must not exceed maximum");
        }
    }

    private void validateListStatus(RequestStatus status) {
        if (status != null && !SUPPORTED_LIST_STATUSES.contains(status)) {
            throw badRequest("INVALID_STUDYING_REQUEST_STATUS", "Status is not supported for studying requests");
        }
    }

    private boolean isEditable(RequestStatus status) {
        return status == RequestStatus.DRAFT || status == RequestStatus.OPEN;
    }

    private boolean isCancellable(RequestStatus status) {
        return status == RequestStatus.DRAFT
                || status == RequestStatus.OPEN
                || status == RequestStatus.MATCHED
                || status == RequestStatus.CLOSED;
    }

    private StudyingRequestResponse toResponse(StudyingRequest studyingRequest) {
        List<StudyingRequestResponse.Availability> availabilities = studyingRequest.getAvailabilities().stream()
                .map(studyingRequestMapper::toAvailability)
                .toList();
        return toResponse(studyingRequest, availabilities);
    }

    private List<StudyingRequestResponse> toResponses(List<StudyingRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<UUID> requestIds = requests.stream().map(StudyingRequest::getId).toList();
        Map<UUID, List<StudyingRequestResponse.Availability>> availabilitiesByRequestId =
                availabilityRepository
                        .findAllByStudyingRequest_IdInOrderByStudyingRequest_IdAscDayOfWeekAscStartTimeAsc(requestIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                availability -> availability.getStudyingRequest().getId(),
                                LinkedHashMap::new,
                                Collectors.mapping(studyingRequestMapper::toAvailability, Collectors.toList())));

        return requests.stream()
                .map(request -> toResponse(
                        request,
                        availabilitiesByRequestId.getOrDefault(request.getId(), List.of())))
                .toList();
    }

    private StudyingRequestResponse toResponse(
            StudyingRequest studyingRequest,
            List<StudyingRequestResponse.Availability> availabilities) {
        return studyingRequestMapper.toResponse(studyingRequest, availabilities);
    }

    private List<AvailabilityValue> toAvailabilityValues(
            List<StudyingRequestRequest.Availability> sourceAvailabilities) {
        return sourceAvailabilities == null ? null : sourceAvailabilities.stream()
                .map(this::toAvailabilityValue)
                .toList();
    }

    private List<AvailabilityValue> toUpdateAvailabilityValues(
            List<StudyingRequestUpdateRequest.Availability> sourceAvailabilities) {
        return sourceAvailabilities == null ? null : sourceAvailabilities.stream()
                .map(this::toAvailabilityValue)
                .toList();
    }

    private AvailabilityValue toAvailabilityValue(StudyingRequestRequest.Availability availability) {
        return availability == null ? null
                : new AvailabilityValue(availability.dayOfWeek(), availability.startTime(), availability.endTime());
    }

    private AvailabilityValue toAvailabilityValue(StudyingRequestUpdateRequest.Availability availability) {
        return availability == null ? null
                : new AvailabilityValue(availability.dayOfWeek(), availability.startTime(), availability.endTime());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private ApiException requestNotFound(UUID requestId) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "STUDYING_REQUEST_NOT_FOUND",
                "Studying request not found: " + requestId);
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_STUDYING_REQUEST_STATUS_TRANSITION",
                message);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record AvailabilityValue(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
