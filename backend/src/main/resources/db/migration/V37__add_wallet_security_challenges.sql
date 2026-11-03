CREATE TABLE IF NOT EXISTS wallet_security_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    purpose VARCHAR(32) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP NULL,
    invalidated_at TIMESTAMP NULL,
    amount NUMERIC(15, 2) NULL,
    note VARCHAR(500) NULL,
    bank_code VARCHAR(20) NULL,
    bank_name VARCHAR(100) NULL,
    encrypted_account_number VARCHAR(512) NULL,
    account_number_last_four VARCHAR(4) NULL,
    account_holder_name VARCHAR(150) NULL,
    idempotency_key VARCHAR(100) NULL,
    withdrawal_request_id UUID NULL REFERENCES withdrawal_requests(id),
    CONSTRAINT ck_wallet_security_challenge_purpose
        CHECK (purpose IN ('WITHDRAWAL', 'BANK_ACCOUNT_UPDATE')),
    CONSTRAINT ck_wallet_security_challenge_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_wallet_security_challenge_payload CHECK (
        (purpose = 'WITHDRAWAL' AND amount IS NOT NULL AND bank_code IS NOT NULL
            AND bank_name IS NOT NULL AND encrypted_account_number IS NOT NULL
            AND account_number_last_four IS NOT NULL AND account_holder_name IS NOT NULL
            AND idempotency_key IS NOT NULL)
        OR (purpose = 'BANK_ACCOUNT_UPDATE' AND amount IS NULL AND bank_code IS NOT NULL
            AND bank_name IS NOT NULL AND encrypted_account_number IS NOT NULL
            AND account_number_last_four IS NOT NULL AND account_holder_name IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS ix_wallet_security_challenge_user_purpose_created
    ON wallet_security_challenges(user_id, purpose, created_at DESC);
