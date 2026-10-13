-- P2: a student reviews a tutor once per completed contract.
ALTER TABLE reviews ADD COLUMN contract_id uuid REFERENCES contracts (id);

CREATE UNIQUE INDEX uq_reviews_contract ON reviews (contract_id)
    WHERE contract_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE reviews
    ADD CONSTRAINT ck_reviews_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);

-- P5: when the counterparty accepted or rejected an application, used for response-time statistics.
ALTER TABLE student_tutor_requests ADD COLUMN responded_at timestamp;
ALTER TABLE tutor_student_requests ADD COLUMN responded_at timestamp;
