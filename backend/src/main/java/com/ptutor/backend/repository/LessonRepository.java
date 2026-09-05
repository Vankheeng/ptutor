package com.ptutor.backend.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.LessonStatus;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    Page<Lesson> findAllByContract_Id(UUID contractId, Pageable pageable);

    Page<Lesson> findAllByContract_IdAndStatus(UUID contractId, LessonStatus status, Pageable pageable);

    @Query("""
            select lesson
            from Lesson lesson
            where lesson.status = :status
              and (lesson.date < :deadlineDate
                   or (lesson.date = :deadlineDate and lesson.endTime <= :deadlineTime))
            """)
    List<Lesson> findAllFinishedBefore(
            @Param("status") LessonStatus status,
            @Param("deadlineDate") LocalDate deadlineDate,
            @Param("deadlineTime") LocalTime deadlineTime);

}
