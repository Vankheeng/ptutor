package com.ptutor.backend.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.Lesson;
import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.ContractStatus;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    Page<Lesson> findAllByContract_Id(UUID contractId, Pageable pageable);

    Page<Lesson> findAllByContract_IdAndStatus(UUID contractId, LessonStatus status, Pageable pageable);

    List<Lesson> findAllByContract_IdOrderByDateAscStartTimeAsc(UUID contractId);

    long countByContract_Student_User_Id(UUID userId);

    long countByContract_Student_User_IdAndStatus(UUID userId, LessonStatus status);

    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user",
            "contract.subject", "contract.grade"
    })
    @Query(value = """
            select lesson
            from Lesson lesson
            where lesson.contract.student.user.id = :userId
              and (:lessonStatus is null or lesson.status = :lessonStatus)
              and (:contractStatus is null or lesson.contract.status = :contractStatus)
            """,
            countQuery = """
                    select count(lesson)
                    from Lesson lesson
                    where lesson.contract.student.user.id = :userId
                      and (:lessonStatus is null or lesson.status = :lessonStatus)
                      and (:contractStatus is null or lesson.contract.status = :contractStatus)
                    """)
    Page<Lesson> findAllForStudentUser(
            @Param("userId") UUID userId,
            @Param("lessonStatus") LessonStatus lessonStatus,
            @Param("contractStatus") ContractStatus contractStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user",
            "contract.subject", "contract.grade"
    })
    @Query("""
            select lesson
            from Lesson lesson
            where lesson.id = :lessonId
              and lesson.contract.student.user.id = :userId
            """)
    java.util.Optional<Lesson> findDetailedByIdAndStudentUserId(
            @Param("lessonId") UUID lessonId,
            @Param("userId") UUID userId);

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
