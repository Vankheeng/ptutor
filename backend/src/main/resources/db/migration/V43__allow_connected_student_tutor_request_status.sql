ALTER TABLE student_tutor_requests
    DROP CONSTRAINT IF EXISTS ck_student_tutor_requests_status;

ALTER TABLE student_tutor_requests
    ADD CONSTRAINT ck_student_tutor_requests_status
    CHECK (
        status IS NULL
        OR status IN ('PENDING', 'ACCEPTED', 'CONNECTED', 'REJECTED', 'CANCELLED')
    );
