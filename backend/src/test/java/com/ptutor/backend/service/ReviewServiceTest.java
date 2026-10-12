package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.ReviewCreateRequest;
import com.ptutor.backend.dto.response.ReviewResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.Review;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.event.TutorScoreRecalculationRequestedEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ReviewMapper;
import com.ptutor.backend.repository.ContractRepository;
import com.ptutor.backend.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ContractRepository contractRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private ReviewService service;
    private UUID userId;
    private UUID contractId;
    private UUID tutorId;
    private Student student;
    private Tutor tutor;

    @BeforeEach
    void setUp() {
        service = new ReviewService(
                reviewRepository, Mappers.getMapper(ReviewMapper.class), contractRepository, eventPublisher);
        userId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        student = Student.builder().build();
        student.setId(UUID.randomUUID());
        tutor = Tutor.builder().build();
        tutor.setId(tutorId);
    }

    @Test
    void createsReviewForCompletedContractAndRequestsScoreRecalculation() {
        Contract contract = contract(ContractStatus.COMPLETED);
        UUID reviewId = UUID.randomUUID();
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId))
                .thenReturn(Optional.of(contract));
        when(reviewRepository.existsByContract_Id(contractId)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review value = invocation.getArgument(0);
            value.setId(reviewId);
            return value;
        });

        ReviewResponse response = service.createForContract(
                userId, contractId, new ReviewCreateRequest(4, "  Dạy dễ hiểu  "));

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(student);
        assertThat(saved.getValue().getTutor()).isSameAs(tutor);
        assertThat(saved.getValue().getContract()).isSameAs(contract);
        assertThat(response.id()).isEqualTo(reviewId);
        assertThat(response.contractId()).isEqualTo(contractId);
        assertThat(response.tutorId()).isEqualTo(tutorId);
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Dạy dễ hiểu");
        verify(eventPublisher).publishEvent(new TutorScoreRecalculationRequestedEvent(Set.of(tutorId)));
    }

    @Test
    void storesBlankCommentAsNull() {
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId))
                .thenReturn(Optional.of(contract(ContractStatus.COMPLETED)));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = service.createForContract(userId, contractId, new ReviewCreateRequest(5, "   "));

        assertThat(response.comment()).isNull();
    }

    @Test
    void rejectsContractThatDoesNotBelongToStudent() {
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createForContract(userId, contractId, new ReviewCreateRequest(5, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_NOT_FOUND");
                });
        verifyNothingSavedOrPublished();
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsContractThatIsNotCompleted(ContractStatus status) {
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId))
                .thenReturn(Optional.of(contract(status)));

        assertThatThrownBy(() -> service.createForContract(userId, contractId, new ReviewCreateRequest(5, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("CONTRACT_NOT_COMPLETED");
                });
        verifyNothingSavedOrPublished();
    }

    @Test
    void rejectsSecondReviewForSameContract() {
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId))
                .thenReturn(Optional.of(contract(ContractStatus.COMPLETED)));
        when(reviewRepository.existsByContract_Id(contractId)).thenReturn(true);

        assertThatThrownBy(() -> service.createForContract(userId, contractId, new ReviewCreateRequest(5, null)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS");
                });
        verifyNothingSavedOrPublished();
    }

    @Test
    void reportsDuplicateWhenConcurrentReviewWinsTheUniqueIndex() {
        when(contractRepository.findDetailedByIdAndStudentUserId(contractId, userId))
                .thenReturn(Optional.of(contract(ContractStatus.COMPLETED)));
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("uq_reviews_contract"));

        assertThatThrownBy(() -> service.createForContract(userId, contractId, new ReviewCreateRequest(5, null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private void verifyNothingSavedOrPublished() {
        verify(reviewRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private Contract contract(ContractStatus status) {
        Contract contract = Contract.builder().student(student).tutor(tutor).status(status).build();
        contract.setId(contractId);
        return contract;
    }
}
