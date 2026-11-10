ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'PAYMENT', 'CONTRACT', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'TUTOR_STUDENT_REQUEST', 'STUDENT_TUTOR_REQUEST', 'CERTIFICATE', 'INSTALLMENT',
            'COMPLAINT', 'USER', 'LESSON', 'WALLET_TRANSACTION',
            'WITHDRAWAL_REQUEST', 'REFUND_REQUEST'
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
            'WITHDRAWAL_REVIEW_REQUIRED', 'WITHDRAWAL_REQUEST_CREATED',
            'WITHDRAWAL_REQUEST_CANCELLED', 'WALLET_BALANCE_CHANGED',
            'REFUND_REQUEST_REVIEW_REQUIRED', 'REFUND_REQUEST_SUBMITTED',
            'REFUND_REQUEST_TUTOR_RESPONDED', 'REFUND_REQUEST_APPROVED',
            'REFUND_REQUEST_REJECTED'
        )
    );

INSERT INTO notifications (
    id,
    user_id,
    title,
    content,
    type,
    event_type,
    reference_type,
    reference_id,
    is_read,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    wallet.user_id,
    'Đã hủy yêu cầu rút tiền',
    'Yêu cầu rút ' || withdrawal.amount::text
        || ' VND đã được hủy. Tiền đã được cộng lại vào ví.',
    'PAYMENT',
    'WITHDRAWAL_REQUEST_CANCELLED',
    'WITHDRAWAL_REQUEST',
    withdrawal.id::text,
    false,
    withdrawal.updated_at,
    withdrawal.updated_at
FROM withdrawal_requests withdrawal
JOIN wallets wallet ON wallet.id = withdrawal.wallet_id
WHERE withdrawal.status = 'CANCELLED'
  AND NOT EXISTS (
      SELECT 1
      FROM notifications notification
      WHERE notification.user_id = wallet.user_id
        AND notification.event_type = 'WITHDRAWAL_REQUEST_CANCELLED'
        AND notification.reference_type = 'WITHDRAWAL_REQUEST'
        AND notification.reference_id = withdrawal.id::text
        AND notification.deleted_at IS NULL
  );

INSERT INTO notifications (
    id,
    user_id,
    title,
    content,
    type,
    event_type,
    reference_type,
    reference_id,
    is_read,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    wallet.user_id,
    'Yêu cầu rút tiền đã được ghi nhận',
    'Yêu cầu rút ' || withdrawal.amount::text
        || ' VND đã được tạo và đang chờ xử lý.',
    'PAYMENT',
    'WITHDRAWAL_REQUEST_CREATED',
    'WITHDRAWAL_REQUEST',
    withdrawal.id::text,
    false,
    withdrawal.created_at,
    withdrawal.created_at
FROM withdrawal_requests withdrawal
JOIN wallets wallet ON wallet.id = withdrawal.wallet_id
WHERE NOT EXISTS (
    SELECT 1
    FROM notifications notification
    WHERE notification.user_id = wallet.user_id
      AND notification.event_type = 'WITHDRAWAL_REQUEST_CREATED'
      AND notification.reference_type = 'WITHDRAWAL_REQUEST'
      AND notification.reference_id = withdrawal.id::text
      AND notification.deleted_at IS NULL
);
