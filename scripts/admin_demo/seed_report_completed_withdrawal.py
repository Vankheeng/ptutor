"""Add one settled sample withdrawal to the isolated report database."""

import argparse
import subprocess

from seed_report_extra import bank_account_ciphertext


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", default="ptutor_report_admin")
    args = parser.parse_args()
    encrypted = bank_account_ciphertext("9704220000456789")
    sql = f"""
BEGIN;
UPDATE wallets SET balance=250000, pending_balance=0
WHERE id='2f04baf2-2d64-3424-4a46-ff2fbae791c6';
INSERT INTO wallet_transactions
 (id,wallet_id,transaction_type,purpose,amount,balance_after,pending_balance_after,
  status,idempotency_key,description,reference_type,reference_id,created_at,updated_at)
VALUES
 ('a0000000-0000-4000-8000-000000000411','2f04baf2-2d64-3424-4a46-ff2fbae791c6',
  'DEBIT','WITHDRAWAL',150000,250000,0,'COMPLETED','report-completed-withdrawal-1',
  'Rút thu nhập về tài khoản ngân hàng','WITHDRAWAL_REQUEST',
  'a0000000-0000-4000-8000-000000000412',now()-interval '2 days',now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO withdrawal_requests
 (id,wallet_id,amount,bank_code,bank_name,encrypted_account_number,
  account_number_last_four,account_holder_name,note,idempotency_key,status,
  reviewed_by_user_id,reviewed_at,completed_at,transfer_reference,wallet_transaction_id,
  created_at,updated_at)
VALUES
 ('a0000000-0000-4000-8000-000000000412','2f04baf2-2d64-3424-4a46-ff2fbae791c6',
  150000,'VCB','Vietcombank','{encrypted}','6789','TRAN QUANG MINH',
  'Rút thu nhập giảng dạy','report-completed-withdrawal-1','COMPLETED',
  (SELECT user_id FROM employees WHERE role=1 LIMIT 1),now()-interval '2 days',
  now()-interval '2 days','VCB2611210001','a0000000-0000-4000-8000-000000000411',
  now()-interval '3 days',now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;
COMMIT;
"""
    result = subprocess.run(
        ["docker", "exec", "-i", "ptutor-postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", args.database],
        input=sql.encode(), capture_output=True,
    )
    if result.returncode:
        raise SystemExit(result.stderr.decode("utf-8", errors="replace"))
    print(result.stdout.decode("utf-8", errors="replace"))


if __name__ == "__main__":
    main()
