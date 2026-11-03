package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
import com.ptutor.backend.dto.request.ComplaintEvidenceSubmissionRequest;
import com.ptutor.backend.dto.request.ComplaintRespondentResponseRequest;
import com.ptutor.backend.dto.request.ComplaintUpdateRequest;
import com.ptutor.backend.dto.response.ComplaintResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Evidence;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ComplaintMapper;
import com.ptutor.backend.repository.ComplaintRepository;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.EvidenceRepository;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    // The complainant can still edit or cancel a PENDING complaint, so the respondent only sees it afterwards.
    private static final Set<ComplaintStatus> RESPONDENT_VISIBLE_STATUSES = EnumSet.of(
            ComplaintStatus.IN_REVIEW, ComplaintStatus.AWAITING_EVIDENCE,
            ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);
    private static final Set<ComplaintStatus> RESPONDENT_RESPONSE_STATUSES = EnumSet.of(
            ComplaintStatus.IN_REVIEW, ComplaintStatus.AWAITING_EVIDENCE);
    private static final Set<ComplaintStatus> FINAL_STATUSES = EnumSet.of(
            ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);

    private final ComplaintRepository complaintRepository;
    private final EvidenceRepository evidenceRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ComplaintMapper complaintMapper;
    private final Clock clock;

    @Transactional
    public ComplaintResponse create(UUID userId, ComplaintCreateRequest source) {
        User user = findUserById(userId);
        Contract contract = contractRepository.findByIdAndParticipantUserId(source.contractId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                        "Contract not found: " + source.contractId()));

        Complaint complaint = complaintRepository.saveAndFlush(Complaint.builder()
                .user(user)
                .contract(contract)
                .title(normalize(source.title()))
                .content(normalize(source.content()))
                .category(source.category())
                .status(ComplaintStatus.PENDING)
                .build());
        List<Evidence> evidences = saveEvidences(complaint, user, source.evidences());
        return toResponse(complaint, evidences);
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> findMine(
            UUID userId, ComplaintStatus status, Pageable pageable) {
        Page<Complaint> complaints = status == null
                ? complaintRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageable)
                : complaintRepository.findAllByUser_IdAndStatusOrderByCreatedAtDesc(
                        userId, status, pageable);
        return PageResponse.from(complaints, toResponses(complaints.getContent()));
    }

    @Transactional(readOnly = true)
    public ComplaintResponse findMineById(UUID userId, UUID complaintId) {
        Complaint complaint = complaintRepository.findByIdAndUser_Id(complaintId, userId)
                .orElseThrow(() -> complaintNotFound(complaintId));
        return toResponse(complaint, evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)));
    }

    @Transactional
    public ComplaintResponse update(UUID userId, UUID complaintId, ComplaintUpdateRequest source) {
        Complaint complaint = findMineForUpdate(userId, complaintId);
        ensurePending(complaint, "updated");

        complaint.setTitle(normalize(source.title()));
        complaint.setContent(normalize(source.content()));
        complaint.setCategory(source.category());
        Complaint saved = complaintRepository.saveAndFlush(complaint);

        List<Evidence> evidences = evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId));
        if (source.evidences() != null) {
            evidenceRepository.deleteAll(evidences);
            evidenceRepository.flush();
            evidences = saveEvidences(saved, saved.getUser(), source.evidences());
        }
        return toResponse(saved, evidences);
    }

    @Transactional
    public ComplaintResponse cancel(UUID userId, UUID complaintId) {
        Complaint complaint = findMineForUpdate(userId, complaintId);
        ensurePending(complaint, "cancelled");

        complaint.setStatus(ComplaintStatus.CANCELLED);
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        return toResponse(saved, evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)));
    }

    @Transactional
    public ComplaintResponse submitEvidence(
            UUID userId, UUID complaintId, ComplaintEvidenceSubmissionRequest source) {
        Complaint complaint = findMineForUpdate(userId, complaintId);
        if (complaint.getStatus() != ComplaintStatus.AWAITING_EVIDENCE) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_COMPLAINT_STATUS_TRANSITION",
                    "Evidence can only be submitted for an AWAITING_EVIDENCE complaint");
        }

        saveEvidences(complaint, complaint.getUser(), source.evidences());
        complaint.setStatus(ComplaintStatus.IN_REVIEW);
        complaint.setResolution(null);
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        return toResponse(saved,
                evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> findAgainstMe(
            UUID userId, ComplaintStatus status, Pageable pageable) {
        if (status != null && !RESPONDENT_VISIBLE_STATUSES.contains(status)) {
            return PageResponse.from(new PageImpl<>(List.<Complaint>of(), pageable, 0), List.of());
        }
        Page<Complaint> complaints = complaintRepository.findAllAgainstUser(
                userId, status == null ? RESPONDENT_VISIBLE_STATUSES : Set.of(status), pageable);
        return PageResponse.from(complaints, toResponses(complaints.getContent()).stream()
                .map(this::forRespondent)
                .toList());
    }

    @Transactional(readOnly = true)
    public ComplaintResponse findAgainstMeById(UUID userId, UUID complaintId) {
        Complaint complaint = complaintRepository.findDetailedById(complaintId)
                .filter(value -> isVisibleToRespondent(value, userId))
                .orElseThrow(() -> complaintNotFound(complaintId));
        return forRespondent(toResponse(complaint,
                evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId))));
    }

    @Transactional
    public ComplaintResponse respond(
            UUID userId, UUID complaintId, ComplaintRespondentResponseRequest source) {
        Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                .filter(value -> isVisibleToRespondent(value, userId))
                .orElseThrow(() -> complaintNotFound(complaintId));
        if (!RESPONDENT_RESPONSE_STATUSES.contains(complaint.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESPONDENT_RESPONSE_NOT_ALLOWED",
                    "A response can only be submitted while the complaint is under review");
        }
        if (complaint.getRespondentRespondedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESPONDENT_RESPONSE_ALREADY_SUBMITTED",
                    "A response has already been submitted for this complaint");
        }

        complaint.setRespondentResponse(normalize(source.response()));
        complaint.setRespondentRespondedAt(LocalDateTime.now(clock));
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        saveEvidences(saved, ComplaintParties.respondentOf(saved), source.evidences());
        return forRespondent(toResponse(saved,
                evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId))));
    }

    private boolean isVisibleToRespondent(Complaint complaint, UUID userId) {
        return RESPONDENT_VISIBLE_STATUSES.contains(complaint.getStatus())
                && ComplaintParties.isRespondent(complaint, userId);
    }

    // While a complaint is open, its resolution holds the reviewer's message to the complainant.
    private ComplaintResponse forRespondent(ComplaintResponse response) {
        if (FINAL_STATUSES.contains(response.status())) {
            return response;
        }
        return new ComplaintResponse(
                response.id(), response.userId(), response.contractId(), response.title(), response.content(),
                response.category(), response.status(), null, response.faultParty(), response.resolvedAt(),
                response.respondentResponse(), response.respondentRespondedAt(), response.evidences(),
                response.createdAt(), response.updatedAt());
    }

    private List<Evidence> saveEvidences(
            Complaint complaint, User uploadedBy, List<ComplaintCreateRequest.Evidence> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Evidence> evidences = source.stream()
                .map(value -> Evidence.builder()
                        .complaint(complaint)
                        .fileUrl(normalize(value.fileUrl()))
                        .fileType(normalize(value.fileType()))
                        .uploadedBy(uploadedBy)
                        .build())
                .toList();
        List<Evidence> saved = evidenceRepository.saveAll(evidences);
        evidenceRepository.flush();
        return saved;
    }

    private List<ComplaintResponse> toResponses(List<Complaint> complaints) {
        if (complaints.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Evidence>> evidencesByComplaintId = evidenceRepository
                .findAllByComplaint_IdInOrderByCreatedAtAsc(complaints.stream().map(Complaint::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(evidence -> evidence.getComplaint().getId()));
        return complaints.stream()
                .map(complaint -> toResponse(complaint, evidencesByComplaintId.getOrDefault(complaint.getId(), List.of())))
                .toList();
    }

    private ComplaintResponse toResponse(Complaint complaint, Collection<Evidence> evidences) {
        return complaintMapper.toResponse(
                complaint,
                evidences.stream().map(complaintMapper::toEvidenceResponse).toList());
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Authenticated user not found"));
    }

    private Complaint findMineForUpdate(UUID userId, UUID complaintId) {
        Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                .orElseThrow(() -> complaintNotFound(complaintId));
        if (!userId.equals(complaint.getUser().getId())) {
            throw complaintNotFound(complaintId);
        }
        return complaint;
    }

    private void ensurePending(Complaint complaint, String action) {
        if (complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_COMPLAINT_STATUS_TRANSITION",
                    "Only PENDING complaints can be " + action);
        }
    }

    private ApiException complaintNotFound(UUID complaintId) {
        return new ApiException(HttpStatus.NOT_FOUND, "COMPLAINT_NOT_FOUND",
                "Complaint not found: " + complaintId);
    }

    private String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
