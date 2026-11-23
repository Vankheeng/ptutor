ALTER TABLE tutor_student_requests
    ADD COLUMN accepted_at timestamp;

UPDATE tutor_student_requests
SET accepted_at = updated_at
WHERE status = 'ACCEPTED'
  AND accepted_at IS NULL;
