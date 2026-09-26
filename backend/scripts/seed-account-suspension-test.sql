-- Manual development seed for testing account suspension APIs.
-- All seeded accounts use password: Test@123

INSERT INTO users (
    id, citizen_id, citizen_id_hash, email, password,
    first_name, last_name, phone, status, suspension_count
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        '079000000001',
        NULL,
        'student.suspension.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Test', 'Student', '0900000001', 'ACTIVE', 0
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        '079000000002',
        NULL,
        'tutor.suspension.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Test', 'Tutor', '0900000002', 'ACTIVE', 0
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        '079000000003',
        NULL,
        'employee.suspension.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Test', 'Employee', '0900000003', 'ACTIVE', 0
    )
ON CONFLICT (id) DO UPDATE SET
    password = EXCLUDED.password,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    phone = EXCLUDED.phone,
    status = 'ACTIVE',
    suspension_type = NULL,
    suspension_reason = NULL,
    suspended_at = NULL,
    suspended_until = NULL,
    suspended_by_user_id = NULL,
    reactivated_at = NULL,
    reactivated_by_user_id = NULL,
    reactivation_reason = NULL,
    suspension_count = 0,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO students (id, user_id)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001'
)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO tutors (
    id, user_id, average_rating, total_reviews,
    completed_contracts_count, total_students_taught
)
VALUES (
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    0, 0, 0, 0
)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO employees (id, user_id, role)
VALUES (
    '20000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000003',
    2
)
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role = EXCLUDED.role,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wallets (id, user_id, balance, pending_balance)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        0, 0
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000002',
        0, 0
    )
ON CONFLICT (user_id) DO NOTHING;

SELECT id, email, status
FROM users
WHERE id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003'
)
ORDER BY email;
