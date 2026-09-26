ALTER TABLE users
    ADD COLUMN suspension_type varchar(20),
    ADD COLUMN suspension_reason text,
    ADD COLUMN suspended_at timestamp,
    ADD COLUMN suspended_until timestamp,
    ADD COLUMN suspended_by_user_id uuid REFERENCES users (id),
    ADD COLUMN reactivated_at timestamp,
    ADD COLUMN reactivated_by_user_id uuid REFERENCES users (id),
    ADD COLUMN reactivation_reason text,
    ADD COLUMN suspension_count integer NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_suspension_type CHECK (
        suspension_type IS NULL OR suspension_type IN ('TEMPORARY', 'PERMANENT')
    ),
    ADD CONSTRAINT ck_users_suspension_period CHECK (
        suspension_type IS NULL
        OR (
            suspension_type = 'TEMPORARY'
            AND suspended_at IS NOT NULL
            AND suspended_until IS NOT NULL
            AND suspended_until > suspended_at
        )
        OR (
            suspension_type = 'PERMANENT'
            AND suspended_at IS NOT NULL
            AND suspended_until IS NULL
        )
    ),
    ADD CONSTRAINT ck_users_blocked_metadata CHECK (
        status <> 'BLOCKED'
        OR (
            suspension_type IS NOT NULL
            AND suspension_reason IS NOT NULL
            AND btrim(suspension_reason) <> ''
            AND suspended_at IS NOT NULL
            AND suspended_by_user_id IS NOT NULL
        )
    ),
    ADD CONSTRAINT ck_users_suspension_count CHECK (suspension_count >= 0);

CREATE INDEX idx_users_status_suspended_until
    ON users (status, suspended_until)
    WHERE deleted_at IS NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_event_type;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

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
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
            'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED'
        )
    );

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT',
            'COMPLAINT', 'USER'
        )
    );
