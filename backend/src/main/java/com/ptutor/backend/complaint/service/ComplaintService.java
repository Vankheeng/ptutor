package com.ptutor.backend.complaint.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.complaint.dto.ComplaintEvidenceRequest;
import com.ptutor.backend.complaint.dto.ComplaintEvidenceResponse;
import com.ptutor.backend.complaint.dto.ComplaintResolutionRequest;
import com.ptutor.backend.complaint.dto.ComplaintResponse;
import com.ptutor.backend.complaint.dto.CreateComplaintRequest;
import com.ptutor.backend.complaint.repository.ComplaintRepository;
import com.ptutor.backend.complaint.repository.EvidenceRepository;
import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.Evidence;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final EvidenceRepository evidenceRepository;
    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public ComplaintResponse create(UUID userId, CreateComplaintRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Authenticated user was not found"));
        Contract contract = findContract(request.contractId());
        ensureContractParticipant(contract, userId);

        Complaint complaint = Complaint.builder()
                .user(user)
                .contract(contract)
                .title(normalize(request.title()))
                .content(normalize(request.content()))
                .status(ComplaintStatus.PENDING)
                .build();
        Complaint savedComplaint = complaintRepository.saveAndFlush(complaint);
        saveEvidences(savedComplaint, request.evidences());
        return toResponse(savedComplaint);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> findMine(UUID userId) {
        return complaintRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse findMineById(UUID userId, UUID complaintId) {
        Complaint complaint = complaintRepository.findByIdAndUser_Id(complaintId, userId)
                .orElseThrow(() -> complaintNotFound(complaintId));
        return toResponse(complaint);
    }

    @Transactional
    public ComplaintEvidenceResponse addEvidence(UUID userId, UUID complaintId, ComplaintEvidenceRequest request) {
        Complaint complaint = findMineEntity(userId, complaintId);
        ensureEvidenceCanBeChanged(complaint);
        Evidence evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                .complaint(complaint)
                .fileUrl(normalize(request.fileUrl()))
                .fileType(normalize(request.fileType()))
                .build());
        return ComplaintEvidenceResponse.from(evidence);
    }

    @Transactional
    public void deleteEvidence(UUID userId, UUID complaintId, UUID evidenceId) {
        Complaint complaint = findMineEntity(userId, complaintId);
        ensureEvidenceCanBeChanged(complaint);
        Evidence evidence = evidenceRepository.findByIdAndComplaint_Id(evidenceId, complaintId)
                .orElseThrow(() -> evidenceNotFound(evidenceId));
        evidenceRepository.delete(evidence);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> findForManagement(ComplaintStatus status) {
        List<Complaint> complaints = status == null
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return complaints.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse findForManagementById(UUID complaintId) {
        return toResponse(findComplaint(complaintId));
    }

    @Transactional
    public ComplaintResponse updateStatus(
            UUID employeeUserId, UUID complaintId, ComplaintResolutionRequest request) {
        Complaint complaint = findComplaint(complaintId);
        Employee employee = employeeRepository.findByUser_Id(employeeUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "Only an employee can process complaints"));

        ComplaintStatus status = request.status();
        if (status == ComplaintStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPLAINT_STATUS",
                    "A complaint cannot be moved back to PENDING");
        }
        if (isTerminal(complaint.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPLAINT_ALREADY_RESOLVED",
                    "A resolved or rejected complaint cannot be processed again");
        }
        String resolution = normalize(request.resolution());
        if ((status == ComplaintStatus.RESOLVED || status == ComplaintStatus.REJECTED)
                && (resolution == null || resolution.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESOLUTION_REQUIRED",
                    "Resolution is required when resolving or rejecting a complaint");
        }

        complaint.setEmployee(employee);
        complaint.setStatus(status);
        complaint.setResolution(resolution);
        complaint.setResolvedAt(isTerminal(status) ? LocalDateTime.now(clock) : null);
        Complaint savedComplaint = complaintRepository.saveAndFlush(complaint);
        notificationRepository.save(Notification.builder()
                .user(complaint.getUser())
                .title("Complaint status updated")
                .content(notificationContent(status, resolution))
                .referenceId(complaint.getId().toString())
                .type(NotificationType.COMPLAINT)
                .isRead(false)
                .build());
        return toResponse(savedComplaint);
    }

    private void saveEvidences(Complaint complaint, List<ComplaintEvidenceRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        evidenceRepository.saveAll(requests.stream()
                .map(request -> Evidence.builder()
                        .complaint(complaint)
                        .fileUrl(normalize(request.fileUrl()))
                        .fileType(normalize(request.fileType()))
                        .build())
                .toList());
    }

    private ComplaintResponse toResponse(Complaint complaint) {
        List<ComplaintEvidenceResponse> evidences = evidenceRepository
                .findAllByComplaint_IdOrderByCreatedAtAsc(complaint.getId()).stream()
                .map(ComplaintEvidenceResponse::from)
                .toList();
        return ComplaintResponse.from(complaint, evidences);
    }

    private Complaint findMineEntity(UUID userId, UUID complaintId) {
        return complaintRepository.findByIdAndUser_Id(complaintId, userId)
                .orElseThrow(() -> complaintNotFound(complaintId));
    }

    private Complaint findComplaint(UUID complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> complaintNotFound(complaintId));
    }

    private Contract findContract(UUID contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CONTRACT_NOT_FOUND",
                        "Contract not found: " + contractId));
    }

    private void ensureContractParticipant(Contract contract, UUID userId) {
        boolean isStudent = contract.getStudent() != null
                && contract.getStudent().getUser() != null
                && userId.equals(contract.getStudent().getUser().getId());
        boolean isTutor = contract.getTutor() != null
                && contract.getTutor().getUser() != null
                && userId.equals(contract.getTutor().getUser().getId());
        if (!isStudent && !isTutor) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONTRACT_ACCESS_DENIED",
                    "You can only create a complaint for your own contract");
        }
    }

    private void ensureEvidenceCanBeChanged(Complaint complaint) {
        if (isTerminal(complaint.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPLAINT_ALREADY_RESOLVED",
                    "Evidence cannot be changed after a complaint is resolved or rejected");
        }
    }

    private boolean isTerminal(ComplaintStatus status) {
        return status == ComplaintStatus.RESOLVED || status == ComplaintStatus.REJECTED;
    }

    private String notificationContent(ComplaintStatus status, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            return "Your complaint is now in status " + status + ".";
        }
        return "Your complaint is now in status " + status + ": " + resolution;
    }

    private ApiException complaintNotFound(UUID complaintId) {
        return new ApiException(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND",
                "Complaint not found: " + complaintId);
    }

    private ApiException evidenceNotFound(UUID evidenceId) {
        return new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND",
                "Evidence not found: " + evidenceId);
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
