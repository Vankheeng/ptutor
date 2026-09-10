-- Wallets are required for every active student and tutor. Existing users are
-- backfilled here; newly registered users receive a wallet in AuthService.
ALTER TABLE wallets
    ADD CONSTRAINT ck_wallets_balance_non_negative
        CHECK (balance >= 0),
    ADD CONSTRAINT ck_wallets_pending_balance_non_negative
        CHECK (pending_balance >= 0),
    ADD CONSTRAINT ck_wallets_total_funds_within_limit
        CHECK (balance + pending_balance <= 9999999999999.99);

CREATE TABLE bank_accounts (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users (id),
    bank_code varchar(20) NOT NULL,
    bank_name varchar(100) NOT NULL,
    encrypted_account_number varchar(512) NOT NULL,
    account_number_last_four varchar(4) NOT NULL,
    account_holder_name varchar(150) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_bank_accounts_bank_code_not_blank CHECK (btrim(bank_code) <> ''),
    CONSTRAINT ck_bank_accounts_bank_name_not_blank CHECK (btrim(bank_name) <> ''),
    CONSTRAINT ck_bank_accounts_last_four CHECK (account_number_last_four ~ '^[0-9]{4}$'),
    CONSTRAINT ck_bank_accounts_holder_not_blank CHECK (btrim(account_holder_name) <> '')
);

INSERT INTO wallets (id, user_id, balance, pending_balance)
SELECT gen_random_uuid(), wallet_user.user_id, 0, 0
FROM (
    SELECT student.user_id
    FROM students student
    JOIN users app_user ON app_user.id = student.user_id
    WHERE student.deleted_at IS NULL AND app_user.deleted_at IS NULL
    UNION
    SELECT tutor.user_id
    FROM tutors tutor
    JOIN users app_user ON app_user.id = tutor.user_id
    WHERE tutor.deleted_at IS NULL AND app_user.deleted_at IS NULL
) wallet_user
ON CONFLICT (user_id) DO NOTHING;
