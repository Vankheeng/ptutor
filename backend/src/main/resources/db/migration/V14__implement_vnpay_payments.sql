-- Contract payment periods are now machine-readable so installment amounts can be derived server-side.
UPDATE contracts
SET payment_period = upper(btrim(payment_period))
WHERE payment_period IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM contracts
        WHERE payment_period NOT IN ('PER_LESSON', 'WEEKLY', 'MONTHLY', 'PACKAGE')
    ) THEN
        RAISE EXCEPTION 'contracts.payment_period contains unsupported legacy values; normalize them before V14';
    END IF;
END $$;

ALTER TABLE contracts
    ADD CONSTRAINT ck_contracts_payment_period
        CHECK (payment_period IN ('PER_LESSON', 'WEEKLY', 'MONTHLY', 'PACKAGE'));

CREATE TABLE contract_payment_installments (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL REFERENCES contracts (id),
    lesson_id uuid UNIQUE REFERENCES lessons (id),
    sequence_number integer NOT NULL,
    payment_period varchar(30) NOT NULL CHECK (payment_period IN ('PER_LESSON', 'WEEKLY', 'MONTHLY', 'PACKAGE')),
    amount decimal(15,2) NOT NULL CHECK (amount > 0),
    due_date date,
    status varchar(30) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT uq_contract_payment_installment_sequence UNIQUE (contract_id, sequence_number)
);

CREATE INDEX idx_contract_payment_installments_contract_status
    ON contract_payment_installments (contract_id, status, sequence_number);

ALTER TABLE payments
    ADD COLUMN payment_installment_id uuid REFERENCES contract_payment_installments (id),
    ADD COLUMN provider_transaction_no varchar(100),
    ADD COLUMN provider_response_code varchar(20),
    ADD COLUMN expires_at timestamp,
    ADD COLUMN provider_payload jsonb;

ALTER TABLE payments
    DROP CONSTRAINT ck_payments_reference_type,
    ADD CONSTRAINT ck_payments_reference_type
        CHECK (reference_type IS NULL OR reference_type IN (
            'CONTRACT', 'LESSON', 'PAYMENT', 'WALLET_TRANSACTION', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
            'WITHDRAWAL_REQUEST'
        ));

CREATE UNIQUE INDEX uq_payments_transaction_code
    ON payments (transaction_code)
    WHERE transaction_code IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_payments_provider_transaction_no
    ON payments (provider_transaction_no)
    WHERE provider_transaction_no IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_payments_pending_reference
    ON payments (user_id, payment_type, reference_type, reference_id, COALESCE(payment_installment_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'PENDING' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_payments_paid_installment
    ON payments (payment_installment_id)
    WHERE payment_installment_id IS NOT NULL AND status = 'PAID' AND deleted_at IS NULL;
