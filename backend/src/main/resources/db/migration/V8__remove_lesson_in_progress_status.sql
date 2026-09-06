-- IN_PROGRESS is derived from the lesson's scheduled time and is not persisted.
-- Preserve existing records by returning them to SCHEDULED before tightening the constraint.
UPDATE lessons
SET status = 'SCHEDULED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'IN_PROGRESS';

ALTER TABLE lessons
    DROP CONSTRAINT IF EXISTS ck_lessons_status;

ALTER TABLE lessons
    ADD CONSTRAINT ck_lessons_status
        CHECK (
            status IS NULL OR status IN (
                'SCHEDULED',
                'PENDING_CONFIRMATION',
                'CONFIRMED',
                'COMPLETED',
                'CANCELLED'
            )
        );
