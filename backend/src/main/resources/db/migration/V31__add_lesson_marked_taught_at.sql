ALTER TABLE lessons
    ADD COLUMN IF NOT EXISTS marked_taught_at timestamp;

-- A PENDING_CONFIRMATION lesson cannot be edited, so updated_at is the moment it was marked as taught.
UPDATE lessons
SET marked_taught_at = updated_at
WHERE status = 'PENDING_CONFIRMATION'
  AND marked_taught_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_lessons_status_marked_taught_at
    ON lessons(status, marked_taught_at)
    WHERE deleted_at IS NULL;
