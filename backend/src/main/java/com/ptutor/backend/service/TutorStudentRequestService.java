package com.ptutor.backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.TutorStudentRequestCreateRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.TutorStudentRequestResponse;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TutorStudentRequestMapper;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudyingRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

@Service
public class TutorStudentRequestService {

    private final TutorStudentRequestRepository tutorStudentRequestRepository;
    private final StudyingRequestRepository studyingRequestRepository;
    private final TutorRepository tutorRepository;
    private final GradeRepository gradeRepository;
    private final DistrictRepository districtRepository;
    private final StudentRepository studentRepository;
    private final TutorStudentRequestMapper tutorStudentRequestMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TutorStudentRequestService(
            TutorStudentRequestRepository tutorStudentRequestRepository,
            StudyingRequestRepository studyingRequestRepository,
            TutorRepository tutorRepository,
            GradeRepository gradeRepository,
            DistrictRepository districtRepository,
            StudentRepository studentRepository,
            TutorStudentRequestMapper tutorStudentRequestMapper,
            ApplicationEventPublisher eventPublisher) {
        this.tutorStudentRequestRepository = tutorStudentRequestRepository;
        this.studyingRequestRepository = studyingRequestRepository;
        this.tutorRepository = tutorRepository;
        this.gradeRepository = gradeRepository;
        this.districtRepository = districtRepository;
        this.studentRepository = studentRepository;
        this.tutorStudentRequestMapper = tutorStudentRequestMapper;
        this.eventPublisher = eventPublisher;
    }

    public TutorStudentRequestService(
            TutorStudentRequestRepository tutorStudentRequestRepository,
            StudyingRequestRepository studyingRequestRepository,
            TutorRepository tutorRepository,
            GradeRepository gradeRepository,
            DistrictRepository districtRepository,
            StudentRepository studentRepository,
            TutorStudentRequestMapper tutorStudentRequestMapper) {
        this(tutorStudentRequestRepository, studyingRequestRepository, tutorRepository, gradeRepository,
                districtRepository, studentRepository, tutorStudentRequestMapper, event -> { });
    }

    @Transactional
    public TutorStudentRequestResponse create(
            UUID userId, UUID studyingRequestId, TutorStudentRequestCreateRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        StudyingRequest studyingRequest = findOpenStudyingRequest(studyingRequestId);
        Grade grade = gradeRepository.findById(source.gradeId())
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_GRADE", "Grade not found or inactive"));
        var preferredLocation = resolvePreferredLocation(source, source.teachingMode());

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
                .preferredProvince(preferredLocation.province())
                .preferredDistrict(preferredLocation.district())
                .message(normalize(source.message()))
                .status(ApplicationStatus.PENDING)
                .build();
        TutorStudentRequest saved = tutorStudentRequestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                studyingRequest.getStudent().getUser().getId(),
                NotificationEventType.TUTOR_REQUEST_RECEIVED,
                NotificationReferenceType.TUTOR_STUDENT_REQUEST,
                saved.getId(), java.util.Map.of()));
        return tutorStudentRequestMapper.toResponse(saved);
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

    @Transactional
    public TutorStudentRequestResponse update(UUID userId, UUID requestId, TutorStudentRequestCreateRequest source) {
        Tutor tutor = findTutorByUserId(userId);
        TutorStudentRequest request = tutorStudentRequestRepository.findByIdAndTutor_Id(requestId, tutor.getId())
                .orElseThrow(() -> requestNotFound(requestId));
        if (request.getStatus() != ApplicationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION",
                    "Only PENDING teaching proposals can be updated");
        }

        Grade grade = gradeRepository.findById(source.gradeId())
                .filter(value -> value.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_GRADE", "Grade not found or inactive"));
        var preferredLocation = resolvePreferredLocation(source, source.teachingMode());
        request.setGrade(grade);
        request.setProposedPrice(source.proposedPrice());
        request.setTeachingMode(source.teachingMode());
        request.setPreferredSchedule(normalize(source.preferredSchedule()));
        request.setPreferredProvince(preferredLocation.province());
        request.setPreferredDistrict(preferredLocation.district());
        request.setMessage(normalize(source.message()));
        return tutorStudentRequestMapper.toResponse(tutorStudentRequestRepository.saveAndFlush(request));
    }

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
        if (acceptedCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STUDYING_REQUEST_ALREADY_MATCHED",
                    "Studying request already has an accepted tutor");
        }

        tutorRequest.setStatus(ApplicationStatus.ACCEPTED);
        tutorRequest.setRespondedAt(LocalDateTime.now());
        TutorStudentRequest savedRequest = tutorStudentRequestRepository.saveAndFlush(tutorRequest);
        studyingRequest.setStatus(RequestStatus.MATCHED);
        studyingRequestRepository.saveAndFlush(studyingRequest);
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                tutorRequest.getTutor().getUser().getId(),
                NotificationEventType.TUTOR_REQUEST_ACCEPTED,
                NotificationReferenceType.TUTOR_STUDENT_REQUEST,
                savedRequest.getId(), java.util.Map.of()));
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
        tutorRequest.setRespondedAt(LocalDateTime.now());
        TutorStudentRequest saved = tutorStudentRequestRepository.saveAndFlush(tutorRequest);
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                saved.getTutor().getUser().getId(),
                NotificationEventType.TUTOR_REQUEST_REJECTED,
                NotificationReferenceType.TUTOR_STUDENT_REQUEST,
                saved.getId(), java.util.Map.of()));
        return tutorStudentRequestMapper.toResponse(saved);
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can manage teaching proposals"));
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "STUDENT_PROFILE_REQUIRED",
                        "Only a student with a profile can manage tutor requests"));
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

    private ApiException requestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_STUDENT_REQUEST_NOT_FOUND",
                "Tutor student request not found: " + requestId);
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private PreferredLocation resolvePreferredLocation(
            TutorStudentRequestCreateRequest source, com.ptutor.backend.entity.enums.TeachingMode teachingMode) {
        if (teachingMode != com.ptutor.backend.entity.enums.TeachingMode.OFFLINE) {
            return new PreferredLocation(null, null);
        }
        if (source.preferredProvinceId() == null || source.preferredDistrictId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PREFERRED_LOCATION",
                    "Province and district are required for in-person proposals");
        }

        var district = districtRepository.findById(source.preferredDistrictId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PREFERRED_LOCATION",
                        "Selected district was not found"));
        var province = district.getProvince();
        if (province == null || !province.getId().equals(source.preferredProvinceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PREFERRED_LOCATION",
                    "Selected district does not belong to the selected province");
        }
        return new PreferredLocation(province, district);
    }

    private record PreferredLocation(com.ptutor.backend.entity.Province province,
                                     com.ptutor.backend.entity.District district) { }
}
