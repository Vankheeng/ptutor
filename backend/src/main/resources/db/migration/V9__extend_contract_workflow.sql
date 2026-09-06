ALTER TABLE contracts
    ADD COLUMN created_by_user_id uuid,
    ADD COLUMN signed_by_user_id uuid,
    ADD COLUMN tutor_student_request_id uuid,
    ADD COLUMN student_tutor_request_id uuid,
    ADD COLUMN renewed_from_contract_id uuid,
    ADD COLUMN preferred_schedule varchar(500);

ALTER TABLE contracts
    ADD CONSTRAINT fk_contracts_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    ADD CONSTRAINT fk_contracts_signed_by_user
        FOREIGN KEY (signed_by_user_id) REFERENCES users (id),
    ADD CONSTRAINT fk_contracts_tutor_student_request
        FOREIGN KEY (tutor_student_request_id) REFERENCES tutor_student_requests (id),
    ADD CONSTRAINT fk_contracts_student_tutor_request
        FOREIGN KEY (student_tutor_request_id) REFERENCES student_tutor_requests (id),
    ADD CONSTRAINT fk_contracts_renewed_from_contract
        FOREIGN KEY (renewed_from_contract_id) REFERENCES contracts (id);

ALTER TABLE contracts
    ALTER COLUMN student_id SET NOT NULL,
    ALTER COLUMN tutor_id SET NOT NULL,
    ALTER COLUMN subject_id SET NOT NULL,
    ALTER COLUMN grade_id SET NOT NULL,
    ALTER COLUMN teaching_mode SET NOT NULL,
    ALTER COLUMN price SET NOT NULL,
    ALTER COLUMN payment_period SET NOT NULL,
    ALTER COLUMN total_lession SET NOT NULL,
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN created_by_user_id SET NOT NULL,
    ALTER COLUMN preferred_schedule SET NOT NULL;

ALTER TABLE contracts
    ADD CONSTRAINT ck_contracts_origin
        CHECK (
            ((tutor_student_request_id IS NOT NULL)::integer
                + (student_tutor_request_id IS NOT NULL)::integer
                + (renewed_from_contract_id IS NOT NULL)::integer) = 1
        ),
    ADD CONSTRAINT ck_contracts_price_non_negative
        CHECK (price >= 0),
    ADD CONSTRAINT ck_contracts_total_lession_positive
        CHECK (total_lession > 0),
    ADD CONSTRAINT ck_contracts_payment_period_not_blank
        CHECK (btrim(payment_period) <> ''),
    ADD CONSTRAINT ck_contracts_preferred_schedule_not_blank
        CHECK (btrim(preferred_schedule) <> ''),
    ADD CONSTRAINT ck_contracts_date_range
        CHECK (end_date > start_date),
    ADD CONSTRAINT ck_contracts_signature_pair
        CHECK ((signed_by_user_id IS NULL) = (signed_at IS NULL)),
    ADD CONSTRAINT ck_contracts_signer_is_not_creator
        CHECK (signed_by_user_id IS NULL OR signed_by_user_id <> created_by_user_id);

CREATE UNIQUE INDEX uq_contracts_open_tutor_student_request
    ON contracts (tutor_student_request_id)
    WHERE tutor_student_request_id IS NOT NULL
      AND deleted_at IS NULL
      AND status <> 'CANCELLED';

CREATE UNIQUE INDEX uq_contracts_open_student_tutor_request
    ON contracts (student_tutor_request_id)
    WHERE student_tutor_request_id IS NOT NULL
      AND deleted_at IS NULL
      AND status <> 'CANCELLED';

CREATE UNIQUE INDEX uq_contracts_open_renewal
    ON contracts (renewed_from_contract_id)
    WHERE renewed_from_contract_id IS NOT NULL
      AND deleted_at IS NULL
      AND status <> 'CANCELLED';

CREATE INDEX idx_contracts_student_status_created_at
    ON contracts (student_id, status, created_at DESC);

CREATE INDEX idx_contracts_tutor_status_created_at
    ON contracts (tutor_id, status, created_at DESC);

CREATE INDEX idx_contracts_created_by_user_id
    ON contracts (created_by_user_id);
