ALTER TABLE complaints
    DROP CONSTRAINT IF EXISTS ck_complaints_status;

ALTER TABLE complaints
    ADD CONSTRAINT ck_complaints_status CHECK (
        status IS NULL OR status IN (
            'PENDING', 'IN_REVIEW', 'AWAITING_EVIDENCE', 'RESOLVED', 'REJECTED', 'CANCELLED'
        )
    );

CREATE INDEX IF NOT EXISTS idx_complaints_status_created_at
    ON complaints(status, created_at)
    WHERE deleted_at IS NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_event_type;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

-- V16 accidentally attached this wallet constraint name to notifications.
ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_reference_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_event_type CHECK (
        event_type IS NULL OR event_type IN (
            'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
            'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
            'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
            'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
            'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
            'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
            'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED'
        )
    );

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT',
            'COMPLAINT'
        )
    );
