CREATE TABLE withdrawal_requests (
    id uuid PRIMARY KEY,
    wallet_id uuid NOT NULL REFERENCES wallets (id),
    amount decimal(15,2) NOT NULL CHECK (amount > 0),
    bank_code varchar(20) NOT NULL,
    bank_name varchar(100) NOT NULL,
    encrypted_account_number varchar(512) NOT NULL,
    account_number_last_four varchar(4) NOT NULL CHECK (account_number_last_four ~ '^[0-9]{4}$'),
    account_holder_name varchar(150) NOT NULL,
    note varchar(500),
    idempotency_key varchar(100) NOT NULL,
    status varchar(30) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'CANCELLED', 'REJECTED', 'COMPLETED')),
    reviewed_by_user_id uuid REFERENCES users (id),
    reviewed_at timestamp,
    rejection_reason varchar(500),
    completed_at timestamp,
    transfer_reference varchar(100),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    UNIQUE (wallet_id, idempotency_key)
);
CREATE INDEX idx_withdrawal_requests_wallet_created_at ON withdrawal_requests (wallet_id, created_at DESC, id DESC);
