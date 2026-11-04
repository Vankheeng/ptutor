package com.ptutor.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.SupportRequestAnswerRequest;
import com.ptutor.backend.dto.request.SupportRequestCreateRequest;
import com.ptutor.backend.dto.response.AdminSupportRequestDetailResponse;
import com.ptutor.backend.dto.response.AdminSupportRequestSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.SupportRequestResponse;
import com.ptutor.backend.dto.response.SupportRequestStatsResponse;
import com.ptutor.backend.entity.Employee;
import com.ptutor.backend.entity.SupportRequest;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.EmployeeJobFunction;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.SupportRequestSource;
import com.ptutor.backend.entity.enums.SupportRequestStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.SupportRequestRepository;
import com.ptutor.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Yêu cầu hỗ trợ: người dùng gửi câu hỏi cho nhân viên CUSTOMER_SERVICE khi trợ lý không trả lời được
 * (docs/support-chatbot/14-yeu-cau-ho-tro.md).
 *
 * <p>Vòng đời (mục 14.3): OPEN → IN_PROGRESS → ANSWERED → CLOSED; OPEN → CANCELLED do người dùng hủy;
 * IN_PROGRESS → OPEN khi nhân viên trả lại hàng chờ. Mọi chuyển trạng thái khóa dòng ({@code findByIdForUpdate}) để
 * hai nhân viên không cùng nhận một yêu cầu.
 *
 * <p>Đoạn hội thoại chỉ lấy khi người dùng tự chọn đính kèm, qua {@link AssistantProxyService} với chính danh tính
 * người dùng: assistant-service tự kiểm quyền sở hữu cuộc trò chuyện và trả bản đã che thông tin cá nhân.
 */
@Service
@RequiredArgsConstructor
public class SupportRequestService {

    static final int MAX_OPEN_PER_USER = 5;
    static final int MAX_PER_DAY = 10;
    static final int AUTO_CLOSE_DAYS = 7;
    static final int EXCERPT_RETENTION_DAYS = 365;
    static final int STATS_WINDOW_DAYS = 30;
    private static final int HISTORY_LIMIT = 10;

    private static final Set<SupportRequestStatus> QUEUE_DEFAULT =
            EnumSet.of(SupportRequestStatus.OPEN, SupportRequestStatus.IN_PROGRESS);

    private final SupportRequestRepository supportRequestRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AssistantProxyService assistantProxyService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ------------------------------------------------------------------ người dùng

    @Transactional
    public SupportRequestResponse create(AssistantProxyService.Caller caller, SupportRequestCreateRequest request) {
        UUID userId = caller.userId();
        String question = request.question() == null ? "" : request.question().strip();
        if (question.isEmpty() || question.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUPPORT_QUESTION",
                    "Question must contain between 1 and 2000 characters");
        }
        enforceLimits(userId);
        boolean attach = request.attachConversation();
        if (attach && (request.conversationId() == null || request.messageId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONVERSATION_REQUIRED",
                    "conversationId and messageId are required to attach the conversation");
        }
        SupportRequest previous = request.previousRequestId() == null ? null
                : supportRequestRepository.findByIdAndUser_Id(request.previousRequestId(), userId)
                        .orElseThrow(() -> notFound(request.previousRequestId()));
        User user = userRepository.getReferenceById(userId);

        SupportRequest entity = SupportRequest.builder()
                .code(String.format("HT-%06d", supportRequestRepository.nextCodeNumber()))
                .user(user)
                .userRole(caller.role())
                .question(question)
                .source(request.source())
                .conversationId(request.conversationId())
                .messageId(request.messageId())
                .attachConversation(attach)
                .conversationExcerpt(attach ? fetchExcerpt(caller, request.conversationId(), request.messageId()) : null)
                .pagePath(normalizeOptional(request.pagePath()))
                .status(SupportRequestStatus.OPEN)
                .previousRequest(previous)
                .build();
        SupportRequest saved = supportRequestRepository.save(entity);
        notifyCustomerService(saved);
        return toUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportRequestResponse> findMine(UUID userId, SupportRequestStatus status, Pageable pageable) {
        Page<SupportRequest> page = supportRequestRepository.findMine(userId, status, pageable);
        return PageResponse.from(page, page.getContent().stream().map(SupportRequestService::toUserResponse).toList());
    }

    @Transactional(readOnly = true)
    public SupportRequestResponse findMine(UUID userId, UUID requestId) {
        return toUserResponse(findOwned(userId, requestId));
    }

    @Transactional
    public SupportRequestResponse cancel(UUID userId, UUID requestId) {
        SupportRequest request = lockOwned(userId, requestId);
        requireStatus(request, SupportRequestStatus.OPEN, "Only an OPEN support request can be cancelled");
        request.setStatus(SupportRequestStatus.CANCELLED);
        request.setClosedAt(now());
        return toUserResponse(request);
    }

