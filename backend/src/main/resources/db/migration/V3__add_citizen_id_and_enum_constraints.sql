ALTER TABLE users
    ADD COLUMN citizen_id varchar(12);

ALTER TABLE users
    ADD CONSTRAINT uk_users_citizen_id UNIQUE (citizen_id);

ALTER TABLE users
    ADD CONSTRAINT ck_users_citizen_id_format
        CHECK (citizen_id ~ '^[0-9]{12}$');

ALTER TABLE users
    ADD CONSTRAINT ck_users_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'));

ALTER TABLE users
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));

ALTER TABLE employees
    ADD CONSTRAINT ck_employees_role
        CHECK (role IN (1, 2));

ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));

ALTER TABLE grades
    ADD CONSTRAINT ck_grades_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));

ALTER TABLE certificates
    ADD CONSTRAINT ck_certificates_status
        CHECK (status IS NULL OR status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'));

ALTER TABLE studying_requests
    ADD CONSTRAINT ck_studying_requests_learning_mode
        CHECK (learning_mode IN ('OFFLINE', 'ONLINE')),
    ADD CONSTRAINT ck_studying_requests_status
        CHECK (status IN ('DRAFT', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED'));

ALTER TABLE teaching_requests
    ADD CONSTRAINT ck_teaching_requests_teaching_mode
        CHECK (teaching_mode IN ('OFFLINE', 'ONLINE')),
    ADD CONSTRAINT ck_teaching_requests_status
        CHECK (status IN ('DRAFT', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED'));

ALTER TABLE student_tutor_requests
    ADD CONSTRAINT ck_student_tutor_requests_learning_mode
        CHECK (learning_mode IN ('OFFLINE', 'ONLINE')),
    ADD CONSTRAINT ck_student_tutor_requests_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'));

ALTER TABLE tutor_student_requests
    ADD CONSTRAINT ck_tutor_student_requests_teaching_mode
        CHECK (teaching_mode IN ('OFFLINE', 'ONLINE')),
    ADD CONSTRAINT ck_tutor_student_requests_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'));

ALTER TABLE contracts
    ADD CONSTRAINT ck_contracts_teaching_mode
        CHECK (teaching_mode IS NULL OR teaching_mode IN ('OFFLINE', 'ONLINE')),
    ADD CONSTRAINT ck_contracts_status
        CHECK (status IS NULL OR status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'));

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_payment_method
        CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'VNPAY', 'MOMO')),
    ADD CONSTRAINT ck_payments_payment_type
        CHECK (payment_type IS NULL OR payment_type IN ('PAYMENT', 'REFUND', 'DEPOSIT', 'WITHDRAWAL')),
    ADD CONSTRAINT ck_payments_status
        CHECK (status IS NULL OR status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED')),
    ADD CONSTRAINT ck_payments_reference_type
        CHECK (reference_type IS NULL OR reference_type IN ('CONTRACT', 'LESSON', 'PAYMENT', 'WALLET_TRANSACTION'));

ALTER TABLE wallet_transactions
    ADD CONSTRAINT ck_wallet_transactions_type
        CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
    ADD CONSTRAINT ck_wallet_transactions_reference_type
        CHECK (reference_type IS NULL OR reference_type IN ('CONTRACT', 'LESSON', 'PAYMENT', 'WALLET_TRANSACTION')),
    ADD CONSTRAINT ck_wallet_transactions_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'));

ALTER TABLE lessons
    ADD CONSTRAINT ck_lessons_teaching_mode
        CHECK (teaching_mode IS NULL OR teaching_mode IN ('OFFLINE', 'ONLINE')),
    ADD CONSTRAINT ck_lessons_status
        CHECK (status IS NULL OR status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

ALTER TABLE cancel_contract_requests
    ADD CONSTRAINT ck_cancel_contract_requests_status
        CHECK (status IS NULL OR status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE complaints
    ADD CONSTRAINT ck_complaints_status
        CHECK (status IS NULL OR status IN ('PENDING', 'IN_REVIEW', 'RESOLVED', 'REJECTED'));

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type
        CHECK (type IN ('SYSTEM', 'REQUEST', 'CONTRACT', 'PAYMENT', 'COMPLAINT'));

ALTER TABLE recommendation_click_logs
    ADD CONSTRAINT ck_recommendation_click_logs_action
        CHECK (action IN ('CLICKED', 'VIEWED', 'CONTACTED'));
