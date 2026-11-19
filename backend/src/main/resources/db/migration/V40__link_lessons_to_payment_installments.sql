ALTER TABLE lessons
    ADD COLUMN IF NOT EXISTS payment_installment_id uuid;

ALTER TABLE lessons
    ADD CONSTRAINT fk_lessons_payment_installment
    FOREIGN KEY (payment_installment_id)
    REFERENCES contract_payment_installments(id);

CREATE INDEX IF NOT EXISTS idx_lessons_payment_installment_id
    ON lessons(payment_installment_id);
