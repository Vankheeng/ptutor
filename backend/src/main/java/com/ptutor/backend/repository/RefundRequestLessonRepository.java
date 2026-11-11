package com.ptutor.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.RefundRequestLesson;
import com.ptutor.backend.entity.enums.RefundRequestStatus;

public interface RefundRequestLessonRepository extends JpaRepository<RefundRequestLesson, UUID> {

    @EntityGraph(attributePaths = { "lesson" })
    List<RefundRequestLesson> findAllByRefundRequest_IdOrderByLesson_DateAscLesson_StartTimeAsc(UUID refundRequestId);

    long countByRefundRequest_Id(UUID refundRequestId);

    boolean existsByLesson_IdAndRefundRequest_StatusIn(UUID lessonId, Collection<RefundRequestStatus> statuses);

    /**
     * Lessons among {@code lessonIds} that an open refund request currently disputes.
     */
    @Query("""
            select disputed.lesson.id
            from RefundRequestLesson disputed
            where disputed.lesson.id in :lessonIds
              and disputed.refundRequest.status in :statuses
            """)
    List<UUID> findHeldLessonIds(
            @Param("lessonIds") Collection<UUID> lessonIds,
            @Param("statuses") Collection<RefundRequestStatus> statuses);
}
