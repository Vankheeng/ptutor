-- Tutor earnings for the completed lessons on the other two active contracts.
BEGIN;
INSERT INTO wallets (id, user_id, balance, pending_balance)
SELECT md5(t.user_id::text || ':report-wallet')::uuid, t.user_id, 400000, 0
FROM contracts c JOIN tutors t ON t.id=c.tutor_id
WHERE c.status='ACTIVE' AND t.user_id<>'9da38ede-06b3-5316-912b-56d5ee09ff40'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO wallet_transactions
 (id, wallet_id, transaction_type, purpose, amount, balance_after,
  pending_balance_after, status, idempotency_key, description, reference_type,
  reference_id, created_at, updated_at)
SELECT md5('report-other-tutor-income:' || l.id::text)::uuid,
       w.id, 'CREDIT', 'TUTOR_EARNING', 200000,
       row_number() OVER (PARTITION BY t.user_id ORDER BY l.date, l.id) * 200000,
       0, 'COMPLETED', 'report-tutor-lesson-' || l.id::text,
       'Thu nhập buổi học đã hoàn thành', 'LESSON', l.id,
       l.date + interval '2 hours', l.date + interval '2 hours'
FROM lessons l
JOIN contracts c ON c.id=l.contract_id
JOIN tutors t ON t.id=c.tutor_id
JOIN wallets w ON w.user_id=t.user_id
WHERE l.status='COMPLETED' AND t.user_id<>'9da38ede-06b3-5316-912b-56d5ee09ff40'
  AND l.title LIKE 'Buổi %: Ôn tập và luyện bài tập'
ON CONFLICT (id) DO NOTHING;
COMMIT;
