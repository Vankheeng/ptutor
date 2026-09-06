package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ContractTermsRequest;
import com.ptutor.backend.dto.request.ContractUpdateRequest;
import com.ptutor.backend.dto.request.TutorContractCreateRequest;
import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudentTutorRequest;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.TutorStudentRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.GradeTeachingRequestRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.repository.TutorStudentRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final TutorStudentRequestRepository tutorStudentRequestRepository;
    private final StudentTutorRequestRepository studentTutorRequestRepository;
    private final GradeRepository gradeRepository;
    private final GradeTeachingRequestRepository gradeTeachingRequestRepository;
    private final ContractMapper contractMapper;
    private final ContractTimeProvider contractTimeProvider;

    @Transactional
    public ContractResponse createFromTutorStudentRequest(
            UUID userId,
            UUID studyingRequestId,
            UUID tutorRequestId,
            ContractTermsRequest source) {
        ContractTerms terms = normalizeTerms(source);
        Student student = findStudentByUserId(userId);
        TutorStudentRequest tutorRequest = tutorStudentRequestRepository
                .findByIdAndStudyingRequest_Id(tutorRequestId, studyingRequestId)
                .orElseThrow(() -> tutorStudentRequestNotFound(tutorRequestId));
        StudyingRequest studyingRequest = tutorRequest.getStudyingRequest();

        if (!studyingRequest.getStudent().getId().equals(student.getId())) {
            throw studyingRequestNotFound(studyingRequestId);
        }
        ensureContractableRequest(studyingRequest.getStatus(), "studying request");
        ensureAccepted(tutorRequest.getStatus(), "Tutor student request");
        if (contractRepository.existsByTutorStudentRequest_IdAndStatusNot(
                tutorRequestId, ContractStatus.CANCELLED)) {
            throw duplicateContract();
        }

        Grade grade = requirePresent(tutorRequest.getGrade(), "CONTRACT_GRADE_REQUIRED", "Tutor request grade is required");
        TeachingMode teachingMode = requirePresent(
                tutorRequest.getTeachingMode(), "CONTRACT_TEACHING_MODE_REQUIRED", "Tutor request teaching mode is required");

        Contract contract = Contract.builder()
                .student(student)
                .tutor(requirePresent(tutorRequest.getTutor(), "CONTRACT_TUTOR_REQUIRED", "Tutor is required"))
                .subject(requirePresent(studyingRequest.getSubject(), "CONTRACT_SUBJECT_REQUIRED", "Subject is required"))
                .grade(grade)
                .teachingMode(teachingMode)
                .price(terms.price())
                .paymentPeriod(terms.paymentPeriod())
                .totalLession(terms.totalLessons())
                .preferredSchedule(terms.preferredSchedule())
                .startDate(terms.startDate())
                .endDate(terms.endDate())
                .status(ContractStatus.PENDING)
                .createdBy(student.getUser())
                .tutorStudentRequest(tutorRequest)
                .build();
        return saveNewContract(contract);
    }

    @Transactional
    public ContractResponse createFromStudentTutorRequest(
            UUID userId,
            UUID teachingRequestId,
            UUID studentRequestId,
            TutorContractCreateRequest source) {
        ContractTerms terms = normalizeTerms(new ContractTermsRequest(
                source == null ? null : source.price(),
                source == null ? null : source.paymentPeriod(),
                source == null ? null : source.totalLessons(),
                source == null ? null : source.preferredSchedule(),
                source == null ? null : source.startDate(),
                source == null ? null : source.endDate()));
        Tutor tutor = findTutorByUserId(userId);
        StudentTutorRequest studentRequest = studentTutorRequestRepository
                .findByIdAndTeachingRequest_Id(studentRequestId, teachingRequestId)
                .orElseThrow(() -> studentTutorRequestNotFound(studentRequestId));
        TeachingRequest teachingRequest = studentRequest.getTeachingRequest();

        if (!teachingRequest.getTutor().getId().equals(tutor.getId())) {
            throw teachingRequestNotFound(teachingRequestId);
        }
        ensureContractableRequest(teachingRequest.getStatus(), "teaching request");
        ensureAccepted(studentRequest.getStatus(), "Student tutor request");
        if (teachingRequest.getSubject() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_SUBJECT_REQUIRED",
                    "Teaching request subject must be normalized before creating a contract");
        }
        if (contractRepository.existsByStudentTutorRequest_IdAndStatusNot(
                studentRequestId, ContractStatus.CANCELLED)) {
            throw duplicateContract();
        }

        Grade grade = resolveTutorContractGrade(teachingRequest, studentRequest, source == null ? null : source.gradeId());
        TeachingMode teachingMode = toTeachingMode(studentRequest);

        Contract contract = Contract.builder()
                .student(requirePresent(studentRequest.getStudent(), "CONTRACT_STUDENT_REQUIRED", "Student is required"))
                .tutor(tutor)
                .subject(teachingRequest.getSubject())
                .grade(grade)
                .teachingMode(teachingMode)
                .price(terms.price())
                .paymentPeriod(terms.paymentPeriod())
                .totalLession(terms.totalLessons())
                .preferredSchedule(terms.preferredSchedule())
                .startDate(terms.startDate())
                .endDate(terms.endDate())
                .status(ContractStatus.PENDING)
                .createdBy(tutor.getUser())
                .studentTutorRequest(studentRequest)
                .build();
        return saveNewContract(contract);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> findMine(UUID userId, ContractStatus status, Pageable pageable) {
        Page<Contract> contracts = contractRepository.findAllForParticipant(userId, status, pageable);
        List<ContractResponse> responses = contracts.getContent().stream()
                .map(contractMapper::toResponse)
                .toList();
        return PageResponse.from(contracts, responses);
    }

    @Transactional(readOnly = true)
    public ContractResponse findMineById(UUID userId, UUID contractId) {
        return contractMapper.toResponse(findContractForParticipant(contractId, userId));
    }

    @Transactional
    public ContractResponse update(UUID userId, UUID contractId, ContractUpdateRequest source) {
        if (source == null || source.isEmpty()) {
            throw badRequest("EMPTY_UPDATE_REQUEST", "At least one field must be provided for update");
        }

        Contract contract = contractRepository.findDetailedByIdAndParticipantUserIdForUpdate(contractId, userId)
                .orElseThrow(() -> contractNotFound(contractId));
        ensureCreatorPendingAndCurrent(contract, userId);

        if (contract.getRenewedFromContract() != null) {
            updateRenewalTerms(contract, source);
        } else if (contract.getTutorStudentRequest() != null) {
            updateStudentCreatedContract(contract, source);
        } else if (contract.getStudentTutorRequest() != null) {
            updateTutorCreatedContract(contract, source);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_CONTRACT_ORIGIN", "Contract origin is invalid");
        }

        return contractMapper.toResponse(contractRepository.saveAndFlush(contract));
    }

    @Transactional
    public ContractResponse sign(UUID userId, UUID contractId) {
        Contract contract = findContractForParticipant(contractId, userId);
        ensurePending(contract);
        ensureCounterparty(contract, userId);
        ensureNotExpired(contract);

        User signer = participantUser(contract, userId);
        int updated = contractRepository.activatePendingByCounterparty(
                contractId,
                userId,
                signer,
                ContractStatus.PENDING,
                ContractStatus.ACTIVE,
                contractTimeProvider.today(),
                contractTimeProvider.now());
        if (updated != 1) {
            throw invalidTransition("Contract can no longer be signed");
        }
        return contractMapper.toResponse(findContractForParticipant(contractId, userId));
    }

    @Transactional
    public ContractResponse reject(UUID userId, UUID contractId) {
        Contract contract = findContractForParticipant(contractId, userId);
        ensurePending(contract);
        ensureCounterparty(contract, userId);

        int updated = contractRepository.rejectPendingByCounterparty(
                contractId,
                userId,
                ContractStatus.PENDING,
                ContractStatus.CANCELLED,
                contractTimeProvider.now());
        if (updated != 1) {
            throw invalidTransition("Contract can no longer be rejected");
        }
        return contractMapper.toResponse(findContractForParticipant(contractId, userId));
    }

    @Transactional
    public ContractResponse cancel(UUID userId, UUID contractId) {
        Contract contract = findContractForParticipant(contractId, userId);
        ensurePending(contract);
        if (!contract.getCreatedBy().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONTRACT_CREATOR_REQUIRED",
                    "Only the contract creator can cancel a pending contract");
        }

        int updated = contractRepository.cancelPendingByCreator(
                contractId,
                userId,
                ContractStatus.PENDING,
                ContractStatus.CANCELLED,
                contractTimeProvider.now());
        if (updated != 1) {
            throw invalidTransition("Contract can no longer be cancelled");
        }
        return contractMapper.toResponse(findContractForParticipant(contractId, userId));
    }

    @Transactional
    public ContractResponse renew(UUID userId, UUID contractId, ContractTermsRequest source) {
        ContractTerms terms = normalizeTerms(source);
        Contract current = findContractForParticipant(contractId, userId);
        if (current.getStatus() != ContractStatus.ACTIVE) {
            throw invalidTransition("Only ACTIVE contracts can be renewed");
        }
        ensureNotExpired(current);
        if (contractRepository.existsByRenewedFromContract_IdAndStatusNot(contractId, ContractStatus.CANCELLED)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CONTRACT_RENEWAL",
                    "A pending or effective renewal already exists for this contract");
        }
        if (terms.startDate().isBefore(current.getEndDate())) {
            throw badRequest("INVALID_CONTRACT_DATE_RANGE",
                    "Renewal start date must not be before the previous contract end date");
        }

        Contract renewal = Contract.builder()
                .student(current.getStudent())
                .tutor(current.getTutor())
                .subject(current.getSubject())
                .grade(current.getGrade())
                .teachingMode(current.getTeachingMode())
                .price(terms.price())
                .paymentPeriod(terms.paymentPeriod())
                .totalLession(terms.totalLessons())
                .preferredSchedule(terms.preferredSchedule())
                .startDate(terms.startDate())
                .endDate(terms.endDate())
                .status(ContractStatus.PENDING)
                .createdBy(participantUser(current, userId))
                .renewedFromContract(current)
                .build();
        return saveNewContract(renewal);
    }

    private ContractResponse saveNewContract(Contract contract) {
        try {
            return contractMapper.toResponse(contractRepository.saveAndFlush(contract));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateContract();
        }
    }

    private void updateStudentCreatedContract(Contract contract, ContractUpdateRequest source) {
        if (source.paymentPeriod() != null
                || source.totalLessons() != null
                || source.startDate() != null
                || source.endDate() != null
                || source.gradeId() != null) {
            throw nonEditableField();
        }
        applyPriceAndSchedule(contract, source);
    }

    private void updateTutorCreatedContract(Contract contract, ContractUpdateRequest source) {
        if (source.paymentPeriod() != null
                || source.totalLessons() != null
                || source.startDate() != null
                || source.endDate() != null) {
            throw nonEditableField();
        }
        applyPriceAndSchedule(contract, source);
        if (source.gradeId() != null) {
            StudentTutorRequest origin = contract.getStudentTutorRequest();
            contract.setGrade(resolveTutorContractGrade(origin.getTeachingRequest(), origin, source.gradeId()));
        }
    }

    private void updateRenewalTerms(Contract contract, ContractUpdateRequest source) {
        if (source.gradeId() != null) {
            throw nonEditableField();
        }
        applyPriceAndSchedule(contract, source);
        if (source.paymentPeriod() != null) {
            contract.setPaymentPeriod(requireText(source.paymentPeriod(), "Payment period is required"));
        }
        if (source.totalLessons() != null) {
            validateTotalLessons(source.totalLessons());
            contract.setTotalLession(source.totalLessons());
        }
        LocalDate startDate = source.startDate() == null ? contract.getStartDate() : source.startDate();
        LocalDate endDate = source.endDate() == null ? contract.getEndDate() : source.endDate();
        validateDateRange(startDate, endDate);
        if (startDate.isBefore(contract.getRenewedFromContract().getEndDate())) {
            throw badRequest("INVALID_CONTRACT_DATE_RANGE",
                    "Renewal start date must not be before the previous contract end date");
        }
        if (source.startDate() != null) {
            contract.setStartDate(startDate);
        }
        if (source.endDate() != null) {
            contract.setEndDate(endDate);
        }
    }

    private void applyPriceAndSchedule(Contract contract, ContractUpdateRequest source) {
        if (source.price() != null) {
            validatePrice(source.price());
            contract.setPrice(source.price());
        }
        if (source.preferredSchedule() != null) {
            contract.setPreferredSchedule(requireText(source.preferredSchedule(), "Preferred schedule is required"));
        }
    }

    private Grade resolveTutorContractGrade(
            TeachingRequest teachingRequest, StudentTutorRequest studentRequest, UUID requestedGradeId) {
        if (requestedGradeId == null) {
            return requirePresent(studentRequest.getGrade(), "CONTRACT_GRADE_REQUIRED", "Student request grade is required");
        }
        Grade grade = gradeRepository.findById(requestedGradeId)
                .filter(candidate -> candidate.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> badRequest("INVALID_GRADE", "Grade not found or inactive"));
        if (!gradeTeachingRequestRepository.existsByTeachingRequest_IdAndGrade_Id(teachingRequest.getId(), requestedGradeId)) {
            throw badRequest("INVALID_GRADE", "Grade is not associated with the teaching request");
        }
        return grade;
    }

    private TeachingMode toTeachingMode(StudentTutorRequest studentRequest) {
        if (studentRequest.getLearningMode() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_TEACHING_MODE_REQUIRED",
                    "Student request learning mode is required");
        }
        return TeachingMode.valueOf(studentRequest.getLearningMode().name());
    }

    private ContractTerms normalizeTerms(ContractTermsRequest source) {
        if (source == null) {
            throw badRequest("INVALID_CONTRACT_TERMS", "Contract terms are required");
        }
        validatePrice(source.price());
        validateTotalLessons(source.totalLessons());
        String paymentPeriod = requireText(source.paymentPeriod(), "Payment period is required");
        String preferredSchedule = requireText(source.preferredSchedule(), "Preferred schedule is required");
        validateDateRange(source.startDate(), source.endDate());
        return new ContractTerms(
                source.price(), paymentPeriod, source.totalLessons(), preferredSchedule, source.startDate(), source.endDate());
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("INVALID_CONTRACT_PRICE", "Price must not be negative");
        }
    }

    private void validateTotalLessons(Integer totalLessons) {
        if (totalLessons == null || totalLessons <= 0) {
            throw badRequest("INVALID_TOTAL_LESSONS", "Total lessons must be greater than zero");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw badRequest("INVALID_CONTRACT_DATE_RANGE", "End date must be after start date");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest("INVALID_CONTRACT_TERMS", message);
        }
        return value.trim();
    }

    private void ensureContractableRequest(RequestStatus status, String requestName) {
        if (status != RequestStatus.OPEN && status != RequestStatus.MATCHED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_CONTRACT_SOURCE_STATUS",
                    "Only OPEN or MATCHED " + requestName + " can create a contract");
        }
    }

    private void ensureAccepted(ApplicationStatus status, String requestName) {
        if (status != ApplicationStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_CONTRACT_APPLICATION_STATUS",
                    requestName + " must be ACCEPTED");
        }
    }

    private void ensureCreatorPendingAndCurrent(Contract contract, UUID userId) {
        ensurePending(contract);
        if (!contract.getCreatedBy().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONTRACT_CREATOR_REQUIRED",
                    "Only the contract creator can update a pending contract");
        }
        ensureNotExpired(contract);
    }

    private void ensurePending(Contract contract) {
        if (contract.getStatus() != ContractStatus.PENDING) {
            throw invalidTransition("Only PENDING contracts can be processed");
        }
    }

    private void ensureCounterparty(Contract contract, UUID userId) {
        if (contract.getCreatedBy().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONTRACT_COUNTERPARTY_REQUIRED",
                    "Only the contract counterparty can perform this action");
        }
    }

    private void ensureNotExpired(Contract contract) {
        if (!contract.getEndDate().isAfter(contractTimeProvider.today())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_EXPIRED",
                    "Contract has reached its end date");
        }
    }

    private User participantUser(Contract contract, UUID userId) {
        if (contract.getStudent().getUser().getId().equals(userId)) {
            return contract.getStudent().getUser();
        }
        if (contract.getTutor().getUser().getId().equals(userId)) {
            return contract.getTutor().getUser();
        }
        throw contractNotFound(contract.getId());
    }

    private Contract findContractForParticipant(UUID contractId, UUID userId) {
        return contractRepository.findDetailedByIdAndParticipantUserId(contractId, userId)
                .orElseThrow(() -> contractNotFound(contractId));
    }

    private Student findStudentByUserId(UUID userId) {
        return studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED",
                        "Only a student can create this contract"));
    }

    private Tutor findTutorByUserId(UUID userId) {
        return tutorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TUTOR_PROFILE_REQUIRED",
                        "Only a tutor can create this contract"));
    }

    private <T> T requirePresent(T value, String code, String message) {
        if (value == null) {
            throw new ApiException(HttpStatus.CONFLICT, code, message);
        }
        return value;
    }

    private ApiException duplicateContract() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CONTRACT",
                "A non-cancelled contract already exists for this source");
    }

    private ApiException nonEditableField() {
        return new ApiException(HttpStatus.FORBIDDEN, "CONTRACT_FIELD_NOT_EDITABLE",
                "One or more contract fields cannot be changed by this creator");
    }

    private ApiException invalidTransition(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_CONTRACT_STATUS_TRANSITION", message);
    }

    private ApiException contractNotFound(UUID contractId) {
        return new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found: " + contractId);
    }

    private ApiException tutorStudentRequestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_STUDENT_REQUEST_NOT_FOUND",
                "Tutor student request not found: " + requestId);
    }

    private ApiException studentTutorRequestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_TUTOR_REQUEST_NOT_FOUND",
                "Student tutor request not found: " + requestId);
    }

    private ApiException studyingRequestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDYING_REQUEST_NOT_FOUND",
                "Studying request not found: " + requestId);
    }

    private ApiException teachingRequestNotFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "TEACHING_REQUEST_NOT_FOUND",
                "Teaching request not found: " + requestId);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record ContractTerms(
            BigDecimal price,
            String paymentPeriod,
            Integer totalLessons,
            String preferredSchedule,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