    @Transactional
    public SupportRequestResponse close(UUID userId, UUID requestId) {
        SupportRequest request = lockOwned(userId, requestId);
        requireStatus(request, SupportRequestStatus.ANSWERED, "Only an ANSWERED support request can be closed");
        request.setStatus(SupportRequestStatus.CLOSED);
        request.setClosedAt(now());
        return toUserResponse(request);
    }

    // ------------------------------------------------------------------ nhân viên

    @Transactional(readOnly = true)
    public PageResponse<AdminSupportRequestSummaryResponse> findQueue(
            UUID staffUserId, SupportRequestStatus status, boolean assignedToMe, String role,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Set<SupportRequestStatus> statuses = status == null ? QUEUE_DEFAULT : EnumSet.of(status);
        UUID assigneeId = assignedToMe ? findStaff(staffUserId).getId() : null;
        Page<SupportRequest> page = supportRequestRepository.findQueue(
                statuses, assigneeId, normalizeOptional(role), from, to, pageable);
        return PageResponse.from(page, page.getContent().stream().map(SupportRequestService::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public AdminSupportRequestDetailResponse findForStaff(UUID staffUserId, UUID requestId) {
        SupportRequest request = supportRequestRepository.findById(requestId).orElseThrow(() -> notFound(requestId));
        return toDetail(request, staffUserId);
    }

    @Transactional
    public AdminSupportRequestDetailResponse assign(UUID staffUserId, UUID requestId) {
        Employee employee = findStaff(staffUserId);
        SupportRequest request = lockAny(requestId);
        requireStatus(request, SupportRequestStatus.OPEN, "Only an OPEN support request can be assigned");
        request.setStatus(SupportRequestStatus.IN_PROGRESS);
        request.setAssignedEmployee(employee);
        request.setAssignedAt(now());
        return toDetail(request, staffUserId);
    }

    @Transactional
    public AdminSupportRequestDetailResponse release(UUID staffUserId, UUID requestId) {
        SupportRequest request = lockAny(requestId);
        requireStatus(request, SupportRequestStatus.IN_PROGRESS, "Only an IN_PROGRESS support request can be released");
        requireAssignee(request, staffUserId);
        request.setStatus(SupportRequestStatus.OPEN);
        request.setAssignedEmployee(null);
        request.setAssignedAt(null);
        return toDetail(request, staffUserId);
    }

    @Transactional
    public AdminSupportRequestDetailResponse answer(UUID staffUserId, UUID requestId,
                                                    SupportRequestAnswerRequest body) {
        SupportRequest request = lockAny(requestId);
        requireStatus(request, SupportRequestStatus.IN_PROGRESS, "Only an IN_PROGRESS support request can be answered");
        requireAssignee(request, staffUserId);
        request.setAnswer(body.answer().strip());
        request.setStatus(SupportRequestStatus.ANSWERED);
        request.setAnsweredAt(now());
        eventPublisher.publishEvent(NotificationDomainEvent.of(request.getUser().getId(),
                NotificationEventType.SUPPORT_REQUEST_ANSWERED, NotificationReferenceType.SUPPORT_REQUEST,
                request.getId(), Map.of("code", request.getCode())));
        return toDetail(request, staffUserId);
    }

    @Transactional(readOnly = true)
    public SupportRequestStatsResponse stats(AssistantProxyService.Caller caller) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SupportRequestStatus value : SupportRequestStatus.values()) {
            byStatus.put(value.name(), 0L);
        }
        supportRequestRepository.countByStatus().forEach(row -> byStatus.put(row[0].toString(), (Long) row[1]));
        Map<String, Long> bySource = new LinkedHashMap<>();
        supportRequestRepository.countBySource().forEach(row -> bySource.put(row[0].toString(), (Long) row[1]));

        // createdAt do Hibernate ghi theo múi giờ JVM, assignedAt/answeredAt do service ghi theo UTC (clock) — trừ
        // trực tiếp sẽ lệch đúng độ lệch múi giờ. Đổi createdAt về UTC trước khi tính.
        List<Long> toAssign = new ArrayList<>();
        List<Long> toAnswer = new ArrayList<>();
        LocalDateTime since = LocalDateTime.now().minusDays(STATS_WINDOW_DAYS);
        for (SupportRequest request : supportRequestRepository.findAnsweredSince(since)) {
            LocalDateTime createdUtc = TimeZones.jvmToUtc(request.getCreatedAt());
            if (request.getAssignedAt() != null) {
                toAssign.add(Math.max(0, Duration.between(createdUtc, request.getAssignedAt()).toMinutes()));
            }
            toAnswer.add(Math.max(0, Duration.between(createdUtc, request.getAnsweredAt()).toMinutes()));
        }
        return new SupportRequestStatsResponse(byStatus, bySource, STATS_WINDOW_DAYS,
                percentile(toAssign, 50), percentile(toAssign, 95), percentile(toAnswer, 50), percentile(toAnswer, 95),
                refusalFollowUp(caller, since));
    }

    /**
     * Mẫu số (lượt từ chối / một phần) lấy từ assistant-service, tử số (yêu cầu theo nguồn) đếm ở đây, cùng cửa sổ thời
     * gian. Mốc {@code since} theo giờ JVM (như {@code createdAt}) được đổi sang UTC vì assistant lưu theo UTC.
     * Trợ lý không trả lời được → null: trang thống kê vẫn hiện các số còn lại.
     */
    private SupportRequestStatsResponse.RefusalFollowUp refusalFollowUp(AssistantProxyService.Caller caller,
                                                                         LocalDateTime since) {
        JsonNode counts;
        try {
            // Gửi dạng UTC có "Z": dấu "+" của độ lệch múi giờ trong query string bị đọc thành khoảng trắng.
            counts = assistantProxyService.get(caller, "/v1/admin/refusal-counts", Map.of(
                    "from", since.atZone(ZoneId.systemDefault()).toInstant().toString(),
                    "to", Instant.now().toString()));
        } catch (ApiException exception) {
            return null;
        }
        Map<String, Long> requests = new LinkedHashMap<>();
        supportRequestRepository.countBySourceSince(since)
                .forEach(row -> requests.put(row[0].toString(), (Long) row[1]));
        long abstainTurns = counts.path("abstain").asLong(0);
        long partialTurns = counts.path("partial").asLong(0);
        long afterAbstain = requests.getOrDefault(SupportRequestSource.ASSISTANT_ABSTAIN.name(), 0L);
        long afterPartial = requests.getOrDefault(SupportRequestSource.ASSISTANT_PARTIAL.name(), 0L);
        return new SupportRequestStatsResponse.RefusalFollowUp(abstainTurns, partialTurns, afterAbstain, afterPartial,
                ratio(afterAbstain, abstainTurns), ratio(afterPartial, partialTurns));
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    // ------------------------------------------------------------------ job định kỳ

    /** Yêu cầu đã trả lời quá 7 ngày mà người dùng chưa xác nhận → tự đóng (mục 14.3). */
    @Transactional
    public int autoCloseAnswered() {
        LocalDateTime now = now();
        return supportRequestRepository.closeAnsweredBefore(now.minusDays(AUTO_CLOSE_DAYS), now,
                SupportRequestStatus.ANSWERED, SupportRequestStatus.CLOSED);
    }

    /** Xoá đoạn hội thoại đính kèm 365 ngày sau khi đóng; giữ câu hỏi và câu trả lời (mục 14.8). */
    @Transactional
    public int purgeOldExcerpts() {
        LocalDateTime now = now();
        return supportRequestRepository.purgeExcerptsClosedBefore(now.minusDays(EXCERPT_RETENTION_DAYS), now);
    }

    // ------------------------------------------------------------------ nội bộ

    private void enforceLimits(UUID userId) {
        if (supportRequestRepository.countByUser_IdAndStatus(userId, SupportRequestStatus.OPEN) >= MAX_OPEN_PER_USER) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPORT_REQUEST_OPEN_LIMIT",
                    "You already have " + MAX_OPEN_PER_USER + " open support requests");
        }
        // createdAt do Hibernate ghi theo múi giờ JVM nên mốc "24 giờ trước" cũng lấy theo múi giờ JVM.
        LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
        if (supportRequestRepository.countByUser_IdAndCreatedAtAfter(userId, dayAgo) >= MAX_PER_DAY) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SUPPORT_REQUEST_DAILY_LIMIT",
                    "You can send at most " + MAX_PER_DAY + " support requests per day");
        }
    }

    private String fetchExcerpt(AssistantProxyService.Caller caller, UUID conversationId, UUID messageId) {
        JsonNode excerpt = assistantProxyService.get(caller, "/v1/conversations/" + conversationId + "/excerpt",
                Map.of("upTo", messageId));
        return objectMapper.writeValueAsString(excerpt.path("turns"));
    }

    private void notifyCustomerService(SupportRequest request) {
        for (UUID recipient : employeeRepository.findActiveUserIdsForJobFunction(EmployeeJobFunction.CUSTOMER_SERVICE)) {
            eventPublisher.publishEvent(NotificationDomainEvent.of(recipient,
                    NotificationEventType.SUPPORT_REQUEST_RECEIVED, NotificationReferenceType.SUPPORT_REQUEST,
                    request.getId(), Map.of("code", request.getCode())));
        }
    }

    private SupportRequest findOwned(UUID userId, UUID requestId) {
        return supportRequestRepository.findByIdAndUser_Id(requestId, userId).orElseThrow(() -> notFound(requestId));
    }

    /** Khoá dòng rồi mới kiểm chủ sở hữu; yêu cầu của người khác trả 404 như không tồn tại. */
    private SupportRequest lockOwned(UUID userId, UUID requestId) {
        SupportRequest request = lockAny(requestId);
        if (!Objects.equals(request.getUser().getId(), userId)) {
            throw notFound(requestId);
        }
        return request;
    }

    private SupportRequest lockAny(UUID requestId) {
        return supportRequestRepository.findByIdForUpdate(requestId).orElseThrow(() -> notFound(requestId));
    }

    private Employee findStaff(UUID userId) {
        return employeeRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_PROFILE_REQUIRED",
                        "An employee profile is required to handle support requests"));
    }

    private static void requireStatus(SupportRequest request, SupportRequestStatus expected, String message) {
        if (request.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_SUPPORT_REQUEST_STATUS_TRANSITION", message);
        }
    }

    private static void requireAssignee(SupportRequest request, UUID staffUserId) {
        Employee assignee = request.getAssignedEmployee();
        if (assignee == null || assignee.getUser() == null || !Objects.equals(assignee.getUser().getId(), staffUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUPPORT_REQUEST_NOT_ASSIGNED_TO_YOU",
                    "Only the assigned employee can handle this support request");
        }
    }

    private static ApiException notFound(UUID requestId) {
        return new ApiException(HttpStatus.NOT_FOUND, "SUPPORT_REQUEST_NOT_FOUND",
                "Support request " + requestId + " was not found");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    static Long percentile(List<Long> values, int percent) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String fullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().strip();
        String lastName = user.getLastName() == null ? "" : user.getLastName().strip();
        return (firstName + " " + lastName).strip();
    }

    static SupportRequestResponse toUserResponse(SupportRequest request) {
        SupportRequest previous = request.getPreviousRequest();
        return new SupportRequestResponse(request.getId(), request.getCode(), request.getQuestion(),
                request.getSource(), request.getStatus(), request.getAnswer(), request.isAttachConversation(),
                previous == null ? null : previous.getId(), previous == null ? null : previous.getCode(),
                request.getCreatedAt(), request.getAnsweredAt(), request.getClosedAt());
    }

    private static AdminSupportRequestSummaryResponse toSummary(SupportRequest request) {
        Employee assignee = request.getAssignedEmployee();
        return new AdminSupportRequestSummaryResponse(request.getId(), request.getCode(), request.getQuestion(),
                request.getUserRole(), fullName(request.getUser()), request.getSource(), request.getStatus(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : fullName(assignee.getUser()),
                request.isAttachConversation(), request.getCreatedAt(), request.getAnsweredAt());
    }

    private AdminSupportRequestDetailResponse toDetail(SupportRequest request, UUID staffUserId) {
        User user = request.getUser();
        Employee assignee = request.getAssignedEmployee();
        boolean mine = assignee != null && assignee.getUser() != null
                && Objects.equals(assignee.getUser().getId(), staffUserId);
        List<AdminSupportRequestDetailResponse.History> history = supportRequestRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(other -> !other.getId().equals(request.getId()))
                .limit(HISTORY_LIMIT)
                .map(other -> new AdminSupportRequestDetailResponse.History(other.getId(), other.getCode(),
                        other.getQuestion(), other.getStatus(), other.getCreatedAt()))
                .toList();
        JsonNode excerpt = request.getConversationExcerpt() == null ? null
                : objectMapper.readTree(request.getConversationExcerpt());
        SupportRequest previous = request.getPreviousRequest();
        return new AdminSupportRequestDetailResponse(request.getId(), request.getCode(), request.getQuestion(),
                request.getUserRole(),
                new AdminSupportRequestDetailResponse.Requester(user.getId(), fullName(user), user.getEmail()),
                request.getSource(), request.getStatus(), request.getPagePath(), request.isAttachConversation(),
                excerpt, request.getAnswer(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : fullName(assignee.getUser()), mine,
                previous == null ? null : previous.getId(), history,
                request.getCreatedAt(), request.getAssignedAt(), request.getAnsweredAt(), request.getClosedAt());
    }

    /** Đổi thời điểm ghi theo múi giờ JVM sang UTC (cùng hệ với {@code LocalDateTime.now(clock)}). */
    static final class TimeZones {
        private TimeZones() {
        }

        static LocalDateTime jvmToUtc(LocalDateTime jvmLocal) {
            return jvmLocal.atZone(java.time.ZoneId.systemDefault())
                    .withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
        }
    }
}
