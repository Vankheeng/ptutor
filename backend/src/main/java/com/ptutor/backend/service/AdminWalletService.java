package com.ptutor.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import com.ptutor.backend.dto.response.AdminContractTransactionResponse;
import com.ptutor.backend.dto.response.AdminHeldTuitionResponse;
import com.ptutor.backend.dto.response.AdminWalletResponse;
import com.ptutor.backend.dto.response.AdminWalletSummaryResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminWalletService {
    private final JdbcTemplate jdbc;

    private static final String WALLET_FROM = """
            from wallets w
            join users u on u.id = w.user_id and u.deleted_at is null
            where w.deleted_at is null
              and (exists (select 1 from students s where s.user_id = u.id and s.deleted_at is null)
                   or exists (select 1 from tutors t where t.user_id = u.id and t.deleted_at is null))
              and (? = '' or lower(coalesce(u.last_name, '') || ' ' || coalesce(u.first_name, '')) like ?
                   or lower(u.email) like ?)
            """;

    // Contract-level balance. Refunds are allocated to payments; earnings credited
    // to tutors are recorded as TUTOR_EARNING wallet transactions.
    private static final String HELD_CONTRACTS = """
            with paid as (
                select p.reference_id as contract_id, sum(p.amount) as paid_amount,
                       sum(case when p.status = 'REFUNDED' then p.amount
                                else coalesce(p.refunded_amount, 0) end) as refunded_amount,
                       max(p.paid_at) as last_paid_at
                from payments p
                where p.deleted_at is null and p.payment_type = 'TUITION_PAYMENT'
                  and p.reference_type = 'CONTRACT' and p.status in ('PAID', 'REFUNDED')
                group by p.reference_id
            ), tutor_credits as (
                select entry.contract_id, sum(entry.amount) as amount
                from (
                    select wt.reference_id as contract_id, wt.amount
                    from wallet_transactions wt
                    where wt.deleted_at is null and wt.purpose = 'TUTOR_EARNING'
                      and wt.transaction_type = 'CREDIT' and wt.status = 'COMPLETED'
                      and wt.reference_type = 'CONTRACT'
                    union all
                    select lesson.contract_id, wt.amount
                    from wallet_transactions wt
                    join lessons lesson on lesson.id = wt.reference_id and lesson.deleted_at is null
                    where wt.deleted_at is null and wt.purpose = 'TUTOR_EARNING'
                      and wt.transaction_type = 'CREDIT' and wt.status = 'COMPLETED'
                      and wt.reference_type = 'LESSON'
                    union all
                    select rr.contract_id, wt.amount
                    from refund_requests rr
                    join wallet_transactions wt on wt.id = rr.tutor_wallet_transaction_id
                    where rr.deleted_at is null and wt.deleted_at is null
                      and wt.purpose = 'TUTOR_EARNING' and wt.transaction_type = 'CREDIT'
                      and wt.status = 'COMPLETED'
                ) entry
                group by entry.contract_id
            )
            select c.id as contract_id, su.id as student_user_id,
                   trim(concat_ws(' ', su.last_name, su.first_name)) as student_name,
                   su.email as student_email, tu.id as tutor_user_id,
                   trim(concat_ws(' ', tu.last_name, tu.first_name)) as tutor_name,
                   paid.paid_amount, paid.refunded_amount,
                   coalesce(tutor_credits.amount, 0) as tutor_paid_amount,
                   paid.paid_amount - paid.refunded_amount - coalesce(tutor_credits.amount, 0) as held_amount,
                   paid.last_paid_at
            from paid
            join contracts c on c.id = paid.contract_id and c.deleted_at is null
            join students s on s.id = c.student_id and s.deleted_at is null
            join users su on su.id = s.user_id and su.deleted_at is null
            join tutors t on t.id = c.tutor_id and t.deleted_at is null
            join users tu on tu.id = t.user_id and tu.deleted_at is null
            left join tutor_credits on tutor_credits.contract_id = c.id
            where paid.paid_amount - paid.refunded_amount - coalesce(tutor_credits.amount, 0) > 0
            """;

    private static final String CONTRACT_TRANSACTIONS = """
            with entries as (
                select p.id, 'TUITION_PAYMENT' as type, p.user_id, p.amount, p.status,
                       coalesce(p.paid_at, p.created_at) as occurred_at, p.transaction_code
                from payments p
                where p.deleted_at is null and p.payment_type = 'TUITION_PAYMENT'
                  and p.reference_type = 'CONTRACT' and p.reference_id = ?
                  and p.status in ('PAID', 'REFUNDED')
                union all
                select wt.id,
                       case when wt.purpose = 'TUTOR_EARNING' then 'TUTOR_PAYOUT' else 'TUITION_REFUND' end,
                       w.user_id, wt.amount, wt.status, wt.created_at, null::varchar
                from wallet_transactions wt
                join wallets w on w.id = wt.wallet_id and w.deleted_at is null
                where wt.deleted_at is null and wt.transaction_type = 'CREDIT'
                  and wt.status = 'COMPLETED'
                  and wt.purpose in ('TUTOR_EARNING', 'CONTRACT_REFUND')
                  and ((wt.reference_type = 'CONTRACT' and wt.reference_id = ?)
                    or (wt.reference_type = 'LESSON' and exists (
                        select 1 from lessons l where l.id = wt.reference_id
                          and l.contract_id = ? and l.deleted_at is null))
                    or (wt.reference_type = 'REFUND_REQUEST' and exists (
                        select 1 from refund_requests rr where rr.id = wt.reference_id
                          and rr.contract_id = ? and rr.deleted_at is null)))
            )
            """;

    @Transactional(readOnly = true)
    public AdminWalletSummaryResponse summary() {
        var walletTotals = jdbc.queryForObject("""
                select (select count(*) from wallets w join users u on u.id = w.user_id and u.deleted_at is null
                        where w.deleted_at is null and (exists (select 1 from students s where s.user_id = u.id and s.deleted_at is null)
                             or exists (select 1 from tutors t where t.user_id = u.id and t.deleted_at is null))),
                       (select coalesce(sum(w.balance), 0) from wallets w join users u on u.id = w.user_id and u.deleted_at is null
                        where w.deleted_at is null and (exists (select 1 from students s where s.user_id = u.id and s.deleted_at is null)
                             or exists (select 1 from tutors t where t.user_id = u.id and t.deleted_at is null))),
                       (select coalesce(sum(w.pending_balance), 0) from wallets w join users u on u.id = w.user_id and u.deleted_at is null
                        where w.deleted_at is null and (exists (select 1 from students s where s.user_id = u.id and s.deleted_at is null)
                             or exists (select 1 from tutors t where t.user_id = u.id and t.deleted_at is null)))
                """, (rs, row) -> new Object[] { rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3) });
        var heldTotals = jdbc.queryForObject("select count(*), coalesce(sum(held_amount), 0) from ("
                + HELD_CONTRACTS + ") held", (rs, row) -> new Object[] { rs.getLong(1), rs.getBigDecimal(2) });
        return new AdminWalletSummaryResponse((long) walletTotals[0],
                (java.math.BigDecimal) walletTotals[1], (java.math.BigDecimal) walletTotals[2],
                (long) heldTotals[0], (java.math.BigDecimal) heldTotals[1]);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWalletResponse> wallets(String search, int page, int size) {
        String term = normalize(search);
        String pattern = "%" + term + "%";
        long count = jdbc.queryForObject("select count(*) " + WALLET_FROM, Long.class, term, pattern, pattern);
        var content = jdbc.query("""
                select w.id, u.id as user_id, trim(concat_ws(' ', u.last_name, u.first_name)) as user_name,
                       u.email, case when exists (select 1 from tutors t where t.user_id = u.id and t.deleted_at is null)
                            then 'TUTOR' when exists (select 1 from students s where s.user_id = u.id and s.deleted_at is null)
                            then 'STUDENT' else 'OTHER' end as user_type,
                       w.balance, w.pending_balance
                """ + WALLET_FROM + " order by w.updated_at desc, w.id desc limit ? offset ?",
                (rs, row) -> new AdminWalletResponse(uuid(rs, "id"), uuid(rs, "user_id"),
                        rs.getString("user_name"), rs.getString("email"), rs.getString("user_type"),
                        rs.getBigDecimal("balance"), rs.getBigDecimal("pending_balance")),
                term, pattern, pattern, size, page * size);
        return page(content, page, size, count);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminHeldTuitionResponse> tuition(String search, int page, int size) {
        String term = normalize(search);
        String pattern = "%" + term + "%";
        String filtered = "select * from (" + HELD_CONTRACTS + ") held where (? = '' or "
                + "lower(student_name) like ? or lower(student_email) like ? or lower(tutor_name) like ? "
                + "or contract_id::text like ?)";
        long count = jdbc.queryForObject("select count(*) from (" + filtered + ") matches", Long.class,
                term, pattern, pattern, pattern, pattern);
        var content = jdbc.query(filtered + " order by last_paid_at desc nulls last, contract_id desc limit ? offset ?",
                (rs, row) -> new AdminHeldTuitionResponse(uuid(rs, "contract_id"),
                        uuid(rs, "student_user_id"), rs.getString("student_name"), rs.getString("student_email"),
                        uuid(rs, "tutor_user_id"), rs.getString("tutor_name"),
                        rs.getBigDecimal("paid_amount"), rs.getBigDecimal("refunded_amount"),
                        rs.getBigDecimal("tutor_paid_amount"), rs.getBigDecimal("held_amount"),
                        rs.getTimestamp("last_paid_at") == null ? null
                                : rs.getTimestamp("last_paid_at").toLocalDateTime()),
                term, pattern, pattern, pattern, pattern, size, page * size);
        return page(content, page, size, count);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminContractTransactionResponse> contractTransactions(UUID contractId, int page, int size) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from contracts where id = ? and deleted_at is null)",
                Boolean.class, contractId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found");
        }
        long count = jdbc.queryForObject(CONTRACT_TRANSACTIONS + "select count(*) from entries", Long.class,
                contractId, contractId, contractId, contractId);
        var content = jdbc.query(CONTRACT_TRANSACTIONS + """
                select e.id, e.type, trim(concat_ws(' ', u.last_name, u.first_name)) as user_name,
                       e.amount, e.status, e.occurred_at, e.transaction_code
                from entries e join users u on u.id = e.user_id and u.deleted_at is null
                order by e.occurred_at desc, e.id desc limit ? offset ?
                """, (rs, row) -> new AdminContractTransactionResponse(uuid(rs, "id"), rs.getString("type"),
                rs.getString("user_name"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getTimestamp("occurred_at").toLocalDateTime(), rs.getString("transaction_code")),
                contractId, contractId, contractId, contractId, size, page * size);
        return page(content, page, size, count);
    }

    private String normalize(String search) {
        return search == null ? "" : search.strip().toLowerCase();
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private <T> PageResponse<T> page(java.util.List<T> content, int page, int size, long count) {
        int pages = (int) ((count + size - 1) / size);
        return new PageResponse<>(content, page, size, count, pages, page == 0, page >= pages - 1);
    }
}
