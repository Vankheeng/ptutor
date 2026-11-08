-- Demo data for testing the tutor's student-request workflow.
-- Safe to run repeatedly: fixed IDs are checked before inserting.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH selected AS (
    SELECT
        (SELECT id FROM tutors WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1) AS tutor_id,
        (SELECT id FROM subjects WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1) AS subject_id
)
INSERT INTO teaching_requests (
    id, tutor_id, subject_id, title, note, quantity, detail_address,
    expected_price, teaching_mode, preferred_schedule, description, status
)
SELECT
    'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid,
    tutor_id,
    subject_id,
    'Ôn tập học kỳ và củng cố kiến thức',
    'Dữ liệu mẫu để kiểm tra trang đề nghị từ học viên.',
    1,
    'Số 1, đường Demo, Quận trung tâm',
    180000,
    'ONLINE',
    'Thứ 3 và Thứ 5, 19:00 - 20:30',
    'Mong muốn được hỗ trợ ôn tập và giải các dạng bài còn yếu.',
    'OPEN'
FROM selected
WHERE tutor_id IS NOT NULL
  AND subject_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM teaching_requests
      WHERE id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid
  );

WITH selected AS (
    SELECT (SELECT id FROM grades WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1) AS grade_id
)
INSERT INTO grade_teaching_requests (id, grade_id, teaching_request_id)
SELECT gen_random_uuid(), grade_id, 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid
FROM selected
WHERE grade_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM teaching_requests
      WHERE id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid
  )
  AND NOT EXISTS (
      SELECT 1 FROM grade_teaching_requests
      WHERE teaching_request_id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid
  );

WITH selected AS (
    SELECT
        (SELECT id FROM students WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1) AS student_id,
        (SELECT id FROM grades WHERE deleted_at IS NULL ORDER BY created_at LIMIT 1) AS grade_id
)
INSERT INTO student_tutor_requests (
    id, student_id, grade_id, teaching_request_id, proposed_price,
    learning_mode, preferred_schedule, message, status
)
SELECT
    'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb002'::uuid,
    student_id,
    grade_id,
    'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid,
    190000,
    'ONLINE',
    'Thứ 3 và Thứ 5, 19:00 - 20:30',
    'Em muốn tập trung vào phần phương trình và bài tập vận dụng.',
    'PENDING'
FROM selected
WHERE student_id IS NOT NULL
  AND grade_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM teaching_requests
      WHERE id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid
  )
  AND NOT EXISTS (
      SELECT 1 FROM student_tutor_requests
      WHERE id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb002'::uuid
  );

-- Repair Vietnamese text for databases where the first run was piped through
-- a non-UTF-8 PowerShell console and stored as question marks.
UPDATE users
SET last_name = U&'Ph\01B0\01A1ng'
WHERE lower(email) = lower('mai.phuong@ptutor.local');

UPDATE teaching_requests
SET title = U&'\00D4n t\1EADp h\1ECDc k\1EF3 v\00E0 c\1EE7ng c\1ED1 ki\1EBFn th\1EE9c',
    note = U&'D\1EEF li\1EC7u m\1EABu \0111\1EC3 ki\1EC3m tra trang \0111\1EC1 ngh\1ECB t\1EEB h\1ECDc vi\00EAn.',
    detail_address = U&'S\1ED1 1, \0111\01B0\1EDDng Demo, Qu\1EADn trung t\00E2m',
    preferred_schedule = U&'Th\1EE9 3 v\00E0 Th\1EE9 5, 19:00 - 20:30',
    description = U&'Mong mu\1ED1n \0111\01B0\1EE3c h\1ED7 tr\1EE3 \00F4n t\1EADp v\00E0 gi\1EA3i c\00E1c d\1EA1ng b\00E0i c\00F2n y\1EBFu.'
WHERE id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb001'::uuid;

UPDATE student_tutor_requests
SET preferred_schedule = U&'Th\1EE9 3 v\00E0 Th\1EE9 5, 19:00 - 20:30',
    message = U&'Em mu\1ED1n t\1EADp trung v\00E0o ph\1EA7n ph\01B0\01A1ng tr\00ECnh v\00E0 b\00E0i t\1EADp v\1EADn d\1EE5ng.'
WHERE id = 'd7d9b3e4-5c88-4f7b-9b54-4f24c42cb002'::uuid;
