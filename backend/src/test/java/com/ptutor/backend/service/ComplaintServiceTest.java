package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.ComplaintCreateRequest;
import com.ptutor.backend.dto.request.ComplaintEvidenceSubmissionRequest;
import com.ptutor.backend.dto.request.ComplaintRespondentResponseRequest;
import com.ptutor.backend.dto.request.ComplaintUpdateRequest;
import com.ptutor.backend.dto.response.ComplaintResponse;
import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Evidence;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.ComplaintCategory;
import com.ptutor.backend.entity.enums.ComplaintFaultParty;
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
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
        service = new ComplaintService(
                complaintRepository, evidenceRepository, contractRepository, userRepository,
                testMapper(), clock);
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
        assertThat(response.category()).isEqualTo(ComplaintCategory.ABSENCE);
        assertThat(response.evidences()).singleElement()
                .satisfies(value -> {
                    assertThat(value.fileType()).isEqualTo("image/png");
                    assertThat(value.uploadedByUserId()).isEqualTo(userId);
                });
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
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
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
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)))
                .thenReturn(List.of(oldEvidence));
        when(evidenceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(userId, complaintId, updateRequest());

        assertThat(response.title()).isEqualTo("Updated schedule issue");
        assertThat(response.category()).isEqualTo(ComplaintCategory.LATE_OR_SHORT);
        assertThat(response.evidences()).singleElement()
                .extracting(ComplaintResponse.Evidence::fileUrl)
                .isEqualTo("https://cdn.example.com/new.png");
        verify(evidenceRepository).deleteAll(List.of(oldEvidence));
    }

    @Test
    void cannotUpdateComplaintAlreadyInReview() {
        UUID complaintId = UUID.randomUUID();
        when(complaintRepository.findByIdForUpdate(complaintId))
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
        when(complaintRepository.findByIdForUpdate(complaintId))
                .thenReturn(Optional.of(complaint(complaintId, ComplaintStatus.IN_REVIEW)));

        assertThatThrownBy(() -> service.cancel(userId, complaintId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_COMPLAINT_STATUS_TRANSITION");
                });
    }

    @Test
    void submitsAdditionalEvidenceAndReturnsComplaintToReview() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, ComplaintStatus.AWAITING_EVIDENCE);
        Evidence evidence = Evidence.builder().complaint(complaint)
                .fileUrl("https://cdn.example.com/additional.png").fileType("image/png").build();
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));
        when(evidenceRepository.saveAll(any())).thenReturn(List.of(evidence));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaintId)))
                .thenReturn(List.of(evidence));

        ComplaintEvidenceSubmissionRequest request = new ComplaintEvidenceSubmissionRequest(List.of(
                new ComplaintCreateRequest.Evidence(
                        "https://cdn.example.com/additional.png", "image/png")));
        ComplaintResponse response = service.submitEvidence(userId, complaintId, request);

        assertThat(response.status()).isEqualTo(ComplaintStatus.IN_REVIEW);
        assertThat(response.resolution()).isNull();
        assertThat(response.evidences()).hasSize(1);
    }

    @Test
    void rejectsAdditionalEvidenceFromAnotherUser() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, ComplaintStatus.AWAITING_EVIDENCE);
        when(complaintRepository.findByIdForUpdate(complaintId)).thenReturn(Optional.of(complaint));

        ComplaintEvidenceSubmissionRequest request = new ComplaintEvidenceSubmissionRequest(List.of(
                new ComplaintCreateRequest.Evidence(
                        "https://cdn.example.com/additional.png", "image/png")));

        assertThatThrownBy(() -> service.submitEvidence(UUID.randomUUID(), complaintId, request))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("COMPLAINT_NOT_FOUND");
                });
    }

    @Test
    void respondentListOnlyShowsComplaintsPastPendingAndHidesOpenReviewerMessages() {
        UUID respondentId = UUID.randomUUID();
        Complaint complaint = complaintWithParticipants(ComplaintStatus.AWAITING_EVIDENCE, respondentId);
        complaint.setResolution("Please upload the payment receipt");
        PageRequest pageable = PageRequest.of(0, 20);
        when(complaintRepository.findAllAgainstUser(respondentId, Set.of(
                ComplaintStatus.IN_REVIEW, ComplaintStatus.AWAITING_EVIDENCE,
                ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED), pageable))
                .thenReturn(new PageImpl<>(List.of(complaint), pageable, 1));
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaint.getId())))
                .thenReturn(List.of());

        var response = service.findAgainstMe(respondentId, null, pageable);

        assertThat(response.content()).singleElement()
                .satisfies(value -> assertThat(value.resolution()).isNull());
    }

    @Test
    void respondentListIsEmptyForStatusesTheyCannotSee() {
        var response = service.findAgainstMe(UUID.randomUUID(), ComplaintStatus.PENDING, PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
    }

    @Test
    void respondentSeesFinalResolutionAndFaultParty() {
        UUID respondentId = UUID.randomUUID();
        Complaint complaint = complaintWithParticipants(ComplaintStatus.RESOLVED, respondentId);
        complaint.setResolution("The tutor missed the lesson");
        complaint.setFaultParty(ComplaintFaultParty.RESPONDENT);
        when(complaintRepository.findDetailedById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaint.getId())))
                .thenReturn(List.of());

        var response = service.findAgainstMeById(respondentId, complaint.getId());

        assertThat(response.resolution()).isEqualTo("The tutor missed the lesson");
        assertThat(response.faultParty()).isEqualTo(ComplaintFaultParty.RESPONDENT);
    }

    @Test
    void complainantCannotUseRespondentEndpoints() {
        Complaint complaint = complaintWithParticipants(ComplaintStatus.IN_REVIEW, UUID.randomUUID());
        when(complaintRepository.findDetailedById(complaint.getId())).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.findAgainstMeById(userId, complaint.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("COMPLAINT_NOT_FOUND"));
    }

    @Test
    void respondentCannotSeePendingComplaint() {
        UUID respondentId = UUID.randomUUID();
        Complaint complaint = complaintWithParticipants(ComplaintStatus.PENDING, respondentId);
        when(complaintRepository.findDetailedById(complaint.getId())).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.findAgainstMeById(respondentId, complaint.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("COMPLAINT_NOT_FOUND"));
    }

    @Test
    void outsiderCannotSeeComplaintAsRespondent() {
        Complaint complaint = complaintWithParticipants(ComplaintStatus.IN_REVIEW, UUID.randomUUID());
        when(complaintRepository.findDetailedById(complaint.getId())).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.findAgainstMeById(UUID.randomUUID(), complaint.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("COMPLAINT_NOT_FOUND"));
    }

    @Test
    void respondentSubmitsOneResponseWithEvidence() {
        UUID respondentId = UUID.randomUUID();
        Complaint complaint = complaintWithParticipants(ComplaintStatus.IN_REVIEW, respondentId);
        when(complaintRepository.findByIdForUpdate(complaint.getId())).thenReturn(Optional.of(complaint));
        when(complaintRepository.saveAndFlush(complaint)).thenReturn(complaint);
        when(evidenceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceRepository.findAllByComplaint_IdInOrderByCreatedAtAsc(List.of(complaint.getId())))
                .thenReturn(List.of());

        var response = service.respond(respondentId, complaint.getId(), respondentRequest());

        assertThat(response.respondentResponse()).isEqualTo("I taught the lesson online");
        assertThat(response.respondentRespondedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 3, 0));
        org.mockito.ArgumentCaptor<List<Evidence>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(evidenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(value -> assertThat(value.getUploadedBy().getId()).isEqualTo(respondentId));
    }

    @Test
    void respondentCannotRespondTwice() {
        UUID respondentId = UUID.randomUUID();
        Complaint complaint = complaintWithParticipants(ComplaintStatus.IN_REVIEW, respondentId);
        complaint.setRespondentResponse("First response");
        complaint.setRespondentRespondedAt(LocalDateTime.of(2026, 10, 14, 8, 0));
        when(complaintRepository.findByIdForUpdate(complaint.getId())).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.respond(respondentId, complaint.getId(), respondentRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("RESPONDENT_RESPONSE_ALREADY_SUBMITTED");
                });
    }

    @Test
    void respondentCannotRespondAfterDecision() {
        UUID respondentId = UUID.randomUUID();
        Complaint complaint = complaintWithParticipants(ComplaintStatus.REJECTED, respondentId);
        when(complaintRepository.findByIdForUpdate(complaint.getId())).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> service.respond(respondentId, complaint.getId(), respondentRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("RESPONDENT_RESPONSE_NOT_ALLOWED");
                });
    }

    private ComplaintRespondentResponseRequest respondentRequest() {
        return new ComplaintRespondentResponseRequest("  I taught the lesson online  ",
                List.of(new ComplaintCreateRequest.Evidence("https://cdn.example.com/meet.png", "image/png")));
    }

    // The current user (userId) is the tutor who complains; the student is the respondent.
    private Complaint complaintWithParticipants(ComplaintStatus status, UUID studentUserId) {
        User studentUser = User.builder().email("student@example.com").build();
        studentUser.setId(studentUserId);
        Student student = Student.builder().user(studentUser).build();
        Tutor tutor = Tutor.builder().user(user()).build();
        Contract contract = Contract.builder().student(student).tutor(tutor).build();
        contract.setId(contractId);
        Complaint complaint = Complaint.builder()
                .user(user())
                .contract(contract)
                .title("Student absent")
                .content("The student did not join the lesson")
                .category(ComplaintCategory.ABSENCE)
                .status(status)
                .build();
        complaint.setId(UUID.randomUUID());
        return complaint;
    }

    private ComplaintCreateRequest request() {
        return new ComplaintCreateRequest(contractId, ComplaintCategory.ABSENCE,
                "Schedule issue", "Lesson was cancelled without notice",
                List.of(new ComplaintCreateRequest.Evidence(
                        "https://cdn.example.com/evidence.png", "image/png")));
    }

    private ComplaintUpdateRequest updateRequest() {
        return new ComplaintUpdateRequest(ComplaintCategory.LATE_OR_SHORT, "Updated schedule issue", "Updated content",
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
                        complaint.getCategory(),
                        complaint.getStatus(),
                        complaint.getResolution(),
                        complaint.getFaultParty(),
                        complaint.getResolvedAt(),
                        complaint.getRespondentResponse(),
                        complaint.getRespondentRespondedAt(),
                        evidenceResponses,
                        complaint.getCreatedAt(),
                        complaint.getUpdatedAt());
            }

            @Override
            public ComplaintResponse.Evidence toEvidenceResponse(Evidence evidence) {
                return new ComplaintResponse.Evidence(evidence.getId(), evidence.getFileUrl(), evidence.getFileType(),
                        evidence.getUploadedBy() == null ? null : evidence.getUploadedBy().getId());
            }
        };
    }
}
