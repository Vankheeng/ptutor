-- Employees now have only two job functions: customer service and accountant.
-- The four former non-accounting functions are merged into CUSTOMER_SERVICE.
ALTER TABLE employees
    DROP CONSTRAINT ck_employees_job_function;

UPDATE employees
SET job_function = 'CUSTOMER_SERVICE'
WHERE job_function IN ('GENERAL_OPERATIONS', 'COMPLAINT_HANDLER', 'CONTENT_REVIEWER', 'USER_SUPPORT');

ALTER TABLE employees
    ADD CONSTRAINT ck_employees_job_function
        CHECK (job_function IS NULL OR job_function IN ('CUSTOMER_SERVICE', 'ACCOUNTANT'));
