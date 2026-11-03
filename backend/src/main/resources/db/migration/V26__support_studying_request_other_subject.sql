ALTER TABLE studying_requests
    ALTER COLUMN subject_id DROP NOT NULL;

ALTER TABLE studying_requests
    DROP CONSTRAINT IF EXISTS ck_studying_requests_status;

ALTER TABLE studying_requests
    ADD CONSTRAINT ck_studying_requests_status
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED', 'REJECTED'));
