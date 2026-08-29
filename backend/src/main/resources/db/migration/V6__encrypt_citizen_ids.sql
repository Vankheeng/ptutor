ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_citizen_id_format;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS uk_users_citizen_id;

ALTER TABLE users
    ALTER COLUMN citizen_id TYPE varchar(255);

ALTER TABLE users
    ADD COLUMN citizen_id_hash varchar(64);

ALTER TABLE users
    ADD CONSTRAINT ck_users_citizen_id_hash_format
        CHECK (citizen_id_hash IS NULL OR citizen_id_hash ~ '^[0-9a-f]{64}$');

CREATE UNIQUE INDEX uk_users_citizen_id_hash
    ON users (citizen_id_hash)
    WHERE citizen_id_hash IS NOT NULL;
