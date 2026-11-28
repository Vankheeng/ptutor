-- Local-only fixtures for the admin transaction history screen.
-- Run against a disposable database after Flyway migrations.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, citizen_id, email, password, first_name, last_name, status) VALUES
('b0000000-0000-4000-8000-000000000001', '099988800001', 'transaction.admin@ptutor.local', crypt('PtutorDemo@2026!', gen_salt('bf', 10)), 'Demo', 'Admin', 'ACTIVE'),
('b0000000-0000-4000-8000-000000000002', '099988800002', 'transaction.student@ptutor.local', crypt('PtutorDemo@2026!', gen_salt('bf', 10)), 'Minh', 'Student Demo', 'ACTIVE'),
('b0000000-0000-4000-8000-000000000003', '099988800003', 'transaction.tutor@ptutor.local', crypt('PtutorDemo@2026!', gen_salt('bf', 10)), 'Lan', 'Tutor Demo', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO employees (id, user_id, role) VALUES
('b0000000-0000-4000-8000-000000000011', 'b0000000-0000-4000-8000-000000000001', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO students (id, user_id) VALUES
('b0000000-0000-4000-8000-000000000012', 'b0000000-0000-4000-8000-000000000002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tutors (id, user_id) VALUES
('b0000000-0000-4000-8000-000000000013', 'b0000000-0000-4000-8000-000000000003')
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallets (id, user_id, balance, pending_balance) VALUES
('b0000000-0000-4000-8000-000000000021', 'b0000000-0000-4000-8000-000000000002', 250000, 0),
('b0000000-0000-4000-8000-000000000022', 'b0000000-0000-4000-8000-000000000003', 150000, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, user_id, amount, payment_method, payment_type, status,
                      transaction_code, reference_type, reference_id, paid_at, note, created_at) VALUES
('b0000000-0000-4000-8000-000000000101', 'b0000000-0000-4000-8000-000000000002', 500000, 'VNPAY', 'WALLET_TOP_UP', 'PAID', 'TRANSACTION-DEMO-TOPUP', NULL, NULL, now() - interval '4 days', 'Student wallet top-up', now() - interval '4 days'),
('b0000000-0000-4000-8000-000000000102', 'b0000000-0000-4000-8000-000000000002', 250000, 'WALLET', 'TUITION_PAYMENT', 'PAID', 'TRANSACTION-DEMO-TUITION', NULL, NULL, now() - interval '3 days', 'Tuition paid with wallet', now() - interval '3 days'),
('b0000000-0000-4000-8000-000000000103', 'b0000000-0000-4000-8000-000000000003', 10000, 'VNPAY', 'TEACHING_REQUEST_FEE', 'PAID', 'TRANSACTION-DEMO-TEACHING', NULL, NULL, now() - interval '2 days', 'Teaching request fee', now() - interval '2 days'),
('b0000000-0000-4000-8000-000000000104', 'b0000000-0000-4000-8000-000000000002', 10000, 'VNPAY', 'STUDYING_REQUEST_FEE', 'FAILED', 'TRANSACTION-DEMO-FAILED', NULL, NULL, NULL, 'Failed studying request fee', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallet_transactions (id, wallet_id, transaction_type, purpose, amount,
                                 balance_after, pending_balance_after, reference_type, reference_id,
                                 description, status, idempotency_key, created_at) VALUES
('b0000000-0000-4000-8000-000000000201', 'b0000000-0000-4000-8000-000000000021', 'CREDIT', 'TOP_UP', 500000, 500000, 0, 'PAYMENT', 'b0000000-0000-4000-8000-000000000101', 'Wallet top-up from VNPay', 'COMPLETED', 'transaction-demo-topup', now() - interval '4 days'),
('b0000000-0000-4000-8000-000000000202', 'b0000000-0000-4000-8000-000000000021', 'DEBIT', 'TUITION_PAYMENT', 250000, 250000, 0, 'PAYMENT', 'b0000000-0000-4000-8000-000000000102', 'Tuition debit from wallet', 'COMPLETED', 'transaction-demo-tuition', now() - interval '3 days'),
('b0000000-0000-4000-8000-000000000203', 'b0000000-0000-4000-8000-000000000022', 'CREDIT', 'TUTOR_EARNING', 200000, 200000, 0, NULL, NULL, 'Tutor earning', 'COMPLETED', 'transaction-demo-earning', now() - interval '2 days'),
('b0000000-0000-4000-8000-000000000204', 'b0000000-0000-4000-8000-000000000022', 'DEBIT', 'WITHDRAWAL', 50000, 150000, 0, 'WITHDRAWAL_REQUEST', 'b0000000-0000-4000-8000-000000000301', 'Withdrawal to bank', 'COMPLETED', 'transaction-demo-withdrawal', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

INSERT INTO withdrawal_requests (id, wallet_id, amount, bank_code, bank_name, encrypted_account_number,
                                 account_number_last_four, account_holder_name, note, idempotency_key,
                                 status, wallet_transaction_id, completed_at, transfer_reference, created_at) VALUES
('b0000000-0000-4000-8000-000000000301', 'b0000000-0000-4000-8000-000000000022', 50000, 'VCB', 'Vietcombank', 'demo-only', '1234', 'Lan Tutor Demo', 'Demo withdrawal', 'transaction-demo-withdrawal-request', 'COMPLETED', 'b0000000-0000-4000-8000-000000000204', now() - interval '1 day', 'TRANSACTION-DEMO-TRANSFER', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;
COMMIT;
