-- ContractService marks a tutor's accepted offer CONNECTED when both parties sign.
ALTER TABLE tutor_student_requests
    DROP CONSTRAINT IF EXISTS ck_tutor_student_requests_status;

ALTER TABLE tutor_student_requests
    ADD CONSTRAINT ck_tutor_student_requests_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'CONNECTED', 'REJECTED', 'CANCELLED'));
