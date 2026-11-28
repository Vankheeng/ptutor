"""Check the accounting relationships in a local PTutor fixture database.

Run after `seed.py --load --finance`; every reported violation must be zero.
"""

import argparse
import subprocess


SQL = """
WITH ledger AS (
    SELECT wallet_id,
           sum(CASE WHEN transaction_type='CREDIT' AND status='COMPLETED' THEN amount
                    WHEN transaction_type='DEBIT' AND status='COMPLETED' THEN -amount
                    ELSE 0 END) net
    FROM wallet_transactions WHERE deleted_at IS NULL GROUP BY wallet_id
), tuition AS (
    SELECT p.reference_id contract_id,
           sum(p.amount - coalesce(p.refunded_amount, 0)) net
    FROM payments p WHERE p.deleted_at IS NULL AND p.payment_type='TUITION_PAYMENT'
      AND p.status IN ('PAID','REFUNDED') GROUP BY p.reference_id
), payouts AS (
    SELECT l.contract_id, sum(wt.amount) amount
    FROM wallet_transactions wt JOIN lessons l ON l.id=wt.reference_id
    WHERE wt.deleted_at IS NULL AND wt.reference_type='LESSON'
      AND wt.purpose='TUTOR_EARNING' AND wt.status='COMPLETED'
    GROUP BY l.contract_id
), refund_tutor_shares AS (
    SELECT r.contract_id, sum(wt.amount) amount
    FROM wallet_transactions wt JOIN refund_requests r ON r.id=wt.reference_id
    WHERE wt.deleted_at IS NULL AND wt.reference_type='REFUND_REQUEST'
      AND wt.purpose='TUTOR_EARNING' AND wt.status='COMPLETED'
    GROUP BY r.contract_id
), refund_allocations AS (
    SELECT contract_id, coalesce(sum(refund_amount), 0) amount
    FROM refund_requests WHERE status='REFUNDED' AND deleted_at IS NULL
    GROUP BY contract_id
), payment_refunds AS (
    SELECT reference_id contract_id, coalesce(sum(refunded_amount), 0) amount
    FROM payments WHERE payment_type='TUITION_PAYMENT' AND deleted_at IS NULL
    GROUP BY reference_id
), postings AS (
    SELECT tr.id, t.user_id, tr.status, tr.created_at,
           'TEACHING_REQUEST'::text kind, tr.quantity
    FROM teaching_requests tr JOIN tutors t ON t.id=tr.tutor_id
    WHERE tr.deleted_at IS NULL
    UNION ALL
    SELECT sr.id, s.user_id, sr.status, sr.created_at,
           'STUDYING_REQUEST'::text kind, 1 AS quantity
    FROM studying_requests sr JOIN students s ON s.id=sr.student_id
    WHERE sr.deleted_at IS NULL
)
SELECT 'wallet_ledger_mismatch', count(*) FROM wallets w LEFT JOIN ledger l ON l.wallet_id=w.id
    WHERE w.deleted_at IS NULL AND w.balance+w.pending_balance<>coalesce(l.net,0)
UNION ALL
SELECT 'paid_installment_payment_count', count(*) FROM contract_payment_installments i
    WHERE i.deleted_at IS NULL AND i.status='PAID' AND
      (SELECT count(*) FROM payments p WHERE p.payment_installment_id=i.id
       AND p.status IN ('PAID','REFUNDED') AND p.deleted_at IS NULL)<>1
UNION ALL
SELECT 'tuition_contract_or_amount_mismatch', count(*) FROM payments p
    JOIN contract_payment_installments i ON i.id=p.payment_installment_id
    JOIN contracts c ON c.id=i.contract_id
    JOIN students s ON s.id=c.student_id
    WHERE p.payment_type='TUITION_PAYMENT' AND p.deleted_at IS NULL
      AND (p.reference_type<>'CONTRACT' OR p.reference_id<>c.id
           OR p.user_id<>s.user_id OR p.amount<>i.amount)
UNION ALL
SELECT 'tutor_payout_owner_mismatch', count(*) FROM wallet_transactions wt
    JOIN wallets w ON w.id=wt.wallet_id
    JOIN lessons l ON l.id=wt.reference_id
    JOIN contracts c ON c.id=l.contract_id
    JOIN tutors t ON t.id=c.tutor_id
    WHERE wt.purpose='TUTOR_EARNING' AND wt.reference_type='LESSON'
      AND wt.status='COMPLETED' AND wt.deleted_at IS NULL AND w.user_id<>t.user_id
UNION ALL
SELECT 'negative_held_tuition', count(*) FROM tuition paid
    LEFT JOIN payouts outgoing USING(contract_id)
    LEFT JOIN refund_tutor_shares shares USING(contract_id)
    WHERE paid.net<coalesce(outgoing.amount,0)+coalesce(shares.amount,0)
UNION ALL
SELECT 'refund_allocation_mismatch', count(*) FROM refund_allocations r
    FULL JOIN payment_refunds p USING(contract_id) WHERE coalesce(r.amount,0)<>coalesce(p.amount,0)
UNION ALL
SELECT 'refund_wallet_credit_mismatch', count(*) FROM refund_requests r
    LEFT JOIN wallet_transactions wt ON wt.id=r.wallet_transaction_id
    WHERE r.status='REFUNDED' AND r.refund_amount>0
      AND (wt.id IS NULL OR wt.amount<>r.refund_amount OR wt.purpose<>'CONTRACT_REFUND'
           OR wt.reference_type<>'REFUND_REQUEST' OR wt.reference_id<>r.id)
UNION ALL
SELECT 'pending_withdrawal_reservation_mismatch', count(*) FROM wallets w
    WHERE w.pending_balance<>(
        SELECT coalesce(sum(r.amount),0) FROM withdrawal_requests r
        WHERE r.wallet_id=w.id AND r.deleted_at IS NULL
          AND r.status IN ('PENDING','PROCESSING'))
UNION ALL
SELECT 'posting_fee_count_mismatch', count(*) FROM postings post
    WHERE (SELECT count(*) FROM payments p
           WHERE p.deleted_at IS NULL AND p.status IN ('PAID','REFUNDED')
             AND p.payment_type=post.kind || '_FEE'
             AND p.reference_type=post.kind AND p.reference_id=post.id)
          <> CASE WHEN post.status='DRAFT' THEN 0 ELSE 1 END
       OR (post.status<>'DRAFT' AND NOT EXISTS (
           SELECT 1 FROM payments p WHERE p.deleted_at IS NULL
             AND p.payment_type=post.kind || '_FEE'
             AND p.reference_type=post.kind AND p.reference_id=post.id
             AND p.status=CASE WHEN post.status='REJECTED' THEN 'REFUNDED' ELSE 'PAID' END))
UNION ALL
SELECT 'posting_fee_owner_or_amount_mismatch', count(*) FROM payments p
    LEFT JOIN postings post ON post.id=p.reference_id AND post.kind=p.reference_type
    WHERE p.deleted_at IS NULL AND p.status IN ('PAID','REFUNDED')
      AND p.payment_type IN ('TEACHING_REQUEST_FEE','STUDYING_REQUEST_FEE')
      AND (post.id IS NULL OR p.payment_type<>post.kind || '_FEE'
           OR p.user_id<>post.user_id OR p.amount<>100000 * post.quantity
           OR p.paid_at<post.created_at)
UNION ALL
SELECT 'rejected_posting_refund_mismatch', count(*) FROM postings post
    JOIN payments p ON p.reference_type=post.kind AND p.reference_id=post.id
        AND p.payment_type=post.kind || '_FEE' AND p.status='REFUNDED'
    WHERE post.status='REJECTED' AND
      (SELECT count(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id
       WHERE wt.deleted_at IS NULL AND wt.reference_type=post.kind
         AND wt.reference_id=post.id AND wt.purpose=post.kind || '_REFUND'
         AND wt.status='COMPLETED' AND wt.amount=p.amount
         AND w.user_id=post.user_id)<>1
UNION ALL
SELECT 'topup_payment_credit_mismatch', count(*) FROM payments p
    WHERE p.deleted_at IS NULL AND p.payment_type='WALLET_TOP_UP' AND p.status='PAID'
      AND (SELECT count(*) FROM wallet_transactions wt JOIN wallets w ON w.id=wt.wallet_id
           WHERE wt.deleted_at IS NULL AND wt.reference_type='PAYMENT'
             AND wt.reference_id=p.id AND wt.purpose='TOP_UP' AND wt.status='COMPLETED'
             AND wt.amount=p.amount AND w.user_id=p.user_id)<>1
UNION ALL
SELECT 'earning_without_source', count(*) FROM wallet_transactions wt
    WHERE wt.deleted_at IS NULL AND wt.purpose='TUTOR_EARNING' AND wt.status='COMPLETED'
      AND (wt.reference_type IS NULL OR wt.reference_id IS NULL)
UNION ALL
SELECT 'final_paid_lesson_unsettled', count(*) FROM lessons l
    JOIN contract_payment_installments i ON i.lesson_id=l.id
    WHERE l.deleted_at IS NULL AND l.status IN ('COMPLETED','CONFIRMED') AND i.status='PAID'
      AND NOT EXISTS (SELECT 1 FROM wallet_transactions wt WHERE wt.deleted_at IS NULL
                      AND wt.reference_type='LESSON' AND wt.reference_id=l.id
                      AND wt.purpose='TUTOR_EARNING' AND wt.status='COMPLETED')
      AND NOT EXISTS (SELECT 1 FROM refund_request_lessons rrl
                      JOIN refund_requests r ON r.id=rrl.refund_request_id
                      WHERE rrl.lesson_id=l.id AND r.status='REFUNDED')
UNION ALL
SELECT 'invalid_refund_amount', count(*) FROM payments p
    WHERE p.deleted_at IS NULL AND (p.refunded_amount<0 OR p.refunded_amount>p.amount)
UNION ALL
SELECT 'per_lesson_installment_schedule_mismatch', count(*) FROM contracts c
    WHERE c.deleted_at IS NULL AND c.status IN ('ACTIVE','COMPLETED')
      AND c.payment_period='PER_LESSON' AND
      ((SELECT count(*) FROM contract_payment_installments i
        WHERE i.deleted_at IS NULL AND i.contract_id=c.id)<>c.total_lession
       OR EXISTS (SELECT 1 FROM contract_payment_installments i
                  WHERE i.deleted_at IS NULL AND i.contract_id=c.id
                    AND (i.payment_period<>'PER_LESSON' OR i.amount<>c.price
                         OR i.status='CANCELLED')))
ORDER BY 1;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", default="ptutor_app_seeded")
    parser.add_argument("--container", default="ptutor-postgres")
    args = parser.parse_args()
    result = subprocess.run(
        ["docker", "exec", "-i", args.container, "psql", "-X", "-A", "-t", "-F", "|",
         "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", args.database],
        input=SQL.encode(), capture_output=True,
    )
    if result.returncode:
        raise SystemExit(result.stderr.decode(errors="replace"))
    violations = dict(line.split("|") for line in result.stdout.decode().splitlines() if "|" in line)
    for label, count in violations.items():
        print(f"{label}: {count}")
    if not violations or any(int(count) for count in violations.values()):
        raise SystemExit("Finance fixture has accounting violations")


if __name__ == "__main__":
    main()
