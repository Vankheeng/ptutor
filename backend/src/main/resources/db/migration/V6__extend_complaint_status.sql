ALTER TABLE complaints
    DROP CONSTRAINT IF EXISTS ck_complaints_status;

ALTER TABLE complaints
    ADD CONSTRAINT ck_complaints_status
        CHECK (status IS NULL OR status IN ('PENDING', 'IN_REVIEW', 'RESOLVED', 'REJECTED', 'CANCELLED'));
