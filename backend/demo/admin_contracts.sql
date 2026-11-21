-- Local-only, repeatable examples for the administrator contract list.
-- Uses existing completed contracts as the source of valid participants and terms.
-- It does not update or delete existing records.
BEGIN;

WITH sources AS (
    SELECT c.*, row_number() OVER (ORDER BY c.id) AS sample_number
    FROM contracts c
    WHERE c.status = 'COMPLETED'
      AND c.deleted_at IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM contracts renewal
          WHERE renewal.renewed_from_contract_id = c.id
            AND renewal.deleted_at IS NULL
            AND renewal.status <> 'CANCELLED'
      )
), examples AS (
    SELECT 1 AS sample_number, 'PENDING' AS sample_status, 'admin-contract-demo-pending-1' AS seed_key
    UNION ALL SELECT 2, 'PENDING', 'admin-contract-demo-pending-2'
    UNION ALL SELECT 3, 'CANCELLED', 'admin-contract-demo-cancelled-1'
    UNION ALL SELECT 4, 'CANCELLED', 'admin-contract-demo-cancelled-2'
)
INSERT INTO contracts (
    id, student_id, tutor_id, subject_id, grade_id, teaching_mode, price,
    payment_period, total_lession, start_date, end_date, status,
    signed_at, created_by_user_id, signed_by_user_id, renewed_from_contract_id,
    preferred_schedule, created_at, updated_at
)
SELECT md5(e.seed_key)::uuid, c.student_id, c.tutor_id, c.subject_id, c.grade_id,
       c.teaching_mode, c.price, c.payment_period, c.total_lession,
       CASE WHEN e.sample_status = 'PENDING' THEN current_date + 7 ELSE current_date - 30 END,
       CASE WHEN e.sample_status = 'PENDING' THEN current_date + 67 ELSE current_date + 30 END,
       e.sample_status, NULL, c.created_by_user_id, NULL, c.id,
       c.preferred_schedule, now() - interval '1 day' * e.sample_number, now()
FROM examples e
JOIN sources c ON c.sample_number = e.sample_number
ON CONFLICT (id) DO NOTHING;

COMMIT;
