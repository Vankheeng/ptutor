ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS ck_payments_payment_type;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_payment_type
        CHECK (payment_type IS NULL OR payment_type IN (
            'TUITION_PAYMENT',
            'STUDYING_REQUEST_FEE',
            'TEACHING_REQUEST_FEE'
        ));
