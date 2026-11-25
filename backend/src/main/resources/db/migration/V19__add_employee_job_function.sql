ALTER TABLE employees
    ADD COLUMN job_function varchar(50);

UPDATE employees
SET job_function = 'GENERAL_OPERATIONS'
WHERE role = 2;

ALTER TABLE employees
    ADD CONSTRAINT ck_employees_job_function
        CHECK (job_function IS NULL OR job_function IN (
            'GENERAL_OPERATIONS',
            'COMPLAINT_HANDLER',
            'ACCOUNTANT',
            'CONTENT_REVIEWER',
            'USER_SUPPORT'
        )),
    ADD CONSTRAINT ck_employees_role_job_function
        CHECK (
            (role = 1 AND job_function IS NULL)
            OR (role = 2 AND job_function IS NOT NULL)
        );

ALTER TABLE employees
    ALTER COLUMN user_id SET NOT NULL;

CREATE UNIQUE INDEX uk_employees_user_id_active
    ON employees (user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_employees_job_function
    ON employees (job_function)
    WHERE deleted_at IS NULL;
