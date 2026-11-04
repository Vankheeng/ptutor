package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.SupportRequest;
import com.ptutor.backend.entity.enums.SupportRequestStatus;

import jakarta.persistence.LockModeType;

/** Truy vấn yêu cầu hỗ trợ (docs/support-chatbot/14-yeu-cau-ho-tro.md mục 14.5). */
public interface SupportRequestRepository extends JpaRepository<SupportRequest, UUID> {

    /** Mã hiển thị kế tiếp: HT-000001, HT-000002… */
    @Query(value = "SELECT nextval('support_request_code_seq')", nativeQuery = true)
    long nextCodeNumber();

    @Query("""
            select request from SupportRequest request
            where request.user.id = :userId and (:status is null or request.status = :status)
            order by request.createdAt desc
            """)
    Page<SupportRequest> findMine(@Param("userId") UUID userId, @Param("status") SupportRequestStatus status,
                                  Pageable pageable);

    Optional<SupportRequest> findByIdAndUser_Id(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SupportRequest request where request.id = :id")
    Optional<SupportRequest> findByIdForUpdate(@Param("id") UUID id);

    long countByUser_IdAndStatus(UUID userId, SupportRequestStatus status);

    long countByUser_IdAndCreatedAtAfter(UUID userId, LocalDateTime after);

    /** Hàng chờ của nhân viên: cũ nhất trước (14 mục 14.5). */
    @Query("""
            select request from SupportRequest request
            left join request.assignedEmployee employee
            where request.status in :statuses
              and (:assigneeId is null or employee.id = :assigneeId)
              and (:role is null or request.userRole = :role)
              and (cast(:from as timestamp) is null or request.createdAt >= :from)
              and (cast(:to as timestamp) is null or request.createdAt < :to)
            order by request.createdAt asc
            """)
    Page<SupportRequest> findQueue(@Param("statuses") Collection<SupportRequestStatus> statuses,
                                   @Param("assigneeId") UUID assigneeId, @Param("role") String role,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   Pageable pageable);

    List<SupportRequest> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Query("select request.status, count(request) from SupportRequest request group by request.status")
    List<Object[]> countByStatus();

    @Query("select request.source, count(request) from SupportRequest request group by request.source")
    List<Object[]> countBySource();

    @Query("select request from SupportRequest request where request.answeredAt is not null "
            + "and request.createdAt >= :since")
    List<SupportRequest> findAnsweredSince(@Param("since") LocalDateTime since);

    /** Job tự đóng: yêu cầu đã trả lời quá 7 ngày mà người dùng chưa bấm "Đã giải quyết". */
    @Modifying
    @Query("""
            update SupportRequest request set request.status = :closed, request.closedAt = :now, request.updatedAt = :now
            where request.status = :answered and request.answeredAt < :deadline
            """)
    int closeAnsweredBefore(@Param("deadline") LocalDateTime deadline, @Param("now") LocalDateTime now,
                            @Param("answered") SupportRequestStatus answered,
                            @Param("closed") SupportRequestStatus closed);

    /** Job xoá đoạn trích: 365 ngày sau khi đóng; câu hỏi và câu trả lời giữ lại để thống kê (14 mục 14.8). */
    @Modifying
    @Query("""
            update SupportRequest request set request.conversationExcerpt = null, request.updatedAt = :now
            where request.conversationExcerpt is not null and request.closedAt < :deadline
            """)
    int purgeExcerptsClosedBefore(@Param("deadline") LocalDateTime deadline, @Param("now") LocalDateTime now);
}
