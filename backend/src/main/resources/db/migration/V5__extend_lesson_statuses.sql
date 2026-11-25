-- Lesson lifecycle: tutor marks a lesson as taught, then the student confirms
-- or the settlement job confirms it after the dispute window expires.
ALTER TABLE lessons
    DROP CONSTRAINT IF EXISTS ck_lessons_status;

ALTER TABLE lessons
    ADD CONSTRAINT ck_lessons_status
        CHECK (status IS NULL OR status IN (
            'SCHEDULED',
            'PENDING_CONFIRMATION',
            'CONFIRMED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED'
        ));

-- A lesson can produce at most one completed credit transaction.
CREATE UNIQUE INDEX IF NOT EXISTS ux_wallet_lesson_credit
    ON wallet_transactions (reference_type, reference_id, transaction_type)
    WHERE reference_type = 'LESSON'
      AND transaction_type = 'CREDIT'
      AND deleted_at IS NULL;
