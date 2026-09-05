package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;

import static org.mockito.Mockito.lenient;

import com.ptutor.backend.dto.enums.UserRole;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Subject;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.ApplicationStatus;
import com.ptutor.backend.entity.enums.CatalogStatus;
import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.TeachingRequestMapper;
import com.ptutor.backend.repository.DistrictRepository;
import com.ptutor.backend.repository.GradeRepository;
import com.ptutor.backend.repository.StudentTutorRequestRepository;
import com.ptutor.backend.repository.SubjectRepository;
import com.ptutor.backend.repository.TutorRepository;
import com.ptutor.backend.dto.request.TeachingRequestRequest;
import com.ptutor.backend.dto.response.TeachingRequestResponse;
import com.ptutor.backend.repository.TeachingRequestRepository;
import com.ptutor.backend.repository.TeachingRequestStudentRequestCount;

@ExtendWith(MockitoExtension.class)
class TeachingRequestServiceTest {

    @Mock TeachingRequestRepository teachingRequestRepository;
    @Mock StudentTutorRequestRepository studentTutorRequestRepository;
    @Mock TutorRepository tutorRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock GradeRepository gradeRepository;
    @Mock DistrictRepository districtRepository;

    private TeachingRequestService service;
    private UUID userId;
    private UUID tutorId;
    private UUID subjectId;
    private UUID gradeId;

    @BeforeEach
    void setUp() {
        service = new TeachingRequestService(
                teachingRequestRepository,
                studentTutorRequestRepository,
                Mappers.getMapper(TeachingRequestMapper.class),
                tutorRepository,
                subjectRepository,
                gradeRepository,
                districtRepository);
        userId = UUID.randomUUID();
        tutorId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        gradeId = UUID.randomUUID();
        lenient().when(tutorRepository.findByUser_Id(userId)).thenReturn(Optional.of(tutor()));
        lenient().when(gradeRepository.findById(gradeId)).thenReturn(Optional.of(grade()));
        lenient().when(studentTutorRequestRepository.countByTeachingRequestIds(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void createWithCatalogSubjectWaitsForPayment() {
        Subject subject = subject();
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(teachingRequestRepository.saveAndFlush(any(TeachingRequest.class)))
                .thenAnswer(invocation -> {
                    TeachingRequest request = invocation.getArgument(0);
                    request.setId(UUID.randomUUID());
                    return request;
                });

        TeachingRequestResponse response = service.create(userId, requestWithSubject());

        assertThat(response.status()).isEqualTo(RequestStatus.DRAFT);
        assertThat(response.subjectId()).isEqualTo(subjectId);
        assertThat(response.customSubjectName()).isNull();
    }

    @Test
    void createWithCustomSubjectWaitsForPaymentBeforeReview() {
        when(teachingRequestRepository.saveAndFlush(any(TeachingRequest.class)))
                .thenAnswer(invocation -> {
                    TeachingRequest request = invocation.getArgument(0);
                    request.setId(UUID.randomUUID());
                    return request;
                });

        TeachingRequestResponse response = service.create(userId, requestWithCustomSubject());

        assertThat(response.status()).isEqualTo(RequestStatus.DRAFT);
        assertThat(response.subjectId()).isNull();
        assertThat(response.customSubjectName()).isEqualTo("Advanced Robotics");
        verify(subjectRepository, never()).findById(any());
    }

    @Test
    void findMineIncludesStudentRequestCounts() {
        TeachingRequest request = existingRequest(RequestStatus.OPEN);
        TeachingRequestStudentRequestCount count = new TeachingRequestStudentRequestCount() {
            @Override
            public UUID getTeachingRequestId() {
                return request.getId();
            }

            @Override
            public long getStudentRequestCount() {
                return 3;
            }

            @Override
            public long getPendingStudentRequestCount() {
                return 2;
            }
        };
        when(teachingRequestRepository.findAllByTutor_IdOrderByCreatedAtDesc(tutorId))
                .thenReturn(List.of(request));
        when(studentTutorRequestRepository.countByTeachingRequestIds(
                List.of(request.getId()), ApplicationStatus.PENDING))
                .thenReturn(List.of(count));

        TeachingRequestResponse response = service.findMine(userId, null).getFirst();

        assertThat(response.studentRequestCount()).isEqualTo(3);
        assertThat(response.pendingStudentRequestCount()).isEqualTo(2);
    }

    @Test
    void activatePaidCatalogSubjectAsOpen() {
        TeachingRequest request = existingRequest(RequestStatus.DRAFT);
        request.setSubject(subject());
        when(teachingRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.activateAfterPayment(request.getId());

        assertThat(response.status()).isEqualTo(RequestStatus.OPEN);
        verify(teachingRequestRepository).findById(request.getId());
    }

    @Test
    void activatePaidCustomSubjectAsPendingReview() {
        TeachingRequest request = existingRequest(RequestStatus.DRAFT);
        request.setCustomSubjectName("Advanced Robotics");
        when(teachingRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.activateAfterPayment(request.getId());

        assertThat(response.status()).isEqualTo(RequestStatus.PENDING_REVIEW);
        verify(teachingRequestRepository).findById(request.getId());
    }

    @Test
    void updateDraftKeepsDraftStatus() {
        TeachingRequest request = existingRequest(RequestStatus.DRAFT);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject()));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.update(userId, request.getId(), requestWithSubject());

        assertThat(response.status()).isEqualTo(RequestStatus.DRAFT);
    }

    @Test
    void updateFromCatalogSubjectToCustomSubjectReturnsToReview() {
        TeachingRequest request = existingRequest(RequestStatus.OPEN);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.update(userId, request.getId(), requestWithCustomSubject());

        assertThat(response.status()).isEqualTo(RequestStatus.PENDING_REVIEW);
        assertThat(request.getCustomSubjectName()).isEqualTo("Advanced Robotics");
        assertThat(request.getSubject()).isNull();
    }

    @Test
    void updateStatusAllowsOnlyOpenAndClosed() {
        TeachingRequest request = existingRequest(RequestStatus.OPEN);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.updateStatus(userId, request.getId(), RequestStatus.CLOSED);

        assertThat(response.status()).isEqualTo(RequestStatus.CLOSED);
    }

    @Test
    void updateStatusRejectsMatchedStatus() {
        TeachingRequest request = existingRequest(RequestStatus.OPEN);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.updateStatus(userId, request.getId(), RequestStatus.MATCHED))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TEACHING_REQUEST_STATUS_TRANSITION");
                });
    }

