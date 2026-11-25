-- Presentation data for the isolated report database. All payments and addresses are samples.
BEGIN;

-- The recommendation fixture stored given names before family names.
UPDATE users
SET first_name = last_name, last_name = first_name
WHERE email LIKE 'demo.student%@ptutor.local' OR email LIKE 'demo.tutor%@ptutor.local';

-- Gmail-shaped examples for screenshots; the report database must not send email.
UPDATE users
SET email = lower(regexp_replace(unaccent(first_name || last_name), '[^a-zA-Z0-9]', '', 'g')) || '@gmail.com'
WHERE email LIKE 'demo.student%@ptutor.local' OR email LIKE 'demo.tutor%@ptutor.local';

UPDATE users SET email='lephuonganh@gmail.com' WHERE id='a0000000-0000-4000-8000-000000000003';
UPDATE users SET email='phamhuudat@gmail.com' WHERE id='a0000000-0000-4000-8000-000000000004';
UPDATE users SET email='vogiahan@gmail.com' WHERE id='a0000000-0000-4000-8000-000000000005';
UPDATE users SET email='dangminhkhang@gmail.com' WHERE id='a0000000-0000-4000-8000-000000000006';
UPDATE users SET email=regexp_replace(email, '\.(1998|2004|1996)@gmail\.com$', '@gmail.com')
WHERE email ~ '\.(1998|2004|1996)@gmail\.com$';

-- Paid installments already exist for these three contracts. Add completed lessons
-- linked to them; keep the separate disputed lessons pending confirmation.
INSERT INTO lessons (id, contract_id, payment_installment_id, title, date, start_time,
                     end_time, teaching_mode, status, note, marked_taught_at)
SELECT md5(c.id::text || ':report-completed-lesson:' || n)::uuid,
       c.id, i.id,
       'Buổi ' || n || ': Ôn tập và luyện bài tập', current_date - (n + 2),
       '18:00', '19:30', 'ONLINE', 'COMPLETED',
       'Học viên đã xác nhận hoàn thành buổi học.', now() - (n + 2) * interval '1 day'
FROM contracts c
JOIN tutors t ON t.id=c.tutor_id
JOIN users u ON u.id=t.user_id
CROSS JOIN LATERAL generate_series(1, CASE WHEN u.id='9da38ede-06b3-5316-912b-56d5ee09ff40' THEN 5 ELSE 2 END) n
JOIN contract_payment_installments i ON i.contract_id=c.id AND i.sequence_number=n
WHERE c.status='ACTIVE' AND c.deleted_at IS NULL
ON CONFLICT (id) DO NOTHING;

UPDATE contract_payment_installments i
SET lesson_id=l.id
FROM lessons l
WHERE l.payment_installment_id=i.id
  AND l.title LIKE 'Buổi %: Ôn tập và luyện bài tập'
  AND i.lesson_id IS NULL;

-- Five completed lessons at 200,000 VND each produce 1,000,000 VND tutor earnings.
-- The existing pending withdrawal reserves 300,000 VND, leaving 700,000 available.
INSERT INTO wallet_transactions
 (id, wallet_id, transaction_type, purpose, amount, balance_after,
  pending_balance_after, status, idempotency_key, description, reference_type,
  reference_id, created_at, updated_at)
SELECT md5('report-tutor-income:' || n)::uuid,
       'a0000000-0000-4000-8000-000000000124', 'CREDIT', 'TUTOR_EARNING',
       200000, n * 200000, 0, 'COMPLETED', 'report-tutor-income-' || n,
       'Thu nhập buổi học đã hoàn thành', 'LESSON',
       md5(c.id::text || ':report-completed-lesson:' || n)::uuid,
       now() - (n + 2) * interval '1 day', now() - (n + 2) * interval '1 day'
FROM contracts c
JOIN tutors t ON t.id=c.tutor_id
CROSS JOIN generate_series(1, 5) n
WHERE t.user_id='9da38ede-06b3-5316-912b-56d5ee09ff40'
ON CONFLICT (id) DO NOTHING;

-- Show realistic payment states alongside the settled contract payments.
INSERT INTO payments (id,user_id,amount,payment_method,payment_type,status,
                      transaction_code,note,created_at,updated_at)
VALUES
 ('a0000000-0000-4000-8000-000000000401','aa0c6d3e-76ec-5482-8e5d-4cc2270de36a',
  250000,'VNPAY','WALLET_TOP_UP','PENDING','PT2611234001','Nạp ví đang chờ thanh toán',now()-interval '3 hours',now()-interval '3 hours'),
 ('a0000000-0000-4000-8000-000000000402','30fc2221-8718-5142-9ecc-17c40c939211',
  300000,'VNPAY','WALLET_TOP_UP','FAILED','PT2611234002','Giao dịch chưa hoàn tất',now()-interval '2 days',now()-interval '2 days')
ON CONFLICT (id) DO NOTHING;

UPDATE payments SET transaction_code='PT2611231001' WHERE id='a0000000-0000-4000-8000-000000000091';
UPDATE payments SET transaction_code='PT2611231002' WHERE id='a0000000-0000-4000-8000-000000000092';
UPDATE payments SET transaction_code='PT2611231003' WHERE id='a0000000-0000-4000-8000-000000000093';
UPDATE payments SET transaction_code='PT2611232001' WHERE id='a0000000-0000-4000-8000-000000000123';
UPDATE payments SET transaction_code='PT2611233001' WHERE id='a0000000-0000-4000-8000-000000000131';
UPDATE payments SET transaction_code='PT2611233002' WHERE id='a0000000-0000-4000-8000-000000000132';
COMMIT;
