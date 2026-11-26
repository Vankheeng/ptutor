-- L1: learned ranking weights and snapshots of the willingness features when an application is created
-- (design docs/recommendation/12, section 12.8).

-- One row per update of a model; only one version of each model is active.
CREATE TABLE recommendation_learned_weights (
    id              BIGSERIAL PRIMARY KEY,
    model_key       VARCHAR(20)  NOT NULL,
    version         INT          NOT NULL,
    feature_names   JSONB        NOT NULL,
    weights         JSONB        NOT NULL,
    posterior_mean  JSONB        NOT NULL,
    stats_a         JSONB        NOT NULL,
    stats_c         JSONB        NOT NULL,
    prior_weights   JSONB        NOT NULL,
    sample_count    INT          NOT NULL,
    trained_until   TIMESTAMP    NOT NULL,
    evaluation      JSONB,
    is_active       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learned_weights_version UNIQUE (model_key, version),
    CONSTRAINT ck_learned_weights_model CHECK (model_key IN ('A_S2T', 'A_T2S', 'B_S2T', 'B_T2S'))
);

CREATE UNIQUE INDEX uq_learned_weights_active
    ON recommendation_learned_weights (model_key) WHERE is_active;

-- The willingness features of a pair at the moment an application or proposal was created. DECISION: the
-- model waiting for the other side's answer; INITIATIVE: the positive sample of the sender's side.
CREATE TABLE recommendation_decision_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    model_key       VARCHAR(20)  NOT NULL,
    sample_kind     VARCHAR(20)  NOT NULL,
    request_type    VARCHAR(30)  NOT NULL,
    request_id      UUID         NOT NULL,
    features        JSONB        NOT NULL,
    weights_version JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_decision_snapshot UNIQUE (model_key, sample_kind, request_id),
    CONSTRAINT ck_decision_snapshot_model CHECK (model_key IN ('B_S2T', 'B_T2S')),
    CONSTRAINT ck_decision_snapshot_kind CHECK (sample_kind IN ('DECISION', 'INITIATIVE')),
    CONSTRAINT ck_decision_snapshot_request_type
        CHECK (request_type IN ('STUDENT_TUTOR_REQUEST', 'TUTOR_STUDENT_REQUEST'))
);

CREATE INDEX idx_decision_snapshots_request ON recommendation_decision_snapshots (request_type, request_id);
