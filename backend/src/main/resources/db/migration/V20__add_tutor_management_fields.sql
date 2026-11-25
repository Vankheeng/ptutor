ALTER TABLE tutors
    ADD COLUMN profile_status varchar(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN profile_reviewed_by uuid REFERENCES employees (id),
    ADD COLUMN profile_reviewed_at timestamp,
    ADD COLUMN profile_rejection_reason varchar(500),
    ADD COLUMN recommendation_score decimal(5,2) NOT NULL DEFAULT 50.00,
    ADD COLUMN score_updated_at timestamp,
    ADD COLUMN score_formula_version varchar(30) NOT NULL DEFAULT 'v1',
    ADD COLUMN score_breakdown jsonb;

ALTER TABLE tutors
    ADD CONSTRAINT ck_tutors_profile_status
        CHECK (profile_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    ADD CONSTRAINT ck_tutors_profile_review_metadata
        CHECK (
            (profile_status = 'PENDING'
                AND profile_reviewed_by IS NULL
                AND profile_reviewed_at IS NULL
                AND profile_rejection_reason IS NULL)
            OR (profile_status = 'VERIFIED'
                AND profile_reviewed_by IS NOT NULL
                AND profile_reviewed_at IS NOT NULL
                AND profile_rejection_reason IS NULL)
            OR (profile_status = 'REJECTED'
                AND profile_reviewed_by IS NOT NULL
                AND profile_reviewed_at IS NOT NULL
                AND profile_rejection_reason IS NOT NULL)
        ),
    ADD CONSTRAINT ck_tutors_recommendation_score
        CHECK (recommendation_score >= 0 AND recommendation_score <= 100);

CREATE INDEX idx_tutors_profile_status_recommendation_score
    ON tutors (profile_status, recommendation_score)
    WHERE deleted_at IS NULL;
