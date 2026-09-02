-- Extend review metadata and custom-subject support for databases at V1-V3.
-- IF EXISTS/IF NOT EXISTS keep this migration safe for local databases that
-- already received part of these changes manually.

ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS rejection_reason varchar(500),
    ADD COLUMN IF NOT EXISTS reviewed_by uuid REFERENCES employees (id),
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp;

ALTER TABLE teaching_requests
    ADD COLUMN IF NOT EXISTS custom_subject_name varchar(100),
    ADD COLUMN IF NOT EXISTS rejection_reason varchar(500),
    ADD COLUMN IF NOT EXISTS reviewed_by uuid REFERENCES employees (id),
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp;

ALTER TABLE teaching_requests
    DROP CONSTRAINT IF EXISTS ck_teaching_requests_status;

ALTER TABLE teaching_requests
    ADD CONSTRAINT ck_teaching_requests_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_teaching_requests_status
    ON teaching_requests (status);

CREATE INDEX IF NOT EXISTS idx_teaching_requests_custom_subject_name
    ON teaching_requests (custom_subject_name);
