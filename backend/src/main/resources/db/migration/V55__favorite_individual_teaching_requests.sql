-- Earlier versions saved one tutor and remembered only its most recent posting.
-- Keep that posting (or choose one existing posting for legacy rows) when switching
-- the uniqueness rule to one student + one teaching request.
UPDATE favorite_tutors f
SET teaching_request_id = (
    SELECT tr.id FROM teaching_requests tr
    WHERE tr.tutor_id = f.tutor_id
    ORDER BY (tr.status = 'OPEN') DESC, tr.created_at DESC, tr.id
    LIMIT 1
)
WHERE f.teaching_request_id IS NULL;

ALTER TABLE favorite_tutors DROP CONSTRAINT uk_favorite_tutors_student_tutor;
ALTER TABLE favorite_tutors
    ADD CONSTRAINT uk_favorite_tutors_student_request UNIQUE (student_id, teaching_request_id);
