CREATE TABLE contract_schedules (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL REFERENCES contracts (id),
    day_of_week integer NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT chk_contract_schedules_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_contract_schedules_time CHECK (end_time > start_time)
);

CREATE INDEX idx_contract_schedules_contract_day_time
    ON contract_schedules (contract_id, day_of_week, start_time);
