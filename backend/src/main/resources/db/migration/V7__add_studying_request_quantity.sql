ALTER TABLE studying_requests
    ADD COLUMN quantity integer;

UPDATE studying_requests
SET quantity = 1
WHERE quantity IS NULL;

ALTER TABLE studying_requests
    ALTER COLUMN quantity SET NOT NULL,
    ADD CONSTRAINT ck_studying_requests_quantity CHECK (quantity > 0);
