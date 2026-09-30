ALTER TABLE payments DROP CONSTRAINT ck_payments_payment_type;

ALTER TABLE payments ADD CONSTRAINT ck_payments_payment_type CHECK (
    payment_type IS NULL OR payment_type IN (
        'WALLET_TOP_UP',
        'TUITION_PAYMENT',
        'STUDYING_REQUEST_FEE',
        'TEACHING_REQUEST_FEE'
    )
);
