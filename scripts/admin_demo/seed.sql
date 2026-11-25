-- Local admin UI fixtures. Run after the recommendation demo and evaluation data.
-- Fixed IDs and ON CONFLICT make repeated runs safe.
BEGIN;

INSERT INTO users (id, citizen_id, email, password, first_name, last_name, status)
VALUES
 ('a0000000-0000-4000-8000-000000000001', '099900000001', 'admin.demo.cs@ptutor.local', '$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG', 'Demo', 'Customer Service', 'ACTIVE'),
 ('a0000000-0000-4000-8000-000000000002', '099900000002', 'admin.demo.accountant@ptutor.local', '$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG', 'Demo', 'Accountant', 'ACTIVE'),
 ('a0000000-0000-4000-8000-000000000003', '099900000003', 'admin.demo.pending.tutor@ptutor.local', '$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG', 'Demo', 'Pending Tutor', 'ACTIVE'),
 ('a0000000-0000-4000-8000-000000000004', '099900000004', 'admin.demo.rejected.tutor@ptutor.local', '$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG', 'Demo', 'Rejected Tutor', 'ACTIVE'),
 ('a0000000-0000-4000-8000-000000000005', '099900000005', 'admin.demo.inactive.student@ptutor.local', '$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG', 'Demo', 'Inactive Student', 'INACTIVE'),
 ('a0000000-0000-4000-8000-000000000006', '099900000006', 'admin.demo.blocked.student@ptutor.local', '$2a$10$fjSLBzDSp9Gab9SvT.noDORVdO5Q09vznhQVeOncz1Cx6pLGOCAaG', 'Demo', 'Blocked Student', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Employee detail decrypts citizen IDs. Encrypt the local demo IDs with the same
-- key derivation as CitizenIdCryptoService; placeholders are filled by seed.py.
UPDATE users SET citizen_id='__ADMIN_CITIZEN_ENCRYPTED__',
 citizen_id_hash='__ADMIN_CITIZEN_HASH__'
WHERE email='admin@ptutor.local' AND citizen_id='000000000000' AND citizen_id_hash IS NULL;

UPDATE users SET citizen_id='__CS_CITIZEN_ENCRYPTED__',
 citizen_id_hash='__CS_CITIZEN_HASH__', phone='0911000001',
 date_of_birth='1990-01-01', gender='FEMALE',
 district_id=(SELECT id FROM districts ORDER BY id LIMIT 1), detail_address='Admin demo office'
WHERE id='a0000000-0000-4000-8000-000000000001' AND citizen_id_hash IS NULL;

UPDATE users SET citizen_id='__ACCOUNTANT_CITIZEN_ENCRYPTED__',
 citizen_id_hash='__ACCOUNTANT_CITIZEN_HASH__', phone='0911000002',
 date_of_birth='1991-01-01', gender='MALE',
 district_id=(SELECT id FROM districts ORDER BY id LIMIT 1), detail_address='Admin demo office'
WHERE id='a0000000-0000-4000-8000-000000000002' AND citizen_id_hash IS NULL;

UPDATE users SET suspension_type='TEMPORARY', suspension_reason='Admin demo: test suspension',
 suspended_at=now()-interval '1 day', suspended_until=now()+interval '30 days',
 suspended_by_user_id=(SELECT user_id FROM employees WHERE role=1 LIMIT 1), suspension_count=1
WHERE id='a0000000-0000-4000-8000-000000000006';

INSERT INTO employees (id, user_id, role, job_function) VALUES
 ('a0000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000001', 2, 'CUSTOMER_SERVICE'),
 ('a0000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000002', 2, 'ACCOUNTANT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tutors (id, user_id, introduction, experience_years, profile_status,
 profile_reviewed_by, profile_reviewed_at, profile_rejection_reason)
VALUES
 ('a0000000-0000-4000-8000-000000000021', 'a0000000-0000-4000-8000-000000000003', 'Admin demo profile awaiting review', 3, 'PENDING', NULL, NULL, NULL),
 ('a0000000-0000-4000-8000-000000000022', 'a0000000-0000-4000-8000-000000000004', 'Admin demo rejected profile', 2, 'REJECTED', 'a0000000-0000-4000-8000-000000000011', now()-interval '2 days', 'Admin demo: incomplete profile')
ON CONFLICT (id) DO NOTHING;

INSERT INTO students (id, user_id) VALUES
 ('a0000000-0000-4000-8000-000000000023', 'a0000000-0000-4000-8000-000000000005'),
 ('a0000000-0000-4000-8000-000000000024', 'a0000000-0000-4000-8000-000000000006')
ON CONFLICT (id) DO NOTHING;

INSERT INTO certificates (id, tutor_id, name, issuing_organization, status, rejection_reason, reviewed_by, reviewed_at)
VALUES
 ('a0000000-0000-4000-8000-000000000031', 'a0000000-0000-4000-8000-000000000021', 'Admin demo pending certificate', 'Demo University', 'PENDING', NULL, NULL, NULL),
 ('a0000000-0000-4000-8000-000000000032', (SELECT t.id FROM tutors t JOIN users u ON u.id=t.user_id WHERE u.email='demo.tutor01@ptutor.local'), 'Admin demo certificate for separate review', 'Demo University', 'PENDING', NULL, NULL, NULL),
 ('a0000000-0000-4000-8000-000000000033', (SELECT t.id FROM tutors t JOIN users u ON u.id=t.user_id WHERE u.email='demo.tutor01@ptutor.local'), 'Admin demo verified certificate', 'Demo University', 'VERIFIED', NULL, 'a0000000-0000-4000-8000-000000000011', now()-interval '3 days'),
 ('a0000000-0000-4000-8000-000000000034', (SELECT t.id FROM tutors t JOIN users u ON u.id=t.user_id WHERE u.email='demo.tutor02@ptutor.local'), 'Admin demo rejected certificate', 'Demo University', 'REJECTED', 'Admin demo: unreadable file', 'a0000000-0000-4000-8000-000000000011', now()-interval '3 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO teaching_requests (id, tutor_id, subject_id, title, description, quantity, expected_price, teaching_mode, status, reviewed_by, reviewed_at, rejection_reason)
VALUES
 ('a0000000-0000-4000-8000-000000000041', (SELECT t.id FROM tutors t JOIN users u ON u.id=t.user_id WHERE u.email='demo.tutor01@ptutor.local'), (SELECT subject_id FROM teaching_requests WHERE status='OPEN' AND subject_id IS NOT NULL LIMIT 1), 'ADMIN DEMO: teaching request to approve', 'Test the teaching request review workflow.', 2, 220000, 'ONLINE', 'PENDING_REVIEW', NULL, NULL, NULL),
 ('a0000000-0000-4000-8000-000000000042', (SELECT t.id FROM tutors t JOIN users u ON u.id=t.user_id WHERE u.email='demo.tutor02@ptutor.local'), (SELECT subject_id FROM teaching_requests WHERE status='OPEN' AND subject_id IS NOT NULL LIMIT 1), 'ADMIN DEMO: rejected teaching request', 'Test reviewed requests.', 1, 200000, 'ONLINE', 'REJECTED', 'a0000000-0000-4000-8000-000000000011', now()-interval '2 days', 'Admin demo: missing details')
ON CONFLICT (id) DO NOTHING;

INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id)
SELECT 'a0000000-0000-4000-8000-000000000043', (SELECT id FROM grades WHERE level=9 LIMIT 1), 'a0000000-0000-4000-8000-000000000041'
ON CONFLICT (id) DO NOTHING;

INSERT INTO studying_requests (id, student_id, subject_id, grade_id, title, description, max_price, learning_mode, status, reviewed_by, reviewed_at, rejection_reason)
VALUES
 ('a0000000-0000-4000-8000-000000000051', (SELECT s.id FROM students s JOIN users u ON u.id=s.user_id WHERE u.email='demo.student1@ptutor.local'), (SELECT subject_id FROM studying_requests WHERE status='OPEN' AND subject_id IS NOT NULL LIMIT 1), (SELECT id FROM grades WHERE level=9 LIMIT 1), 'ADMIN DEMO: studying request to approve', 'Test the studying request review workflow.', 250000, 'ONLINE', 'PENDING_REVIEW', NULL, NULL, NULL),
 ('a0000000-0000-4000-8000-000000000052', (SELECT s.id FROM students s JOIN users u ON u.id=s.user_id WHERE u.email='demo.student2@ptutor.local'), (SELECT subject_id FROM studying_requests WHERE status='OPEN' AND subject_id IS NOT NULL LIMIT 1), (SELECT id FROM grades WHERE level=8 LIMIT 1), 'ADMIN DEMO: rejected studying request', 'Test reviewed requests.', 200000, 'ONLINE', 'REJECTED', 'a0000000-0000-4000-8000-000000000011', now()-interval '2 days', 'Admin demo: missing details')
ON CONFLICT (id) DO NOTHING;

INSERT INTO complaints (id, user_id, contract_id, employee_id, title, content, status, category)
VALUES
 ('a0000000-0000-4000-8000-000000000061', (SELECT s.user_id FROM contracts c JOIN students s ON s.id=c.student_id WHERE c.status='ACTIVE' ORDER BY c.id LIMIT 1), (SELECT id FROM contracts WHERE status='ACTIVE' ORDER BY id LIMIT 1), NULL, 'ADMIN DEMO: pending complaint', 'Please review this sample complaint.', 'PENDING', 'TEACHING_QUALITY'),
 ('a0000000-0000-4000-8000-000000000062', (SELECT s.user_id FROM contracts c JOIN students s ON s.id=c.student_id WHERE c.status='ACTIVE' ORDER BY c.id OFFSET 1 LIMIT 1), (SELECT id FROM contracts WHERE status='ACTIVE' ORDER BY id OFFSET 1 LIMIT 1), 'a0000000-0000-4000-8000-000000000011', 'ADMIN DEMO: complaint in review', 'Please test evidence and resolution.', 'IN_REVIEW', 'LATE_OR_SHORT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO notifications (id, title, content, type, record_type, category, audience, campaign_status, created_by_user_id)
VALUES
 ('a0000000-0000-4000-8000-000000000071', 'ADMIN DEMO: draft campaign', 'This campaign is safe to edit.', 'SYSTEM', 'BROADCAST_MASTER', 'GENERAL', 'ALL', 'DRAFT', (SELECT user_id FROM employees WHERE role=1 LIMIT 1)),
 ('a0000000-0000-4000-8000-000000000072', 'ADMIN DEMO: scheduled campaign', 'This campaign is scheduled for testing.', 'SYSTEM', 'BROADCAST_MASTER', 'SERVICE_UPDATE', 'TUTOR', 'SCHEDULED', (SELECT user_id FROM employees WHERE role=1 LIMIT 1))
ON CONFLICT (id) DO NOTHING;

UPDATE notifications SET scheduled_at=now()+interval '30 days'
WHERE id='a0000000-0000-4000-8000-000000000072' AND scheduled_at IS NULL;

INSERT INTO notifications (id, user_id, title, content, type, record_type, event_type)
VALUES ('a0000000-0000-4000-8000-000000000073', (SELECT id FROM users WHERE email='demo.student1@ptutor.local'), 'ADMIN DEMO: account notification', 'Test the student notification list.', 'SYSTEM', 'SYSTEM_EVENT', 'WALLET_BALANCE_CHANGED')
ON CONFLICT (id) DO NOTHING;

-- Use three existing active contracts so refund records have real participants.
CREATE TEMP VIEW admin_demo_contracts AS
SELECT c.*, row_number() OVER (ORDER BY c.id) AS demo_number
FROM contracts c WHERE c.status='ACTIVE' AND c.deleted_at IS NULL;

INSERT INTO lessons (id, contract_id, title, date, start_time, end_time, teaching_mode, status, marked_taught_at)
SELECT CASE c.demo_number
 WHEN 1 THEN 'a0000000-0000-4000-8000-000000000081'::uuid
 WHEN 2 THEN 'a0000000-0000-4000-8000-000000000082'::uuid
 ELSE 'a0000000-0000-4000-8000-000000000083'::uuid END,
 c.id, 'ADMIN DEMO: disputed lesson', current_date-2, '18:00', '19:30', 'ONLINE',
 'PENDING_CONFIRMATION', now()-interval '2 days'
FROM admin_demo_contracts c WHERE c.demo_number<=3
ON CONFLICT (id) DO NOTHING;

INSERT INTO contract_payment_installments
 (id, contract_id, sequence_number, payment_period, amount, due_date, status, created_at, updated_at)
SELECT md5(c.id::text || ':admin-demo-installment:' || n)::uuid, c.id, n, 'PER_LESSON',
 c.price, current_date-10, 'PAID', now()-interval '10 days', now()-interval '10 days'
FROM admin_demo_contracts c CROSS JOIN generate_series(1,8) n
WHERE c.demo_number<=3
ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, user_id, amount, payment_method, payment_type, status,
 transaction_code, reference_type, reference_id, paid_at, note)
SELECT CASE c.demo_number
 WHEN 1 THEN 'a0000000-0000-4000-8000-000000000091'::uuid
 WHEN 2 THEN 'a0000000-0000-4000-8000-000000000092'::uuid
 ELSE 'a0000000-0000-4000-8000-000000000093'::uuid END,
 s.user_id, c.price*8, 'VNPAY', 'TUITION_PAYMENT', 'PAID',
 'ADMIN-DEMO-TUITION-' || c.demo_number, 'CONTRACT', c.id,
 now()-interval '10 days', 'Admin demo paid tuition for refund review'
FROM admin_demo_contracts c JOIN students s ON s.id=c.student_id
WHERE c.demo_number<=3
ON CONFLICT (id) DO NOTHING;

INSERT INTO refund_requests (id, contract_id, student_id, employee_id, description,
 status, tutor_response_deadline, tutor_response, tutor_responded_at)
SELECT CASE c.demo_number
 WHEN 1 THEN 'a0000000-0000-4000-8000-000000000101'::uuid
 WHEN 2 THEN 'a0000000-0000-4000-8000-000000000102'::uuid
 ELSE 'a0000000-0000-4000-8000-000000000103'::uuid END,
 c.id, c.student_id,
 CASE WHEN c.demo_number=2 THEN 'a0000000-0000-4000-8000-000000000012'::uuid ELSE NULL END,
 'ADMIN DEMO: dispute a lesson marked as taught',
 CASE c.demo_number WHEN 1 THEN 'PENDING' WHEN 2 THEN 'IN_REVIEW' ELSE 'REJECTED' END,
 now()+interval '7 days',
 CASE WHEN c.demo_number=2 THEN 'I can provide lesson details.' ELSE NULL END,
 CASE WHEN c.demo_number=2 THEN now()-interval '1 day' ELSE NULL END
FROM admin_demo_contracts c WHERE c.demo_number<=3
ON CONFLICT (id) DO NOTHING;

UPDATE refund_requests SET rejection_reason='Admin demo: claim was not supported',
 reviewed_at=now()-interval '1 day', employee_id='a0000000-0000-4000-8000-000000000012'
WHERE id='a0000000-0000-4000-8000-000000000103' AND rejection_reason IS NULL;

INSERT INTO refund_request_lessons (id, refund_request_id, lesson_id, claimed_outcome)
VALUES
 ('a0000000-0000-4000-8000-000000000111', 'a0000000-0000-4000-8000-000000000101', 'a0000000-0000-4000-8000-000000000081', 'TUTOR_ABSENT'),
 ('a0000000-0000-4000-8000-000000000112', 'a0000000-0000-4000-8000-000000000102', 'a0000000-0000-4000-8000-000000000082', 'TUTOR_ABSENT'),
 ('a0000000-0000-4000-8000-000000000113', 'a0000000-0000-4000-8000-000000000103', 'a0000000-0000-4000-8000-000000000083', 'STUDENT_ABSENT')
ON CONFLICT (id) DO NOTHING;

-- Simulated eval accounts were inserted directly, so give them the wallets that
-- normal registration creates. Refund approval credits these wallets.
INSERT INTO wallets (id, user_id, balance, pending_balance)
SELECT md5(u.id::text || ':admin-demo-wallet')::uuid, u.id, 0, 0
FROM users u WHERE u.email LIKE 'eval.%@ptutor.local'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO wallets (id, user_id, balance, pending_balance)
SELECT 'a0000000-0000-4000-8000-000000000121', id, 500000, 0
FROM users WHERE email='demo.student1@ptutor.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallets (id, user_id, balance, pending_balance)
SELECT 'a0000000-0000-4000-8000-000000000124', id, 0, 0
FROM users WHERE email='demo.tutor01@ptutor.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallet_transactions (id, wallet_id, transaction_type, purpose, amount,
 balance_after, pending_balance_after, status, idempotency_key, description,
 reference_type, reference_id)
