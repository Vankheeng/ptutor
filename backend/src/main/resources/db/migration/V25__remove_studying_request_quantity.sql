ALTER TABLE studying_requests
    DROP CONSTRAINT IF EXISTS ck_studying_requests_quantity;

ALTER TABLE studying_requests
    DROP COLUMN IF EXISTS quantity;
