ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_purpose;

ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_type_purpose;

ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_reference_type;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_purpose CHECK (
        purpose IN (
            'TOP_UP', 'TUTOR_EARNING', 'CONTRACT_REFUND', 'TEACHING_REQUEST_REFUND',
            'WITHDRAWAL_REVERSAL', 'TUITION_PAYMENT', 'STUDYING_REQUEST_FEE',
            'TEACHING_REQUEST_FEE', 'WITHDRAWAL'
        )
    ),
    ADD CONSTRAINT ck_wallet_transactions_type_purpose CHECK (
        (transaction_type = 'CREDIT' AND purpose IN (
            'TOP_UP', 'TUTOR_EARNING', 'CONTRACT_REFUND',
            'TEACHING_REQUEST_REFUND', 'WITHDRAWAL_REVERSAL'
        ))
        OR
        (transaction_type = 'DEBIT' AND purpose IN (
            'TUITION_PAYMENT', 'STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE', 'WITHDRAWAL'
        ))
    );

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS event_type varchar(80),
    ADD COLUMN IF NOT EXISTS reference_type varchar(50),
    ADD COLUMN IF NOT EXISTS deduplication_key varchar(255);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created_at
    ON notifications(user_id, is_read, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_deduplication_key
    ON notifications(deduplication_key)
    WHERE deduplication_key IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_event_type;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_event_type CHECK (
    event_type IS NULL OR event_type IN (
        'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
        'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
        'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
        'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
        'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
        'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
        'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED'
        )
    ),
    ADD CONSTRAINT ck_wallet_transactions_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'CONTRACT', 'LESSON', 'PAYMENT', 'WALLET_TRANSACTION',
            'WITHDRAWAL_REQUEST', 'TEACHING_REQUEST'
        )
    );

ALTER TABLE notifications ADD CONSTRAINT ck_notifications_reference_type CHECK (
    reference_type IS NULL OR reference_type IN (
        'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
        'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT'
    )
);
