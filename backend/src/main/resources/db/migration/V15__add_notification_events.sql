ALTER TABLE notifications ADD COLUMN event_type varchar(80);
ALTER TABLE notifications ADD COLUMN reference_type varchar(50);
ALTER TABLE notifications ADD COLUMN deduplication_key varchar(255);

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_event_type CHECK (
    event_type IS NULL OR event_type IN (
        'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
        'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
        'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
        'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
        'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
        'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED'
    )
);

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_reference_type CHECK (
    reference_type IS NULL OR reference_type IN (
        'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
        'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT'
    )
);

CREATE INDEX idx_notifications_user_read_created_at
    ON notifications(user_id, is_read, created_at DESC);

CREATE UNIQUE INDEX uq_notifications_deduplication_key
    ON notifications(deduplication_key)
    WHERE deduplication_key IS NOT NULL AND deleted_at IS NULL;
