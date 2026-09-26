package com.ptutor.backend.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.ptutor.backend.dto.enums.FinancialTransactionSource;
import com.ptutor.backend.dto.response.FinancialTransactionResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminStudentTransactionRepository {

    private static final String TRANSACTION_UNION = """
            select p.id, 'PAYMENT' as source, p.payment_type as type,
                   p.payment_method as method, p.status, p.amount,
                   p.reference_id, p.note as description, p.created_at as occurred_at
              from payments p
             where p.user_id = :userId and p.deleted_at is null
            union all
            select wt.id, 'WALLET_TRANSACTION' as source, wt.transaction_type as type,
                   'WALLET' as method, wt.status, wt.amount,
                   wt.reference_id, wt.description, wt.created_at as occurred_at
              from wallet_transactions wt
              join wallets w on w.id = wt.wallet_id
             where w.user_id = :userId and wt.deleted_at is null and w.deleted_at is null
            union all
            select wr.id, 'WITHDRAWAL' as source, 'WITHDRAWAL' as type,
                   'WALLET' as method, wr.status, wr.amount,
                   null::uuid as reference_id, wr.note as description, wr.created_at as occurred_at
              from withdrawal_requests wr
              join wallets w on w.id = wr.wallet_id
             where w.user_id = :userId and wr.deleted_at is null and w.deleted_at is null
            """;

    private final EntityManager entityManager;

    public Page<FinancialTransactionResponse> findAll(
            UUID userId,
            FinancialTransactionSource source,
            String status,
            String type,
            String method,
            Pageable pageable) {
        QueryParts parts = filteredQuery(userId, source, status, type, method, null);
        Query dataQuery = entityManager.createNativeQuery(
                "select * from (" + TRANSACTION_UNION + ") tx " + parts.whereClause()
                        + " order by occurred_at desc, id desc limit :limit offset :offset");
        bind(dataQuery, parts.parameters());
        dataQuery.setParameter("limit", pageable.getPageSize());
        dataQuery.setParameter("offset", pageable.getOffset());

        Query countQuery = entityManager.createNativeQuery(
                "select count(*) from (" + TRANSACTION_UNION + ") tx " + parts.whereClause());
        bind(countQuery, parts.parameters());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(rows.stream().map(this::map).toList(), pageable, total);
    }

    public Optional<FinancialTransactionResponse> findById(
            UUID userId, FinancialTransactionSource source, UUID transactionId) {
        QueryParts parts = filteredQuery(userId, source, null, null, null, transactionId);
        Query query = entityManager.createNativeQuery(
                "select * from (" + TRANSACTION_UNION + ") tx " + parts.whereClause());
        bind(query, parts.parameters());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().findFirst().map(this::map);
    }

    private QueryParts filteredQuery(
            UUID userId,
            FinancialTransactionSource source,
            String status,
            String type,
            String method,
            UUID transactionId) {
        List<String> conditions = new ArrayList<>();
        List<QueryParameter> parameters = new ArrayList<>();
        parameters.add(new QueryParameter("userId", userId));
        addFilter(conditions, parameters, "source", source == null ? null : source.name());
        addFilter(conditions, parameters, "status", normalize(status));
        addFilter(conditions, parameters, "type", normalize(type));
        addFilter(conditions, parameters, "method", normalize(method));
        if (transactionId != null) {
            conditions.add("id = :transactionId");
            parameters.add(new QueryParameter("transactionId", transactionId));
        }
        String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
        return new QueryParts(where, parameters);
    }

    private void addFilter(
            List<String> conditions, List<QueryParameter> parameters, String column, String value) {
        if (value != null) {
            conditions.add("upper(" + column + ") = :" + column);
            parameters.add(new QueryParameter(column, value));
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private void bind(Query query, List<QueryParameter> parameters) {
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
    }

    private FinancialTransactionResponse map(Object[] row) {
        return new FinancialTransactionResponse(
                uuid(row[0]),
                FinancialTransactionSource.valueOf(row[1].toString()),
                row[2].toString(),
                row[3].toString(),
                row[4].toString(),
                (BigDecimal) row[5],
                uuid(row[6]),
                row[7] == null ? null : row[7].toString(),
                localDateTime(row[8]));
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    private record QueryParts(String whereClause, List<QueryParameter> parameters) {
    }

    private record QueryParameter(String name, Object value) {
    }
}
