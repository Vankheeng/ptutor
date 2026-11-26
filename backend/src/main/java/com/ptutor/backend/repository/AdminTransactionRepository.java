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
import com.ptutor.backend.dto.enums.AdminTransactionFlow;
import com.ptutor.backend.dto.response.AdminTransactionResponse;
import com.ptutor.backend.dto.response.AdminTransactionSummaryResponse;
import com.ptutor.backend.dto.response.FinancialTransactionResponse;
import com.ptutor.backend.entity.enums.ReferenceType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminTransactionRepository {

    private static final String TRANSACTIONS = """
            select p.id, 'PAYMENT' as source, p.payment_type as type, p.payment_method as method,
                   p.status, p.amount, p.reference_type, p.reference_id, p.note as description,
                   coalesce(p.paid_at, p.created_at) as occurred_at, p.user_id, p.payment_type as purpose,
                   p.transaction_code, null::varchar as transfer_reference
              from payments p where p.deleted_at is null
            union all
            select wt.id, 'WALLET_TRANSACTION', wt.transaction_type, 'WALLET',
                   wt.status, wt.amount, wt.reference_type, wt.reference_id, wt.description,
                   wt.created_at, w.user_id, wt.purpose, null::varchar, null::varchar
              from wallet_transactions wt join wallets w on w.id = wt.wallet_id
             where wt.deleted_at is null and w.deleted_at is null
            union all
            select wr.id, 'WITHDRAWAL', 'WITHDRAWAL', 'WALLET',
                   wr.status, wr.amount, 'WITHDRAWAL_REQUEST', wr.id, wr.note,
                   coalesce(wr.completed_at, wr.created_at), w.user_id, 'WITHDRAWAL',
                   null::varchar, wr.transfer_reference
              from withdrawal_requests wr join wallets w on w.id = wr.wallet_id
             where wr.deleted_at is null and w.deleted_at is null
            """;

    private static final String FROM = " from (" + TRANSACTIONS + ") tx join users u on u.id = tx.user_id ";
    // Only settled VNPay payments bring external cash in. A completed bank withdrawal moves cash out.
    // Wallet ledger entries and wallet-funded payments move existing balances inside PTutor.
    private static final String FLOW = "case "
            + "when tx.source = 'PAYMENT' and tx.method = 'VNPAY' and tx.status in ('PAID', 'REFUNDED') then 'IN' "
            + "when tx.source = 'WITHDRAWAL' and tx.status = 'COMPLETED' then 'OUT' "
            + "when tx.status in ('PENDING', 'PROCESSING', 'FAILED', 'CANCELLED', 'REJECTED') then 'NONE' "
            + "else 'INTERNAL' end";
    private static final String SELECT = "select tx.id, tx.source, tx.type, tx.method, tx.status, tx.amount, "
            + "tx.reference_type, tx.reference_id, tx.description, tx.occurred_at, u.id, "
            + "trim(concat_ws(' ', u.first_name, u.last_name)), u.email, tx.purpose, "
            + FLOW + " as flow, tx.transaction_code, tx.transfer_reference ";

    private final EntityManager entityManager;

    public Page<AdminTransactionResponse> findAll(
            FinancialTransactionSource source, AdminTransactionFlow flow, String status, String type, String method,
            String search, LocalDateTime from, LocalDateTime toExclusive, Pageable pageable) {
        Filters filters = filters(source, flow, status, type, method, search, from, toExclusive, null);
        Query query = entityManager.createNativeQuery(SELECT + FROM + filters.where()
                + " order by tx.occurred_at desc, tx.id desc limit :limit offset :offset");
        bind(query, filters.parameters());
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        Query count = entityManager.createNativeQuery("select count(*) " + FROM + filters.where());
        bind(count, filters.parameters());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return new PageImpl<>(rows.stream().map(this::map).toList(), pageable,
                ((Number) count.getSingleResult()).longValue());
    }

    public Optional<AdminTransactionResponse> findById(FinancialTransactionSource source, UUID id) {
        Filters filters = filters(source, null, null, null, null, null, null, null, id);
        Query query = entityManager.createNativeQuery(SELECT + FROM + filters.where());
        bind(query, filters.parameters());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().findFirst().map(this::map);
    }

    public Page<AdminTransactionResponse> findAllForWalletUser(UUID userId, Pageable pageable) {
        String condition = " where tx.source = 'WALLET_TRANSACTION' and tx.user_id = :userId";
        Query query = entityManager.createNativeQuery(SELECT + FROM + condition
                + " order by tx.occurred_at desc, tx.id desc limit :limit offset :offset");
        query.setParameter("userId", userId);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        Query count = entityManager.createNativeQuery("select count(*) " + FROM + condition);
        count.setParameter("userId", userId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return new PageImpl<>(rows.stream().map(this::map).toList(), pageable,
                ((Number) count.getSingleResult()).longValue());
    }

    public AdminTransactionSummaryResponse summarize(
            FinancialTransactionSource source, AdminTransactionFlow flow, String status, String type, String method,
            String search, LocalDateTime from, LocalDateTime toExclusive) {
        Filters filters = filters(source, flow, status, type, method, search, from, toExclusive, null);
        String sql = "select coalesce(sum(tx.amount) filter (where " + FLOW + " = 'IN'), 0), "
                + "coalesce(sum(tx.amount) filter (where " + FLOW + " = 'OUT'), 0), "
                + "count(*) filter (where " + FLOW + " = 'INTERNAL') " + FROM + filters.where();
        Query query = entityManager.createNativeQuery(sql);
        bind(query, filters.parameters());
        Object[] row = (Object[]) query.getSingleResult();
        BigDecimal moneyIn = (BigDecimal) row[0];
        BigDecimal moneyOut = (BigDecimal) row[1];
        return new AdminTransactionSummaryResponse(moneyIn, moneyOut, moneyIn.subtract(moneyOut),
                ((Number) row[2]).longValue());
    }

    private Filters filters(FinancialTransactionSource source, AdminTransactionFlow flow, String status, String type, String method,
            String search, LocalDateTime from, LocalDateTime toExclusive, UUID id) {
        List<String> conditions = new ArrayList<>();
        List<Parameter> parameters = new ArrayList<>();
        add(conditions, parameters, "tx.source", "source", source == null ? null : source.name());
        if (flow != null) {
            conditions.add(FLOW + " = :flow");
            parameters.add(new Parameter("flow", flow.name()));
        }
        add(conditions, parameters, "tx.status", "status", normalize(status));
        add(conditions, parameters, "tx.type", "type", normalize(type));
        add(conditions, parameters, "tx.method", "method", normalize(method));
        if (search != null && !search.isBlank()) {
            conditions.add("(lower(u.email) like :search or lower(trim(concat_ws(' ', u.first_name, u.last_name))) like :search or tx.id::text like :search)");
            parameters.add(new Parameter("search", "%" + search.strip().toLowerCase(java.util.Locale.ROOT) + "%"));
        }
        if (from != null) {
            conditions.add("tx.occurred_at >= :fromDate");
            parameters.add(new Parameter("fromDate", from));
        }
        if (toExclusive != null) {
            conditions.add("tx.occurred_at < :toDate");
            parameters.add(new Parameter("toDate", toExclusive));
        }
        if (id != null) {
            conditions.add("tx.id = :id");
            parameters.add(new Parameter("id", id));
        }
        return new Filters(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), parameters);
    }

    private void add(List<String> conditions, List<Parameter> parameters, String column, String name, String value) {
        if (value != null) {
            conditions.add("upper(" + column + ") = :" + name);
            parameters.add(new Parameter(name, value));
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private void bind(Query query, List<Parameter> parameters) {
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
    }

    private AdminTransactionResponse map(Object[] row) {
        FinancialTransactionResponse transaction = new FinancialTransactionResponse(
                uuid(row[0]), FinancialTransactionSource.valueOf(row[1].toString()), row[2].toString(),
                row[3].toString(), row[4].toString(), (BigDecimal) row[5],
                row[6] == null ? null : ReferenceType.valueOf(row[6].toString()), uuid(row[7]),
                row[8] == null ? null : row[8].toString(), dateTime(row[9]));
        return new AdminTransactionResponse(uuid(row[10]), row[11].toString(), row[12].toString(),
                row[13].toString(), AdminTransactionFlow.valueOf(row[14].toString()),
                row[15] == null ? null : row[15].toString(),
                row[16] == null ? null : row[16].toString(), transaction);
    }

    private UUID uuid(Object value) {
        return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private LocalDateTime dateTime(Object value) {
        return value instanceof LocalDateTime dateTime ? dateTime : ((Timestamp) value).toLocalDateTime();
    }

    private record Filters(String where, List<Parameter> parameters) { }
    private record Parameter(String name, Object value) { }
}
