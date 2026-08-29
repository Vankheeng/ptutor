CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH admin_user AS (
    INSERT INTO users (
        id,
        citizen_id,
        email,
        password,
        status
    )
    SELECT
        gen_random_uuid(),
        '000000000000',
        'admin@ptutor.local',
        crypt('admin', gen_salt('bf', 12)),
        'ACTIVE'
    WHERE NOT EXISTS (
        SELECT 1
        FROM users
        WHERE lower(email) = lower('admin@ptutor.local')
    )
    RETURNING id
)
INSERT INTO employees (id, user_id, role)
SELECT gen_random_uuid(), id, 1
FROM admin_user;
