package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.request.SupportRequestAnswerRequest;
import com.ptutor.backend.dto.request.SupportRequestCreateRequest;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.SupportRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.SupportRequestRepository;
import com.ptutor.backend.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Kiểm thử theo docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.10 (tầng backend). */
@ExtendWith(MockitoExtension.class)
class SupportRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");

    @Mock SupportRequestRepository supportRequestRepository;
    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock AssistantProxyService assistantProxyService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private SupportRequestService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();
    private User user;
    private Employee staff;

    @BeforeEach
    void setUp() {
        service = new SupportRequestService(supportRequestRepository, userRepository, employeeRepository,
                assistantProxyService, eventPublisher, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(userId);
        user.setFirstName("An");
        user.setLastName("Nguyen");
        User staffUser = new User();
        staffUser.setId(staffUserId);
        staff = new Employee();
        staff.setId(UUID.randomUUID());
        staff.setUser(staffUser);
        staff.setJobFunction(EmployeeJobFunction.CUSTOMER_SERVICE);
        lenient().when(supportRequestRepository.save(any())).thenAnswer(invocation -> {
            SupportRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        lenient().when(supportRequestRepository.nextCodeNumber()).thenReturn(123L);
        lenient().when(userRepository.getReferenceById(userId)).thenReturn(user);
    }

    private AssistantProxyService.Caller caller() {
        return new AssistantProxyService.Caller(userId, "STUDENT", null, "trace");
    }

    private SupportRequestCreateRequest createRequest(boolean attach) {
        return new SupportRequestCreateRequest("Vì sao tôi chưa được hoàn tiền?", UUID.randomUUID(),
                UUID.randomUUID(), attach, SupportRequestSource.ASSISTANT_ABSTAIN, "/student/wallet", null);
    }

    private SupportRequest existing(SupportRequestStatus status, User owner) {
        SupportRequest request = SupportRequest.builder().code("HT-000001").user(owner).userRole("STUDENT")
                .question("Câu hỏi").source(SupportRequestSource.MENU).status(status).build();
        request.setId(UUID.randomUUID());
        lenient().when(supportRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void createsOpenRequestWithCodeAndWithoutExcerptByDefault() {
        var response = service.create(caller(), createRequest(false));

        assertThat(response.code()).isEqualTo("HT-000123");
        assertThat(response.status()).isEqualTo(SupportRequestStatus.OPEN);
        verify(assistantProxyService, never()).get(any(), any(), anyMap());
        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getConversationExcerpt()).isNull();
        assertThat(saved.getValue().getUserRole()).isEqualTo("STUDENT");
    }

    @Test
    void attachesRedactedExcerptOnlyWhenUserAgrees() {
        when(assistantProxyService.get(any(), any(), anyMap())).thenReturn(objectMapper.readTree(
                "{\"turns\":[{\"role\":\"user\",\"content\":\"số điện thoại [đã che]\"}]}"));

        service.create(caller(), createRequest(true));

        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getConversationExcerpt()).contains("[đã che]");
        verify(assistantProxyService).get(eq(caller()), any(), anyMap());
    }

    @Test
    void notifiesCustomerServiceStaffWhenRequestArrives() {
        when(employeeRepository.findActiveUserIdsForJobFunction(EmployeeJobFunction.CUSTOMER_SERVICE))
                .thenReturn(List.of(staffUserId));

        service.create(caller(), createRequest(false));

        ArgumentCaptor<NotificationDomainEvent> event = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(NotificationEventType.SUPPORT_REQUEST_RECEIVED);
        assertThat(event.getValue().recipientUserId()).isEqualTo(staffUserId);
    }

    @Test
    void rejectsSixthOpenRequest() {
        when(supportRequestRepository.countByUser_IdAndStatus(userId, SupportRequestStatus.OPEN)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(caller(), createRequest(false)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SUPPORT_REQUEST_OPEN_LIMIT"));
        verify(supportRequestRepository, never()).save(any());
    }

    @Test
    void rejectsEleventhRequestOfTheDay() {
        when(supportRequestRepository.countByUser_IdAndCreatedAtAfter(eq(userId), any(LocalDateTime.class)))
                .thenReturn(10L);

        assertThatThrownBy(() -> service.create(caller(), createRequest(false)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void userCannotCancelSomeoneElsesRequest() {
        User other = new User();
        other.setId(otherUserId);
        SupportRequest request = existing(SupportRequestStatus.OPEN, other);

        assertThatThrownBy(() -> service.cancel(userId, request.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(request.getStatus()).isEqualTo(SupportRequestStatus.OPEN);
    }

    @Test
    void cancelOnlyWhileOpen() {
        SupportRequest open = existing(SupportRequestStatus.OPEN, user);
        assertThat(service.cancel(userId, open.getId()).status()).isEqualTo(SupportRequestStatus.CANCELLED);

        SupportRequest inProgress = existing(SupportRequestStatus.IN_PROGRESS, user);
        assertThatThrownBy(() -> service.cancel(userId, inProgress.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_SUPPORT_REQUEST_STATUS_TRANSITION"));
    }

    @Test
    void staffFlowAssignAnswerNotifiesUserThenUserCloses() {
        when(employeeRepository.findByUser_Id(staffUserId)).thenReturn(Optional.of(staff));
        SupportRequest request = existing(SupportRequestStatus.OPEN, user);

        service.assign(staffUserId, request.getId());
        assertThat(request.getStatus()).isEqualTo(SupportRequestStatus.IN_PROGRESS);
        assertThat(request.getAssignedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        var detail = service.answer(staffUserId, request.getId(), new SupportRequestAnswerRequest("  Đã kiểm tra.  "));
        assertThat(detail.status()).isEqualTo(SupportRequestStatus.ANSWERED);
        assertThat(detail.answer()).isEqualTo("Đã kiểm tra.");
        assertThat(detail.assignedToMe()).isTrue();
        ArgumentCaptor<NotificationDomainEvent> event = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(NotificationEventType.SUPPORT_REQUEST_ANSWERED);
        assertThat(event.getValue().recipientUserId()).isEqualTo(userId);

        assertThat(service.close(userId, request.getId()).status()).isEqualTo(SupportRequestStatus.CLOSED);
    }

    @Test
    void onlyAssigneeCanAnswerOrRelease() {
        SupportRequest request = existing(SupportRequestStatus.IN_PROGRESS, user);
        request.setAssignedEmployee(staff);
        UUID anotherStaff = UUID.randomUUID();

        assertThatThrownBy(() -> service.answer(anotherStaff, request.getId(), new SupportRequestAnswerRequest("x")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SUPPORT_REQUEST_NOT_ASSIGNED_TO_YOU"));
        assertThatThrownBy(() -> service.release(anotherStaff, request.getId()))
                .isInstanceOf(ApiException.class);

        service.release(staffUserId, request.getId());
        assertThat(request.getStatus()).isEqualTo(SupportRequestStatus.OPEN);
        assertThat(request.getAssignedEmployee()).isNull();
    }

    @Test
    void cannotAssignTwice() {
        when(employeeRepository.findByUser_Id(staffUserId)).thenReturn(Optional.of(staff));
        SupportRequest request = existing(SupportRequestStatus.IN_PROGRESS, user);

        assertThatThrownBy(() -> service.assign(staffUserId, request.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void nightlyJobsUseSevenAndThreeHundredSixtyFiveDayCutoffs() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        service.autoCloseAnswered();
        service.purgeOldExcerpts();

        verify(supportRequestRepository).closeAnsweredBefore(now.minusDays(7), now,
                SupportRequestStatus.ANSWERED, SupportRequestStatus.CLOSED);
        verify(supportRequestRepository).purgeExcerptsClosedBefore(now.minusDays(365), now);
    }

    @Test
    void percentileUsesNearestRank() {
        assertThat(SupportRequestService.percentile(List.of(), 50)).isNull();
        assertThat(SupportRequestService.percentile(List.of(5L, 1L, 3L, 9L), 50)).isEqualTo(3L);
        assertThat(SupportRequestService.percentile(List.of(5L, 1L, 3L, 9L), 95)).isEqualTo(9L);
    }
    @Test
    void statsRelateRequestsToAssistantRefusalsInTheSameWindow() {
        // Mục 14.9: tỉ lệ yêu cầu hỗ trợ trên lượt trợ lý từ chối / trả lời một phần, cùng cửa sổ 30 ngày.
        when(supportRequestRepository.countByStatus()).thenReturn(List.of());
        when(supportRequestRepository.countBySource()).thenReturn(List.of());
        when(supportRequestRepository.findAnsweredSince(any())).thenReturn(List.of());
        when(supportRequestRepository.countBySourceSince(any())).thenReturn(List.of(
                new Object[] {SupportRequestSource.ASSISTANT_ABSTAIN, 5L},
                new Object[] {SupportRequestSource.MENU, 9L}));
        when(assistantProxyService.get(any(), eq("/v1/admin/refusal-counts"), anyMap()))
                .thenReturn(objectMapper.readTree("{\"abstain\": 20, \"partial\": 0}"));
        AssistantProxyService.Caller staffCaller = new AssistantProxyService.Caller(staffUserId, "EMPLOYEE", null, "t");

        var followUp = service.stats(staffCaller).refusalFollowUp();

        assertThat(followUp.abstainTurns()).isEqualTo(20);
        assertThat(followUp.requestsAfterAbstain()).isEqualTo(5);
        assertThat(followUp.abstainRequestRate()).isEqualTo(0.25);
        assertThat(followUp.partialRequestRate()).isNull(); // mẫu số 0 → không có tỉ lệ, không chia cho 0
    }

    @Test
    void statsStillWorkWhenAssistantIsUnavailable() {
        when(supportRequestRepository.countByStatus()).thenReturn(List.of());
        when(supportRequestRepository.countBySource()).thenReturn(List.of());
        when(supportRequestRepository.findAnsweredSince(any())).thenReturn(List.of());
        when(assistantProxyService.get(any(), any(), anyMap()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, AssistantProxyService.UNAVAILABLE, "down"));

        var stats = service.stats(new AssistantProxyService.Caller(staffUserId, "ADMIN", null, "t"));

        assertThat(stats.refusalFollowUp()).isNull();
        assertThat(stats.windowDays()).isEqualTo(SupportRequestService.STATS_WINDOW_DAYS);
    }
}
