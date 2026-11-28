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
), refund_allocations AS (
    SELECT contract_id, coalesce(sum(refund_amount), 0) amount
    FROM refund_requests WHERE status='REFUNDED' AND deleted_at IS NULL
    GROUP BY contract_id
), payment_refunds AS (
    SELECT reference_id contract_id, coalesce(sum(refunded_amount), 0) amount
    FROM payments WHERE payment_type='TUITION_PAYMENT' AND deleted_at IS NULL
    GROUP BY reference_id
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
SELECT 'negative_held_tuition', count(*) FROM tuition paid LEFT JOIN payouts outgoing USING(contract_id)
    WHERE paid.net<coalesce(outgoing.amount,0)
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
