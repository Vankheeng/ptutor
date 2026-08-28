CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE provinces (
    id uuid PRIMARY KEY,
    name varchar,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE districts (
    id uuid PRIMARY KEY,
    province_id uuid REFERENCES provinces (id),
    name varchar,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE subjects (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL UNIQUE,
    description varchar(500),
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE grades (
    id uuid PRIMARY KEY,
    name varchar(50) NOT NULL UNIQUE,
    level integer,
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    district_id uuid REFERENCES districts (id),
    email varchar(255) NOT NULL UNIQUE,
    password varchar(255) NOT NULL,
    first_name varchar(100),
    last_name varchar(100),
    phone varchar(20),
    date_of_birth date,
    gender varchar(20),
    detail_address varchar(255),
    avatar_url varchar(500),
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE students (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users (id),
    introduction text,
    learning_style varchar(50),
    personality_tags varchar(255),
    goals_description text,
    current_level varchar(50),
    weak_points text,
    profile_embedding vector(1536),
    embedding_source_text text,
    embedding_updated_at timestamp,
    embedding_model_version varchar(50),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE tutors (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users (id),
    introduction text,
    experience_years integer,
    education text,
    teaching_style_tags varchar(255),
    teaching_methodology text,
    strength_subjects text,
    target_student_type varchar(255),
    profile_embedding vector(1536),
    embedding_source_text text,
    embedding_updated_at timestamp,
    embedding_model_version varchar(50),
    average_rating decimal(3,2) NOT NULL DEFAULT 0,
    total_reviews integer NOT NULL DEFAULT 0,
    completed_contracts_count integer NOT NULL DEFAULT 0,
    total_students_taught integer NOT NULL DEFAULT 0,
    acceptance_rate decimal(5,2),
    avg_response_time_hours decimal(6,2),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE employees (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES users (id),
    role integer NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE invalid_tokens (
    id uuid PRIMARY KEY,
    token varchar(1000) NOT NULL UNIQUE,
    user_id uuid REFERENCES users (id),
    expires_at timestamp NOT NULL,
    revoked_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE forgot_passwords (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    otp varchar(255) NOT NULL UNIQUE,
    expires_at timestamp NOT NULL,
    used_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE certificates (
    id uuid PRIMARY KEY,
    tutor_id uuid NOT NULL REFERENCES tutors (id),
    name varchar(255) NOT NULL,
    issuing_organization varchar(255),
    description varchar(255),
    issue_date date,
    expiry_date date,
    certificate_url varchar(500),
    status varchar(30),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE studying_requests (
    id uuid PRIMARY KEY,
    district_id uuid REFERENCES districts (id),
    student_id uuid NOT NULL REFERENCES students (id),
    subject_id uuid NOT NULL REFERENCES subjects (id),
    grade_id uuid NOT NULL REFERENCES grades (id),
    title varchar(255),
    description varchar(255),
    note varchar(255),
    detail_address varchar(255),
    min_price decimal(15,2),
    max_price decimal(15,2),
    learning_goals text,
    learning_mode varchar(30) NOT NULL,
    preferred_schedule varchar(500),
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE studying_request_availabilities (
    id uuid PRIMARY KEY,
    studying_request_id uuid NOT NULL REFERENCES studying_requests (id),
    day_of_week integer NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE teaching_requests (
    id uuid PRIMARY KEY,
    tutor_id uuid NOT NULL REFERENCES tutors (id),
    subject_id uuid REFERENCES subjects (id),
    title varchar(255),
    note varchar(255),
    quantity integer,
    detail_address varchar(255),
    expected_price decimal(15,2),
    teaching_mode varchar(30) NOT NULL,
    preferred_schedule varchar(500),
    description text,
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE teaching_request_districts (
    id uuid PRIMARY KEY,
    teaching_request_id uuid REFERENCES teaching_requests (id),
    district_id uuid REFERENCES districts (id),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE teaching_request_availabilities (
    id uuid PRIMARY KEY,
    teaching_request_id uuid NOT NULL REFERENCES teaching_requests (id),
    day_of_week integer NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE grade_teaching_requests (
    id uuid PRIMARY KEY,
    grade_id uuid NOT NULL REFERENCES grades (id),
    teaching_request_id uuid NOT NULL REFERENCES teaching_requests (id),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE student_tutor_requests (
    id uuid PRIMARY KEY,
    student_id uuid NOT NULL REFERENCES students (id),
    grade_id uuid NOT NULL REFERENCES grades (id),
    teaching_request_id uuid NOT NULL REFERENCES teaching_requests (id),
    proposed_price decimal(15,2),
    learning_mode varchar(30) NOT NULL,
    preferred_schedule varchar(500),
    message text,
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE tutor_student_requests (
    id uuid PRIMARY KEY,
    tutor_id uuid NOT NULL REFERENCES tutors (id),
    grade_id uuid NOT NULL REFERENCES grades (id),
    studying_request_id uuid NOT NULL REFERENCES studying_requests (id),
    proposed_price decimal(15,2),
    teaching_mode varchar(30) NOT NULL,
    preferred_schedule varchar(500),
    message text,
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE contracts (
    id uuid PRIMARY KEY,
    student_id uuid REFERENCES students (id),
    tutor_id uuid REFERENCES tutors (id),
    subject_id uuid REFERENCES subjects (id),
    grade_id uuid REFERENCES grades (id),
    teaching_mode varchar,
    price decimal,
    payment_period varchar,
    total_lession integer,
    start_date date,
    end_date date,
    status varchar,
    signed_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES users (id),
    amount decimal,
    payment_method varchar,
    payment_type varchar,
    status varchar,
    transaction_code varchar,
    reference_type varchar(50),
    reference_id uuid,
    note varchar,
    paid_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE wallets (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users (id),
    balance decimal(15,2) NOT NULL DEFAULT 0,
    pending_balance decimal(15,2) NOT NULL DEFAULT 0,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE wallet_transactions (
    id uuid PRIMARY KEY,
    wallet_id uuid NOT NULL REFERENCES wallets (id),
    transaction_type varchar(50) NOT NULL,
    amount decimal(15,2) NOT NULL,
    balance_after decimal(15,2) NOT NULL,
    reference_type varchar(50),
    reference_id uuid,
    description varchar(500),
    status varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE lessons (
    id uuid PRIMARY KEY,
    contract_id uuid REFERENCES contracts (id),
    title varchar,
    date date,
    start_time time,
    end_time time,
    teaching_mode varchar,
    meeting_link varchar,
    location varchar,
    materials_url varchar,
    status varchar,
    note text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE cancel_contract_requests (
    id uuid PRIMARY KEY,
    contract_id uuid REFERENCES contracts (id),
    user_id uuid REFERENCES users (id),
    reason text,
    status varchar,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE reviews (
    id uuid PRIMARY KEY,
    student_id uuid REFERENCES students (id),
    tutor_id uuid REFERENCES tutors (id),
    rating integer,
    comment text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE complaints (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES users (id),
    contract_id uuid REFERENCES contracts (id),
    employee_id uuid REFERENCES employees (id),
    title varchar,
    content text,
    status varchar,
    resolution text,
    resolved_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE evidences (
    id uuid PRIMARY KEY,
    complaint_id uuid REFERENCES complaints (id),
    file_url varchar,
    file_type varchar,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    title varchar(255) NOT NULL,
    content text,
    reference_id varchar(255),
    type varchar(50) NOT NULL,
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp
);

CREATE TABLE recommendation_logs (
    id uuid PRIMARY KEY,
    searcher_student_id uuid REFERENCES students (id),
    searcher_tutor_id uuid REFERENCES tutors (id),
    source_studying_request_id uuid REFERENCES studying_requests (id),
    source_teaching_request_id uuid REFERENCES teaching_requests (id),
    query_context jsonb,
    returned_candidate_ids jsonb,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_recommendation_logs_searcher_xor CHECK (
        (searcher_student_id IS NOT NULL AND searcher_tutor_id IS NULL)
        OR (searcher_student_id IS NULL AND searcher_tutor_id IS NOT NULL)
    )
);

CREATE TABLE recommendation_click_logs (
    id uuid PRIMARY KEY,
    recommendation_log_id uuid NOT NULL REFERENCES recommendation_logs (id),
    clicked_student_id uuid REFERENCES students (id),
    clicked_tutor_id uuid REFERENCES tutors (id),
    rank integer NOT NULL,
    action varchar(20) NOT NULL DEFAULT 'clicked',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_recommendation_click_logs_candidate_xor CHECK (
        (clicked_student_id IS NOT NULL AND clicked_tutor_id IS NULL)
        OR (clicked_student_id IS NULL AND clicked_tutor_id IS NOT NULL)
    )
);

CREATE TABLE favorite_tutors (
    id uuid PRIMARY KEY,
    student_id uuid NOT NULL REFERENCES students (id),
    tutor_id uuid NOT NULL REFERENCES tutors (id),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT uk_favorite_tutors_student_tutor UNIQUE (student_id, tutor_id)
);

CREATE INDEX idx_users_district_id ON users (district_id);
CREATE INDEX idx_certificates_tutor_id ON certificates (tutor_id);
CREATE INDEX idx_studying_requests_student_id ON studying_requests (student_id);
CREATE INDEX idx_studying_requests_subject_id ON studying_requests (subject_id);
CREATE INDEX idx_studying_requests_grade_id ON studying_requests (grade_id);
CREATE INDEX idx_studying_requests_district_id ON studying_requests (district_id);
CREATE INDEX idx_studying_request_availabilities_request_id
    ON studying_request_availabilities (studying_request_id);
CREATE INDEX idx_teaching_requests_tutor_id ON teaching_requests (tutor_id);
CREATE INDEX idx_teaching_requests_subject_id ON teaching_requests (subject_id);
CREATE INDEX idx_teaching_request_districts_request_id
    ON teaching_request_districts (teaching_request_id);
CREATE INDEX idx_teaching_request_districts_district_id
    ON teaching_request_districts (district_id);
CREATE INDEX idx_teaching_request_availabilities_request_id
    ON teaching_request_availabilities (teaching_request_id);
CREATE INDEX idx_grade_teaching_requests_grade_id
    ON grade_teaching_requests (grade_id);
CREATE INDEX idx_grade_teaching_requests_request_id
    ON grade_teaching_requests (teaching_request_id);
CREATE INDEX idx_student_tutor_requests_student_id
    ON student_tutor_requests (student_id);
CREATE INDEX idx_student_tutor_requests_request_id
    ON student_tutor_requests (teaching_request_id);
CREATE INDEX idx_tutor_student_requests_tutor_id
    ON tutor_student_requests (tutor_id);
CREATE INDEX idx_tutor_student_requests_request_id
    ON tutor_student_requests (studying_request_id);
CREATE INDEX idx_contracts_student_id ON contracts (student_id);
CREATE INDEX idx_contracts_tutor_id ON contracts (tutor_id);
CREATE INDEX idx_lessons_contract_id ON lessons (contract_id);
CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions (wallet_id);
CREATE INDEX idx_cancel_contract_requests_contract_id
    ON cancel_contract_requests (contract_id);
CREATE INDEX idx_reviews_student_id ON reviews (student_id);
CREATE INDEX idx_reviews_tutor_id ON reviews (tutor_id);
CREATE INDEX idx_complaints_user_id ON complaints (user_id);
CREATE INDEX idx_complaints_contract_id ON complaints (contract_id);
CREATE INDEX idx_complaints_employee_id ON complaints (employee_id);
CREATE INDEX idx_evidences_complaint_id ON evidences (complaint_id);
CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_recommendation_logs_searcher_student_id
    ON recommendation_logs (searcher_student_id);
CREATE INDEX idx_recommendation_logs_searcher_tutor_id
    ON recommendation_logs (searcher_tutor_id);
CREATE INDEX idx_recommendation_click_logs_recommendation_log_id
    ON recommendation_click_logs (recommendation_log_id);
CREATE INDEX idx_favorite_tutors_student_id ON favorite_tutors (student_id);
CREATE INDEX idx_favorite_tutors_tutor_id ON favorite_tutors (tutor_id);
