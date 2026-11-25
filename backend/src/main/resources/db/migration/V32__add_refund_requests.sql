-- A refund request disputes lessons the tutor marked as taught that did not take place.
CREATE TABLE refund_requests (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL REFERENCES contracts (id),
    student_id uuid NOT NULL REFERENCES students (id),
    employee_id uuid REFERENCES employees (id),
    description text NOT NULL,
    status varchar(20) NOT NULL,
    tutor_response text,
    tutor_responded_at timestamp,
    tutor_response_deadline timestamp NOT NULL,
    -- Snapshot written when the request is approved.
    unit_price numeric(15, 2),
    refund_amount numeric(15, 2),
    tutor_share_amount numeric(15, 2),
    note text,
    rejection_reason varchar(500),
    reviewed_at timestamp,
    wallet_transaction_id uuid UNIQUE REFERENCES wallet_transactions (id),
    tutor_wallet_transaction_id uuid UNIQUE REFERENCES wallet_transactions (id),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_refund_requests_status CHECK (status IN (
        'PENDING', 'IN_REVIEW', 'REFUNDED', 'REJECTED', 'CANCELLED'
    )),
    CONSTRAINT ck_refund_requests_amounts CHECK (
        (refund_amount IS NULL OR refund_amount >= 0) AND (tutor_share_amount IS NULL OR tutor_share_amount >= 0)
    )
);

-- A contract has at most one refund request waiting for a decision.
CREATE UNIQUE INDEX uq_refund_requests_open_contract
    ON refund_requests (contract_id)
    WHERE status IN ('PENDING', 'IN_REVIEW') AND deleted_at IS NULL;

CREATE INDEX idx_refund_requests_status_created_at
    ON refund_requests (status, created_at)
    WHERE deleted_at IS NULL;

-- Each disputed lesson carries the student's claim, the tutor's version and the reviewer's decision.
CREATE TABLE refund_request_lessons (
    id uuid PRIMARY KEY,
    refund_request_id uuid NOT NULL REFERENCES refund_requests (id),
    lesson_id uuid NOT NULL REFERENCES lessons (id),
    claimed_outcome varchar(20) NOT NULL,
    tutor_position varchar(20),
    decision varchar(20),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT uq_refund_request_lessons UNIQUE (refund_request_id, lesson_id),
    CONSTRAINT ck_refund_request_lessons_claim CHECK (claimed_outcome IN ('TUTOR_ABSENT', 'STUDENT_ABSENT')),
    CONSTRAINT ck_refund_request_lessons_position CHECK (
        tutor_position IS NULL OR tutor_position IN ('TAUGHT', 'TUTOR_ABSENT', 'STUDENT_ABSENT')
    ),
    CONSTRAINT ck_refund_request_lessons_decision CHECK (
        decision IS NULL OR decision IN ('TAUGHT', 'TUTOR_ABSENT', 'STUDENT_ABSENT')
    )
);

CREATE INDEX idx_refund_request_lessons_lesson ON refund_request_lessons (lesson_id);

ALTER TABLE evidences
    ADD COLUMN IF NOT EXISTS refund_request_id uuid REFERENCES refund_requests (id);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refunded_amount numeric(15, 2) NOT NULL DEFAULT 0;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_refunded_amount CHECK (
        refunded_amount >= 0 AND (amount IS NULL OR refunded_amount <= amount)
    );

ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_reference_type;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'CONTRACT', 'LESSON', 'PAYMENT', 'WALLET_TRANSACTION',
            'STUDYING_REQUEST', 'TEACHING_REQUEST', 'WITHDRAWAL_REQUEST', 'REFUND_REQUEST'
        )
    );

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT',
            'COMPLAINT', 'USER', 'LESSON', 'WITHDRAWAL_REQUEST', 'REFUND_REQUEST'
        )
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
            'TUTOR_PROFILE_APPROVED', 'TUTOR_PROFILE_REJECTED',
            'TEACHING_REQUEST_REVIEW_REQUIRED',
            'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
            'STUDYING_REQUEST_REVIEW_REQUIRED',
            'STUDYING_REQUEST_APPROVED', 'STUDYING_REQUEST_REJECTED',
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
            'COMPLAINT_RECEIVED_AGAINST_YOU', 'COMPLAINT_AGAINST_YOU_RESOLVED',
            'COMPLAINT_AGAINST_YOU_REJECTED',
            'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
            'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
            'WITHDRAWAL_REVIEW_REQUIRED',
            'REFUND_REQUEST_REVIEW_REQUIRED', 'REFUND_REQUEST_SUBMITTED', 'REFUND_REQUEST_TUTOR_RESPONDED',
            'REFUND_REQUEST_APPROVED', 'REFUND_REQUEST_REJECTED'
        )
    );
