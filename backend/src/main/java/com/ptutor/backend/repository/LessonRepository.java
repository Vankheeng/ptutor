package com.ptutor.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    @EntityGraph(attributePaths = { "paymentInstallment" })
    Page<Lesson> findAllByContract_Id(UUID contractId, Pageable pageable);

    @EntityGraph(attributePaths = { "paymentInstallment" })
    Page<Lesson> findAllByContract_IdAndStatus(UUID contractId, LessonStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "paymentInstallment" })
    List<Lesson> findAllByContract_IdOrderByDateAscStartTimeAsc(UUID contractId);

    @Query("""
            select count(lesson) > 0
            from Lesson lesson
            where (:excludedLessonId is null or lesson.id <> :excludedLessonId)
              and lesson.status <> com.ptutor.backend.entity.enums.LessonStatus.CANCELLED
              and lesson.contract.status = com.ptutor.backend.entity.enums.ContractStatus.ACTIVE
              and lesson.date = :date
              and lesson.startTime < :endTime
              and lesson.endTime > :startTime
              and (lesson.contract.student.user.id = :studentUserId
                   or lesson.contract.tutor.user.id = :tutorUserId)
            """)
    boolean existsScheduleConflict(
            @Param("studentUserId") UUID studentUserId,
            @Param("tutorUserId") UUID tutorUserId,
            @Param("date") java.time.LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("excludedLessonId") UUID excludedLessonId);

    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user"
    })
    @Query("""
            select lesson
            from Lesson lesson
            where (:excludedLessonId is null or lesson.id <> :excludedLessonId)
              and lesson.status <> com.ptutor.backend.entity.enums.LessonStatus.CANCELLED
              and lesson.contract.status = com.ptutor.backend.entity.enums.ContractStatus.ACTIVE
              and lesson.date = :date
              and lesson.startTime < :endTime
              and lesson.endTime > :startTime
              and (lesson.contract.student.user.id = :studentUserId
                   or lesson.contract.tutor.user.id = :tutorUserId)
            order by lesson.startTime asc
            """)
    List<Lesson> findScheduleConflicts(
            @Param("studentUserId") UUID studentUserId,
            @Param("tutorUserId") UUID tutorUserId,
            @Param("date") java.time.LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("excludedLessonId") UUID excludedLessonId);

    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user"
    })
    @Query("""
            select lesson
            from Lesson lesson
            where (:excludedLessonId is null or lesson.id <> :excludedLessonId)
              and lesson.status <> com.ptutor.backend.entity.enums.LessonStatus.CANCELLED
              and lesson.contract.status in :contractStatuses
              and lesson.date = :date
              and lesson.startTime < :endTime
              and lesson.endTime > :startTime
              and (lesson.contract.student.user.id = :studentUserId
                   or lesson.contract.tutor.user.id = :tutorUserId)
            order by lesson.startTime asc
            """)
    List<Lesson> findScheduleConflictsIncludingPending(
            @Param("studentUserId") UUID studentUserId,
            @Param("tutorUserId") UUID tutorUserId,
            @Param("date") java.time.LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("excludedLessonId") UUID excludedLessonId,
            @Param("contractStatuses") Collection<ContractStatus> contractStatuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select lesson from Lesson lesson where lesson.id in :lessonIds")
    List<Lesson> findAllByIdForUpdate(@Param("lessonIds") java.util.Collection<UUID> lessonIds);

    long countByContract_Student_User_Id(UUID userId);

    long countByContract_Student_User_IdAndStatus(UUID userId, LessonStatus status);

    long countByContract_Tutor_User_Id(UUID userId);

    long countByContract_Tutor_User_IdAndStatus(UUID userId, LessonStatus status);

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

    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user",
            "contract.subject", "contract.grade"
    })
    @Query(value = """
            select lesson
            from Lesson lesson
            where lesson.contract.tutor.user.id = :userId
              and (:lessonStatus is null or lesson.status = :lessonStatus)
              and (:contractStatus is null or lesson.contract.status = :contractStatus)
            """,
            countQuery = """
                    select count(lesson)
                    from Lesson lesson
                    where lesson.contract.tutor.user.id = :userId
                      and (:lessonStatus is null or lesson.status = :lessonStatus)
                      and (:contractStatus is null or lesson.contract.status = :contractStatus)
                    """)
    Page<Lesson> findAllForTutorUser(
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
              and lesson.contract.tutor.user.id = :userId
            """)
    java.util.Optional<Lesson> findDetailedByIdAndTutorUserId(
            @Param("lessonId") UUID lessonId,
            @Param("userId") UUID userId);

    @Query("""
            select lesson
            from Lesson lesson
            where lesson.status = :status
              and lesson.markedTaughtAt <= :deadline
            """)
    List<Lesson> findAllMarkedTaughtBefore(
            @Param("status") LessonStatus status,
            @Param("deadline") LocalDateTime deadline);

}
