DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM wallet_transactions) THEN
        RAISE EXCEPTION
            'Cannot add a mandatory wallet transaction purpose while wallet_transactions contains existing rows';
    END IF;
END
$$;

ALTER TABLE wallet_transactions
    ADD COLUMN purpose varchar(50) NOT NULL,
    ADD COLUMN pending_balance_after decimal(15,2) NOT NULL,
    ADD COLUMN idempotency_key varchar(100) NOT NULL;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_purpose CHECK (
        purpose IN (
            'TOP_UP',
            'TUTOR_EARNING',
            'CONTRACT_REFUND',
            'WITHDRAWAL_REVERSAL',
            'TUITION_PAYMENT',
            'STUDYING_REQUEST_FEE',
            'TEACHING_REQUEST_FEE',
            'WITHDRAWAL'
        )
    ),
    ADD CONSTRAINT ck_wallet_transactions_type_purpose CHECK (
        (transaction_type = 'CREDIT' AND purpose IN (
            'TOP_UP', 'TUTOR_EARNING', 'CONTRACT_REFUND', 'WITHDRAWAL_REVERSAL'
        ))
        OR
        (transaction_type = 'DEBIT' AND purpose IN (
            'TUITION_PAYMENT', 'STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE', 'WITHDRAWAL'
        ))
    ),
    ADD CONSTRAINT uq_wallet_transactions_idempotency UNIQUE (wallet_id, idempotency_key);

CREATE INDEX idx_wallet_transactions_wallet_created_at
    ON wallet_transactions (wallet_id, created_at DESC, id DESC);

ALTER TABLE withdrawal_requests
    ADD COLUMN wallet_transaction_id uuid REFERENCES wallet_transactions (id);

ALTER TABLE withdrawal_requests
    ADD CONSTRAINT uq_withdrawal_requests_wallet_transaction UNIQUE (wallet_transaction_id);
