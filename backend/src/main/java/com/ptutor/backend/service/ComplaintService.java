package com.ptutor.backend.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
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

    private final ComplaintRepository complaintRepository;
    private final EvidenceRepository evidenceRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ComplaintMapper complaintMapper;

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
                .status(ComplaintStatus.PENDING)
                .build());
        List<Evidence> evidences = saveEvidences(complaint, source.evidences());
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
        Complaint complaint = complaintRepository.findByIdAndUser_Id(complaintId, userId)
                .orElseThrow(() -> complaintNotFound(complaintId));
        ensurePending(complaint, "updated");

        complaint.setTitle(normalize(source.title()));
        complaint.setContent(normalize(source.content()));
        Complaint saved = complaintRepository.saveAndFlush(complaint);

        List<Evidence> evidences = evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId));
        if (source.evidences() != null) {
            evidenceRepository.deleteAll(evidences);
            evidenceRepository.flush();
            evidences = saveEvidences(saved, source.evidences());
        }
        return toResponse(saved, evidences);
    }

    @Transactional
    public ComplaintResponse cancel(UUID userId, UUID complaintId) {
        Complaint complaint = complaintRepository.findByIdAndUser_Id(complaintId, userId)
                .orElseThrow(() -> complaintNotFound(complaintId));
        ensurePending(complaint, "cancelled");

        complaint.setStatus(ComplaintStatus.CANCELLED);
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        return toResponse(saved, evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)));
    }

    private List<Evidence> saveEvidences(Complaint complaint, List<ComplaintCreateRequest.Evidence> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Evidence> evidences = source.stream()
                .map(value -> Evidence.builder()
                        .complaint(complaint)
                        .fileUrl(normalize(value.fileUrl()))
                        .fileType(normalize(value.fileType()))
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
