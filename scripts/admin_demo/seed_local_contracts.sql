-- Supply three active contracts for the local admin demo fixtures.
-- Run after recommendation_demo/seed_demo.py and before admin_demo/seed.py.
BEGIN;

WITH demo_pairs AS (
    SELECT n,
           (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
            WHERE u.email = 'demo.student' || n || '@ptutor.local') AS student_id,
           (SELECT tr.id FROM teaching_requests tr JOIN tutors t ON t.id = tr.tutor_id
            JOIN users u ON u.id = t.user_id
            WHERE u.email = 'demo.tutor' || lpad(n::text, 2, '0') || '@ptutor.local'
              AND tr.status = 'OPEN' ORDER BY tr.id LIMIT 1) AS teaching_request_id
    FROM generate_series(1, 3) AS n
)
INSERT INTO student_tutor_requests
    (id, student_id, grade_id, teaching_request_id, proposed_price,
     learning_mode, status, responded_at)
SELECT md5('admin-demo-application-' || p.n)::uuid, p.student_id,
       (SELECT gtr.grade_id FROM grade_teaching_requests gtr
        WHERE gtr.teaching_request_id = p.teaching_request_id ORDER BY gtr.id LIMIT 1),
       p.teaching_request_id, 200000, 'ONLINE', 'ACCEPTED', now() - interval '10 days'
FROM demo_pairs p
ON CONFLICT (id) DO NOTHING;

WITH demo_pairs AS (
    SELECT n, a.id AS application_id, a.student_id, t.id AS tutor_id,
           tr.subject_id, a.grade_id, s.user_id AS student_user_id,
           t.user_id AS tutor_user_id
    FROM generate_series(1, 3) AS n
    JOIN student_tutor_requests a ON a.id = md5('admin-demo-application-' || n)::uuid
    JOIN teaching_requests tr ON tr.id = a.teaching_request_id
    JOIN tutors t ON t.id = tr.tutor_id
    JOIN students s ON s.id = a.student_id
)
INSERT INTO contracts
    (id, student_id, tutor_id, subject_id, grade_id, teaching_mode,
     price, payment_period, total_lession, start_date, end_date, status,
     signed_at, created_by_user_id, signed_by_user_id,
     student_tutor_request_id, preferred_schedule)
SELECT md5('admin-demo-contract-' || p.n)::uuid, p.student_id, p.tutor_id,
       p.subject_id, p.grade_id, 'ONLINE', 200000, 'PER_LESSON', 8,
       current_date - 10, current_date + 50, 'ACTIVE', now() - interval '9 days',
       p.tutor_user_id, p.student_user_id, p.application_id,
       'Mon/Wed 18:00-19:30'
FROM demo_pairs p
ON CONFLICT (id) DO NOTHING;

COMMIT;
