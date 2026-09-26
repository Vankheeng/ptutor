package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.dto.response.AdminComplaintDetailResponse;
import com.ptutor.backend.dto.response.AdminComplaintSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.ContractPaymentInstallment;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Evidence;
import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.Payment;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.EmployeeRole;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractPaymentInstallmentRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.LessonRepository;
import com.ptutor.backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminComplaintService {

    private final ComplaintRepository complaintRepository;
    private final EvidenceRepository evidenceRepository;
    private final EmployeeRepository employeeRepository;
    private final LessonRepository lessonRepository;
    private final ContractPaymentInstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminComplaintSummaryResponse> findAll(
            ComplaintStatus status, UUID contractId, String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        Page<Complaint> complaints = complaintRepository.findAllForReview(
                status, contractId, normalizedKeyword, pageable);
        return PageResponse.from(complaints, complaints.getContent().stream()
                .map(this::toSummary)
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminComplaintDetailResponse findById(UUID complaintId) {
        return toDetail(findDetailed(complaintId));
    }

    @Transactional
    public AdminComplaintDetailResponse startReview(UUID reviewerUserId, UUID complaintId) {
        Complaint complaint = findForUpdate(complaintId);
        if (complaint.getStatus() != ComplaintStatus.PENDING || complaint.getEmployee() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPLAINT_ALREADY_ASSIGNED",
                    "Only an unassigned PENDING complaint can be accepted for review");
        }

        complaint.setEmployee(findReviewer(reviewerUserId));
        complaint.setStatus(ComplaintStatus.IN_REVIEW);
        complaint.setResolution(null);
        complaint.setResolvedAt(null);
        return toDetail(complaintRepository.saveAndFlush(complaint));
    }

    @Transactional
    public AdminComplaintDetailResponse requestEvidence(
            UUID reviewerUserId, UUID complaintId, String message) {
        Complaint complaint = findAssignedInReview(reviewerUserId, complaintId);
        String normalizedMessage = normalizeRequired(message, "INVALID_EVIDENCE_REQUEST");
        complaint.setStatus(ComplaintStatus.AWAITING_EVIDENCE);
        complaint.setResolution(normalizedMessage);
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        publishNotification(saved, NotificationEventType.COMPLAINT_EVIDENCE_REQUESTED, normalizedMessage);
        return toDetail(saved);
    }

    @Transactional
    public AdminComplaintDetailResponse reassign(
            UUID adminUserId, UUID complaintId, UUID targetEmployeeId) {
        Complaint complaint = findForUpdate(complaintId);
        Employee admin = findReviewer(adminUserId);
        if (admin.getRole() != EmployeeRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPLAINT_REASSIGN_FORBIDDEN",
                    "Only an admin can reassign complaints");
        }
        if (complaint.getStatus() != ComplaintStatus.IN_REVIEW
                && complaint.getStatus() != ComplaintStatus.AWAITING_EVIDENCE) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_COMPLAINT_STATUS_TRANSITION",
                    "Only an IN_REVIEW or AWAITING_EVIDENCE complaint can be reassigned");
        }
        if (complaint.getEmployee() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPLAINT_NOT_ASSIGNED",
                    "The complaint must be assigned before it can be reassigned");
        }

        Employee targetEmployee = employeeRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND",
                        "Employee not found: " + targetEmployeeId));
        if (targetEmployee.getUser() == null || targetEmployee.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_NOT_ACTIVE",
                    "The target employee account must be active");
        }
        if (complaint.getEmployee().getId().equals(targetEmployee.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPLAINT_ALREADY_ASSIGNED_TO_EMPLOYEE",
                    "The complaint is already assigned to this employee");
        }

        complaint.setEmployee(targetEmployee);
        return toDetail(complaintRepository.saveAndFlush(complaint));
    }

    @Transactional
    public AdminComplaintDetailResponse accept(
            UUID reviewerUserId, UUID complaintId, String resolution) {
        return complete(reviewerUserId, complaintId, resolution,
                ComplaintStatus.RESOLVED, NotificationEventType.COMPLAINT_RESOLVED);
    }

    @Transactional
    public AdminComplaintDetailResponse reject(
            UUID reviewerUserId, UUID complaintId, String resolution) {
        return complete(reviewerUserId, complaintId, resolution,
                ComplaintStatus.REJECTED, NotificationEventType.COMPLAINT_REJECTED);
    }

    private AdminComplaintDetailResponse complete(
            UUID reviewerUserId,
            UUID complaintId,
            String resolution,
            ComplaintStatus finalStatus,
            NotificationEventType eventType) {
        Complaint complaint = findAssignedInReview(reviewerUserId, complaintId);
        String normalizedResolution = normalizeRequired(resolution, "INVALID_COMPLAINT_RESOLUTION");
        complaint.setStatus(finalStatus);
        complaint.setResolution(normalizedResolution);
        complaint.setResolvedAt(LocalDateTime.now(clock));
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        publishNotification(saved, eventType, normalizedResolution);
        return toDetail(saved);
    }

    private Complaint findAssignedInReview(UUID reviewerUserId, UUID complaintId) {
        Complaint complaint = findForUpdate(complaintId);
        if (complaint.getStatus() != ComplaintStatus.IN_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_COMPLAINT_STATUS_TRANSITION",
                    "Only an IN_REVIEW complaint can be processed");
        }
        if (complaint.getEmployee() == null
                || !reviewerUserId.equals(complaint.getEmployee().getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPLAINT_REVIEW_FORBIDDEN",
                    "Only the assigned employee can process this complaint");
        }
        return complaint;
    }

    private Employee findReviewer(UUID reviewerUserId) {
        return employeeRepository.findByUser_Id(reviewerUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "An employee profile is required to review complaints"));
    }

    private Complaint findDetailed(UUID complaintId) {
        return complaintRepository.findDetailedById(complaintId)
                .orElseThrow(() -> complaintNotFound(complaintId));
    }

    private Complaint findForUpdate(UUID complaintId) {
        return complaintRepository.findByIdForUpdate(complaintId)
                .orElseThrow(() -> complaintNotFound(complaintId));
    }

    private ApiException complaintNotFound(UUID complaintId) {
        return new ApiException(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND",
                "Complaint not found: " + complaintId);
    }

    private String normalizeRequired(String value, String errorCode) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 5000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "The provided text is required and must not exceed 5000 characters");
        }
        return normalized;
    }

    private void publishNotification(Complaint complaint, NotificationEventType eventType, String reason) {
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                complaint.getUser().getId(), eventType, NotificationReferenceType.COMPLAINT,
                complaint.getId(), Map.of("reason", reason)));
    }

    private AdminComplaintSummaryResponse toSummary(Complaint complaint) {
        User complainant = complaint.getUser();
        Employee reviewer = complaint.getEmployee();
        return new AdminComplaintSummaryResponse(
                complaint.getId(), complainant.getId(), fullName(complainant), complainant.getEmail(),
                complainantRole(complaint), complaint.getContract().getId(), complaint.getTitle(),
                complaint.getStatus(), reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : fullName(reviewer.getUser()),
                complaint.getCreatedAt(), complaint.getUpdatedAt());
    }

    private AdminComplaintDetailResponse toDetail(Complaint complaint) {
        Contract contract = complaint.getContract();
        List<Evidence> evidences = evidenceRepository
                .findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaint.getId()));
        List<Lesson> lessons = lessonRepository.findAllByContract_IdOrderByDateAscStartTimeAsc(contract.getId());
        List<ContractPaymentInstallment> installments = installmentRepository
                .findAllByContract_IdOrderBySequenceNumberAsc(contract.getId());
        List<Payment> payments = paymentRepository.findAllForContract(contract.getId(), ReferenceType.CONTRACT);
        Employee reviewer = complaint.getEmployee();
        User complainant = complaint.getUser();

        return new AdminComplaintDetailResponse(
                complaint.getId(), complaint.getTitle(), complaint.getContent(), complaint.getStatus(),
                complaint.getResolution(), complaint.getResolvedAt(),
                new AdminComplaintDetailResponse.Complainant(
                        complainant.getId(), fullName(complainant), complainant.getEmail(), complainantRole(complaint)),
                reviewer == null ? null : new AdminComplaintDetailResponse.Reviewer(
                        reviewer.getId(), reviewer.getUser().getId(), fullName(reviewer.getUser())),
                toContractDetail(contract),
                evidences.stream().map(this::toEvidenceDetail).toList(),
                lessons.stream().map(this::toLessonDetail).toList(),
                installments.stream().map(this::toInstallmentDetail).toList(),
                payments.stream().map(this::toPaymentDetail).toList(),
                complaint.getCreatedAt(), complaint.getUpdatedAt());
    }

    private AdminComplaintDetailResponse.ContractDetail toContractDetail(Contract contract) {
        return new AdminComplaintDetailResponse.ContractDetail(
                contract.getId(), contract.getStudent().getUser().getId(), fullName(contract.getStudent().getUser()),
                contract.getTutor().getUser().getId(), fullName(contract.getTutor().getUser()),
                contract.getSubject().getName(), contract.getGrade().getName(), contract.getTeachingMode(),
                contract.getPrice(), contract.getPaymentPeriod(), contract.getTotalLession(),
                contract.getStartDate(), contract.getEndDate(), contract.getStatus());
    }

    private AdminComplaintDetailResponse.EvidenceDetail toEvidenceDetail(Evidence evidence) {
        return new AdminComplaintDetailResponse.EvidenceDetail(
                evidence.getId(), evidence.getFileUrl(), evidence.getFileType(), evidence.getCreatedAt());
    }

    private AdminComplaintDetailResponse.LessonDetail toLessonDetail(Lesson lesson) {
        return new AdminComplaintDetailResponse.LessonDetail(
                lesson.getId(), lesson.getTitle(), lesson.getDate(), lesson.getStartTime(), lesson.getEndTime(),
                lesson.getTeachingMode(), lesson.getStatus(), lesson.getNote());
    }

    private AdminComplaintDetailResponse.InstallmentDetail toInstallmentDetail(
            ContractPaymentInstallment installment) {
        return new AdminComplaintDetailResponse.InstallmentDetail(
                installment.getId(), installment.getLesson() == null ? null : installment.getLesson().getId(),
                installment.getSequenceNumber(), installment.getPaymentPeriod(), installment.getAmount(),
                installment.getDueDate(), installment.getStatus());
    }

    private AdminComplaintDetailResponse.PaymentDetail toPaymentDetail(Payment payment) {
        return new AdminComplaintDetailResponse.PaymentDetail(
                payment.getId(), payment.getPaymentInstallment() == null
                        ? null : payment.getPaymentInstallment().getId(),
                payment.getPaymentType(), payment.getPaymentMethod(), payment.getStatus(), payment.getAmount(),
                payment.getTransactionCode(), payment.getProviderTransactionNo(), payment.getPaidAt(),
                payment.getCreatedAt());
    }

    private UserRole complainantRole(Complaint complaint) {
        UUID complainantId = complaint.getUser().getId();
        return complainantId.equals(complaint.getContract().getStudent().getUser().getId())
                ? UserRole.STUDENT : UserRole.TUTOR;
    }

    private String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }
}
