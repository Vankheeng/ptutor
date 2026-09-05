package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
import com.ptutor.backend.dto.request.ComplaintUpdateRequest;
import com.ptutor.backend.dto.response.ComplaintResponse;
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

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock ContractRepository contractRepository;
    @Mock UserRepository userRepository;

    private ComplaintService service;
    private UUID userId;
    private UUID contractId;

    @BeforeEach
    void setUp() {
        service = new ComplaintService(
                complaintRepository, evidenceRepository, contractRepository, userRepository,
                testMapper());
        userId = UUID.randomUUID();
        contractId = UUID.randomUUID();
    }

    @Test
    void createsPendingComplaintWithEvidence() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(contractRepository.findByIdAndParticipantUserId(contractId, userId)).thenReturn(Optional.of(contract()));
        when(complaintRepository.saveAndFlush(any(Complaint.class)))
                .thenAnswer(invocation -> {
                    Complaint complaint = invocation.getArgument(0);
                    complaint.setId(UUID.randomUUID());
                    return complaint;
                });
        when(evidenceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(userId, request());

        assertThat(response.contractId()).isEqualTo(contractId);
        assertThat(response.status()).isEqualTo(ComplaintStatus.PENDING);
        assertThat(response.evidences()).singleElement()
                .extracting(value -> value.fileType())
                .isEqualTo("image/png");
        verify(evidenceRepository).flush();
    }

    @Test
    void rejectsComplaintForContractNotOwnedByCurrentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));
        when(contractRepository.findByIdAndParticipantUserId(contractId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(userId, request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_NOT_FOUND");
                });
    }

    @Test
    void detailReturnsResolutionAndEvidenceForCurrentUser() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = Complaint.builder()
                .user(user())
                .contract(contract())
                .title("Schedule issue")
                .content("Lesson was cancelled without notice")
                .status(ComplaintStatus.RESOLVED)
                .resolution("A replacement lesson has been scheduled")
                .build();
        complaint.setId(complaintId);
        Evidence evidence = Evidence.builder().complaint(complaint)
                .fileUrl("https://cdn.example.com/evidence.png").fileType("image/png").build();
        when(complaintRepository.findByIdAndUser_Id(complaintId, userId))
                .thenReturn(Optional.of(complaint));
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)))
                .thenReturn(List.of(evidence));

        var response = service.findMineById(userId, complaintId);

        assertThat(response.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(response.resolution()).isEqualTo("A replacement lesson has been scheduled");
        assertThat(response.evidences()).hasSize(1);
    }

    @Test
    void cancelsPendingComplaintOwnedByCurrentUser() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, ComplaintStatus.PENDING);
        when(complaintRepository.findByIdAndUser_Id(complaintId, userId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId))).thenReturn(List.of());

        var response = service.cancel(userId, complaintId);

        assertThat(response.status()).isEqualTo(ComplaintStatus.CANCELLED);
        verify(complaintRepository).saveAndFlush(complaint);
    }

    @Test
    void updatesPendingComplaintAndReplacesEvidenceWhenProvided() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, ComplaintStatus.PENDING);
        Evidence oldEvidence = Evidence.builder().complaint(complaint)
                .fileUrl("https://cdn.example.com/old.png").fileType("image/png").build();
        when(complaintRepository.findByIdAndUser_Id(complaintId, userId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)))
                .thenReturn(List.of(oldEvidence));
        when(evidenceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(userId, complaintId, updateRequest());

        assertThat(response.title()).isEqualTo("Updated schedule issue");
        assertThat(response.evidences()).singleElement()
                .extracting(ComplaintResponse.Evidence::fileUrl)
                .isEqualTo("https://cdn.example.com/new.png");
        verify(evidenceRepository).deleteAll(List.of(oldEvidence));
    }

    @Test
    void cannotUpdateComplaintAlreadyInReview() {
        UUID complaintId = UUID.randomUUID();
        when(complaintRepository.findByIdAndUser_Id(complaintId, userId))
                .thenReturn(Optional.of(complaint(complaintId, ComplaintStatus.IN_REVIEW)));

        assertThatThrownBy(() -> service.update(userId, complaintId, updateRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_COMPLAINT_STATUS_TRANSITION");
                });
    }

    @Test
    void cannotCancelComplaintAlreadyInReview() {
        UUID complaintId = UUID.randomUUID();
        when(complaintRepository.findByIdAndUser_Id(complaintId, userId))
                .thenReturn(Optional.of(complaint(complaintId, ComplaintStatus.IN_REVIEW)));

        assertThatThrownBy(() -> service.cancel(userId, complaintId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_COMPLAINT_STATUS_TRANSITION");
                });
    }

    private ComplaintCreateRequest request() {
        return new ComplaintCreateRequest(contractId, "Schedule issue", "Lesson was cancelled without notice",
                List.of(new ComplaintCreateRequest.Evidence(
                        "https://cdn.example.com/evidence.png", "image/png")));
    }

    private ComplaintUpdateRequest updateRequest() {
        return new ComplaintUpdateRequest("Updated schedule issue", "Updated content",
                List.of(new ComplaintCreateRequest.Evidence("https://cdn.example.com/new.png", "image/png")));
    }

    private User user() {
        User user = User.builder().email("tutor@example.com").build();
        user.setId(userId);
        return user;
    }

    private Contract contract() {
        Contract contract = Contract.builder().build();
        contract.setId(contractId);
        return contract;
    }

    private Complaint complaint(UUID complaintId, ComplaintStatus status) {
        Complaint complaint = Complaint.builder()
                .user(user())
                .contract(contract())
                .title("Schedule issue")
                .content("Lesson was cancelled without notice")
                .status(status)
                .build();
        complaint.setId(complaintId);
        return complaint;
    }

    private ComplaintMapper testMapper() {
        return new ComplaintMapper() {
            @Override
            public ComplaintResponse toResponse(
                    Complaint complaint, List<ComplaintResponse.Evidence> evidenceResponses) {
                return new ComplaintResponse(
                        complaint.getId(),
                        complaint.getUser() == null ? null : complaint.getUser().getId(),
                        complaint.getContract() == null ? null : complaint.getContract().getId(),
                        complaint.getTitle(),
                        complaint.getContent(),
                        complaint.getStatus(),
                        complaint.getResolution(),
                        complaint.getResolvedAt(),
                        evidenceResponses,
                        complaint.getCreatedAt(),
                        complaint.getUpdatedAt());
            }

            @Override
            public ComplaintResponse.Evidence toEvidenceResponse(Evidence evidence) {
                return new ComplaintResponse.Evidence(evidence.getId(), evidence.getFileUrl(), evidence.getFileType());
            }
        };
    }
}
