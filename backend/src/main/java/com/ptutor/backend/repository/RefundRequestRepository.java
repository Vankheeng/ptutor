package com.ptutor.backend.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ptutor.backend.entity.RefundRequest;
import com.ptutor.backend.entity.enums.RefundRequestStatus;

import jakarta.persistence.LockModeType;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    boolean existsByContract_IdAndStatusIn(UUID contractId, Collection<RefundRequestStatus> statuses);

    boolean existsByEmployee_IdAndStatus(UUID employeeId, RefundRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user",
            "student", "student.user", "employee", "employee.user"
    })
    @Query("select request from RefundRequest request where request.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {
            "contract", "contract.student", "contract.student.user", "contract.tutor", "contract.tutor.user",
            "contract.subject", "contract.grade", "student", "student.user", "employee", "employee.user"
    })
    @Query("select request from RefundRequest request where request.id = :id")
    Optional<RefundRequest> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = { "contract", "contract.tutor", "contract.tutor.user", "student", "student.user" })
    @Query(value = """
            select request
            from RefundRequest request
            where request.student.user.id = :userId
              and (:status is null or request.status = :status)
            """,
            countQuery = """
                    select count(request)
                    from RefundRequest request
                    where request.student.user.id = :userId
                      and (:status is null or request.status = :status)
                    """)
    Page<RefundRequest> findAllForStudent(
            @Param("userId") UUID userId,
            @Param("status") RefundRequestStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = { "contract", "contract.tutor", "contract.tutor.user", "student", "student.user" })
    @Query(value = """
            select request
            from RefundRequest request
            where request.contract.tutor.user.id = :userId
              and (:status is null or request.status = :status)
            """,
            countQuery = """
                    select count(request)
                    from RefundRequest request
                    where request.contract.tutor.user.id = :userId
                      and (:status is null or request.status = :status)
                    """)
    Page<RefundRequest> findAllForTutor(
            @Param("userId") UUID userId,
            @Param("status") RefundRequestStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "contract", "contract.tutor", "contract.tutor.user", "contract.subject", "contract.grade",
            "student", "student.user", "employee", "employee.user"
    })
    @Query(value = """
            select request
            from RefundRequest request
            where request.status = :status
              and (:contractId is null or request.contract.id = :contractId)
              and (:keyword = ''
                   or lower(request.student.user.email) like lower(concat('%', :keyword, '%'))
                   or lower(concat(coalesce(request.student.user.firstName, ''), ' ',
                           coalesce(request.student.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
            """,
            countQuery = """
                    select count(request)
                    from RefundRequest request
                    where request.status = :status
                      and (:contractId is null or request.contract.id = :contractId)
                      and (:keyword = ''
                           or lower(request.student.user.email) like lower(concat('%', :keyword, '%'))
                           or lower(concat(coalesce(request.student.user.firstName, ''), ' ',
                                   coalesce(request.student.user.lastName, ''))) like lower(concat('%', :keyword, '%')))
                    """)
    Page<RefundRequest> findAllForReview(
            @Param("status") RefundRequestStatus status,
            @Param("contractId") UUID contractId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            select coalesce(sum(request.refundAmount), 0)
            from RefundRequest request
            where request.contract.id = :contractId
              and request.status = com.ptutor.backend.entity.enums.RefundRequestStatus.REFUNDED
            """)
    BigDecimal sumRefundedAmountForContract(@Param("contractId") UUID contractId);

    List<RefundRequest> findAllByContract_IdAndStatusOrderByReviewedAtDesc(UUID contractId, RefundRequestStatus status);
}
