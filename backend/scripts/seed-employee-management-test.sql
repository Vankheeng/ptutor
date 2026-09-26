-- Manual development seed for testing employee-management APIs.
-- Requires Flyway migration V19 or newer.
-- All employee accounts use password: Test@123
-- The default administrator remains admin@ptutor.local / admin.
--
-- The citizen_id values are intentionally inserted as plaintext with a null
-- hash. Restart the backend after running this script so
-- CitizenIdEncryptionMigration encrypts them and calculates their hashes.

INSERT INTO users (
    id, citizen_id, citizen_id_hash, email, password,
    first_name, last_name, phone, date_of_birth, gender,
    status, suspension_count
)
VALUES
    (
        '41000000-0000-0000-0000-000000000001',
        '079100000001', NULL,
        'employee.complaint.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Complaint', 'Handler', '0910000001', '1995-01-10', 'MALE',
        'ACTIVE', 0
    ),
    (
        '41000000-0000-0000-0000-000000000002',
        '079100000002', NULL,
        'employee.accounting.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Accounting', 'Employee', '0910000002', '1994-02-11', 'FEMALE',
        'ACTIVE', 0
    ),
    (
        '41000000-0000-0000-0000-000000000003',
        '079100000003', NULL,
        'employee.reviewer.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Content', 'Reviewer', '0910000003', '1993-03-12', 'OTHER',
        'ACTIVE', 0
    ),
    (
        '41000000-0000-0000-0000-000000000004',
        '079100000004', NULL,
        'employee.support.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'User', 'Support', '0910000004', '1992-04-13', 'FEMALE',
        'ACTIVE', 0
    ),
    (
        '41000000-0000-0000-0000-000000000005',
        '079100000005', NULL,
        'employee.inactive.test@ptutor.local',
        crypt('Test@123', gen_salt('bf', 12)),
        'Inactive', 'Employee', '0910000005', '1991-05-14', 'MALE',
        'INACTIVE', 0
    )
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    password = EXCLUDED.password,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    phone = EXCLUDED.phone,
    date_of_birth = EXCLUDED.date_of_birth,
    gender = EXCLUDED.gender,
    status = EXCLUDED.status,
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

INSERT INTO employees (id, user_id, role, job_function)
VALUES
    (
        '42000000-0000-0000-0000-000000000001',
        '41000000-0000-0000-0000-000000000001',
        2, 'COMPLAINT_HANDLER'
    ),
    (
        '42000000-0000-0000-0000-000000000002',
        '41000000-0000-0000-0000-000000000002',
        2, 'ACCOUNTANT'
    ),
    (
        '42000000-0000-0000-0000-000000000003',
        '41000000-0000-0000-0000-000000000003',
        2, 'CONTENT_REVIEWER'
    ),
    (
        '42000000-0000-0000-0000-000000000004',
        '41000000-0000-0000-0000-000000000004',
        2, 'USER_SUPPORT'
    ),
    (
        '42000000-0000-0000-0000-000000000005',
        '41000000-0000-0000-0000-000000000005',
        2, 'GENERAL_OPERATIONS'
    )
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role = EXCLUDED.role,
    job_function = EXCLUDED.job_function,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

