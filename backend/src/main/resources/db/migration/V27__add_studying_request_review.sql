ALTER TABLE studying_requests
    ADD COLUMN IF NOT EXISTS reviewed_by uuid REFERENCES employees (id),
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp,
    ADD COLUMN IF NOT EXISTS rejection_reason varchar(500);

CREATE INDEX IF NOT EXISTS idx_studying_requests_status
    ON studying_requests(status);

ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_purpose;

ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_type_purpose;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_purpose CHECK (
        purpose IN (
            'TOP_UP', 'TUTOR_EARNING', 'CONTRACT_REFUND', 'TEACHING_REQUEST_REFUND',
            'STUDYING_REQUEST_REFUND', 'WITHDRAWAL_REVERSAL', 'TUITION_PAYMENT',
            'STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE', 'WITHDRAWAL'
        )
    ),
    ADD CONSTRAINT ck_wallet_transactions_type_purpose CHECK (
        (transaction_type = 'CREDIT' AND purpose IN (
            'TOP_UP', 'TUTOR_EARNING', 'CONTRACT_REFUND',
            'TEACHING_REQUEST_REFUND', 'STUDYING_REQUEST_REFUND', 'WITHDRAWAL_REVERSAL'
        ))
        OR
        (transaction_type = 'DEBIT' AND purpose IN (
            'TUITION_PAYMENT', 'STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE', 'WITHDRAWAL'
        ))
    );

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_event_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_event_type CHECK (
        event_type IS NULL OR event_type IN (
            'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
            'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
            'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
            'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
            'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
            'CERTIFICATE_REVIEW_REQUIRED', 'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
            'TEACHING_REQUEST_REVIEW_REQUIRED',
            'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
            'STUDYING_REQUEST_REVIEW_REQUIRED',
            'STUDYING_REQUEST_APPROVED', 'STUDYING_REQUEST_REJECTED',
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
            'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
            'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
            'WITHDRAWAL_REVIEW_REQUIRED'
        )
    );
