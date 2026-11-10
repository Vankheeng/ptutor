-- L1: the mode an administrator chose for a learned weight model at runtime (design docs/recommendation/12,
-- sections 12.8 and 12.9). A model without a row uses the mode of the configuration.
CREATE TABLE recommendation_learning_modes (
    model_key   VARCHAR(20)  PRIMARY KEY,
    mode        VARCHAR(10)  NOT NULL,
    updated_by  UUID         REFERENCES users (id),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_learning_modes_model CHECK (model_key IN ('A_S2T', 'A_T2S', 'B_S2T', 'B_T2S')),
    CONSTRAINT ck_learning_modes_mode CHECK (mode IN ('OFF', 'SHADOW', 'ACTIVE'))
);
