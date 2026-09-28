-- Reconcile constraints introduced by parallel notification, review, and wallet work.
-- This migration only replaces CHECK constraints; it does not modify application data.
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
            'CERTIFICATE_REVIEW_REQUIRED', 'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
            'TEACHING_REQUEST_REVIEW_REQUIRED',
            'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
            'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
            'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
            'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
            'WITHDRAWAL_REVIEW_REQUIRED'
        )
    );

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT',
            'COMPLAINT', 'USER', 'LESSON', 'WITHDRAWAL_REQUEST'
        )
    );

ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_reference_type;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'CONTRACT', 'LESSON', 'PAYMENT', 'WALLET_TRANSACTION',
            'STUDYING_REQUEST', 'TEACHING_REQUEST', 'WITHDRAWAL_REQUEST'
        )
    );
