-- V1 constrained reference_type before withdrawals became a wallet-ledger source.
-- Keep the database constraint aligned with ReferenceType.WITHDRAWAL_REQUEST.
ALTER TABLE wallet_transactions
    DROP CONSTRAINT IF EXISTS ck_wallet_transactions_reference_type;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_reference_type CHECK (
        reference_type IS NULL OR reference_type IN (
            'CONTRACT',
            'LESSON',
            'PAYMENT',
            'WALLET_TRANSACTION',
            'WITHDRAWAL_REQUEST'
        )
    );