VALUES ('a0000000-0000-4000-8000-000000000122', 'a0000000-0000-4000-8000-000000000121',
 'CREDIT', 'TOP_UP', 500000, 500000, 0, 'COMPLETED', 'admin-demo-top-up',
 'Admin demo wallet top-up', 'PAYMENT', 'a0000000-0000-4000-8000-000000000123')
ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (id, user_id, amount, payment_method, payment_type, status,
 transaction_code, reference_type, reference_id, paid_at, note)
SELECT 'a0000000-0000-4000-8000-000000000123', id, 500000, 'VNPAY', 'WALLET_TOP_UP',
 'PAID', 'ADMIN-DEMO-TOP-UP', 'WALLET_TRANSACTION',
 'a0000000-0000-4000-8000-000000000122', now()-interval '1 day', 'Admin demo wallet transaction'
FROM users WHERE email='demo.student1@ptutor.local'
ON CONFLICT (id) DO NOTHING;

-- A rejected pending request credits its fee to the owner's wallet.
INSERT INTO payments (id, user_id, amount, payment_method, payment_type, status,
 transaction_code, reference_type, reference_id, paid_at, note)
VALUES
 ('a0000000-0000-4000-8000-000000000131', (SELECT id FROM users WHERE email='demo.tutor01@ptutor.local'), 10000, 'VNPAY', 'TEACHING_REQUEST_FEE', 'PAID', 'ADMIN-DEMO-TEACHING-FEE', 'TEACHING_REQUEST', 'a0000000-0000-4000-8000-000000000041', now()-interval '1 day', 'Admin demo review fee'),
 ('a0000000-0000-4000-8000-000000000132', (SELECT id FROM users WHERE email='demo.student1@ptutor.local'), 10000, 'VNPAY', 'STUDYING_REQUEST_FEE', 'PAID', 'ADMIN-DEMO-STUDYING-FEE', 'STUDYING_REQUEST', 'a0000000-0000-4000-8000-000000000051', now()-interval '1 day', 'Admin demo review fee')
ON CONFLICT (id) DO NOTHING;

COMMIT;
