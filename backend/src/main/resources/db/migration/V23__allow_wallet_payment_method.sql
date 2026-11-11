ALTER TABLE payments DROP CONSTRAINT ck_payments_payment_method;

ALTER TABLE payments ADD CONSTRAINT ck_payments_payment_method CHECK (
    payment_method IS NULL OR payment_method IN ('VNPAY', 'WALLET')
);