    @Test
    void updateStatusCannotPublishDraftRequest() {
        TeachingRequest request = existingRequest(RequestStatus.DRAFT);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.updateStatus(userId, request.getId(), RequestStatus.OPEN))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TEACHING_REQUEST_STATUS_TRANSITION");
                });
    }

    @Test
    void cancelMovesPendingRequestToCancelled() {
        TeachingRequest request = existingRequest(RequestStatus.PENDING_REVIEW);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.cancel(userId, request.getId());

        assertThat(response.status()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void cancelMovesDraftRequestToCancelled() {
        TeachingRequest request = existingRequest(RequestStatus.DRAFT);
        when(teachingRequestRepository.findByIdAndTutor_Id(request.getId(), tutorId))
                .thenReturn(Optional.of(request));
        when(teachingRequestRepository.saveAndFlush(request)).thenReturn(request);

        TeachingRequestResponse response = service.cancel(userId, request.getId());

        assertThat(response.status()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void staffCanSeeAllStatusesButStudentOnlySeesOpenRequests() {
        TeachingRequest open = existingRequest(RequestStatus.OPEN);
        TeachingRequest draft = existingRequest(RequestStatus.DRAFT);
        TeachingRequest pending = existingRequest(RequestStatus.PENDING_REVIEW);
        when(teachingRequestRepository.findAllByStatusOrderByCreatedAtDesc(RequestStatus.OPEN))
                .thenReturn(List.of(open));
        when(teachingRequestRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(open, draft, pending));

        assertThat(service.findVisible(UserRole.STUDENT)).hasSize(1)
                .extracting(TeachingRequestResponse::status).containsExactly(RequestStatus.OPEN);
        assertThat(service.findVisible(UserRole.EMPLOYEE)).hasSize(3);
        verify(teachingRequestRepository).findAllByStatusOrderByCreatedAtDesc(RequestStatus.OPEN);
        verify(teachingRequestRepository).findAllByOrderByCreatedAtDesc();
    }

    private TeachingRequestRequest requestWithSubject() {
        return new TeachingRequestRequest(
                subjectId, null, List.of(gradeId), "Find math students", null, 2, List.of(), null,
                new BigDecimal("150000"), TeachingMode.ONLINE, null, "Math tutoring", List.of());
    }

    private TeachingRequestRequest requestWithCustomSubject() {
        return new TeachingRequestRequest(
                null, " Advanced Robotics ", List.of(gradeId), "Find robotics students", null, 2, List.of(), null,
                new BigDecimal("200000"), TeachingMode.ONLINE, null, "Robotics tutoring", List.of());
    }

    private TeachingRequest existingRequest(RequestStatus status) {
        TeachingRequest request = TeachingRequest.builder()
                .tutor(tutor())
                .title("Existing request")
                .quantity(1)
                .expectedPrice(new BigDecimal("100000"))
                .teachingMode(TeachingMode.ONLINE)
                .status(status)
                .build();
        request.setId(UUID.randomUUID());
        return request;
    }

    private Tutor tutor() {
        Tutor tutor = Tutor.builder().build();
        tutor.setId(tutorId);
        return tutor;
    }

    private Subject subject() {
        Subject subject = Subject.builder().status(CatalogStatus.ACTIVE).name("Mathematics").build();
        subject.setId(subjectId);
        return subject;
    }

    private Grade grade() {
        Grade grade = Grade.builder().status(CatalogStatus.ACTIVE).name("Grade 9").build();
        grade.setId(gradeId);
        return grade;
    }
}
