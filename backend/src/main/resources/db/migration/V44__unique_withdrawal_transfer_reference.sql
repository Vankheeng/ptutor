CREATE UNIQUE INDEX uq_withdrawal_requests_transfer_reference
    ON withdrawal_requests (bank_code, transfer_reference)
    WHERE transfer_reference IS NOT NULL;

CREATE INDEX idx_withdrawal_requests_status_created_at
    ON withdrawal_requests (status, created_at DESC, id DESC);
