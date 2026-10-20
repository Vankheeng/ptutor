-- ===== 1. Đổi số chiều vector sang 1024 (bge-m3). An toàn vì chưa có dữ liệu. =====
ALTER TABLE students ALTER COLUMN profile_embedding TYPE vector(1024) USING NULL::vector(1024);
ALTER TABLE tutors   ALTER COLUMN profile_embedding TYPE vector(1024) USING NULL::vector(1024);
ALTER TABLE students ADD COLUMN embedding_content_hash varchar(64);
ALTER TABLE tutors   ADD COLUMN embedding_content_hash varchar(64);

-- ===== 2. Embedding theo tin đăng (ứng viên) =====
ALTER TABLE studying_requests
    ADD COLUMN embedding vector(1024),
    ADD COLUMN embedding_source_text text,
    ADD COLUMN embedding_content_hash varchar(64),
    ADD COLUMN embedding_model_version varchar(50),
    ADD COLUMN embedding_updated_at timestamp;
ALTER TABLE teaching_requests
    ADD COLUMN embedding vector(1024),
    ADD COLUMN embedding_source_text text,
    ADD COLUMN embedding_content_hash varchar(64),
    ADD COLUMN embedding_model_version varchar(50),
    ADD COLUMN embedding_updated_at timestamp;

-- ===== 3. Nguồn gốc của lịch rảnh =====
ALTER TABLE studying_request_availabilities
    ADD COLUMN source varchar(10) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_sr_availabilities_source CHECK (source IN ('USER', 'LLM'));
ALTER TABLE teaching_request_availabilities
    ADD COLUMN source varchar(10) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_tr_availabilities_source CHECK (source IN ('USER', 'LLM'));

-- ===== 4. Bộ từ vựng tag (cũng là danh sách chip trên bộ lọc) =====
CREATE TABLE recommendation_tags (
    id uuid PRIMARY KEY,
    tag_group varchar(20) NOT NULL,           -- TOPIC | STYLE | SEGMENT | GOAL
    subject_id uuid REFERENCES subjects (id), -- chỉ dùng cho TOPIC
    parent_id uuid REFERENCES recommendation_tags (id),
    code varchar(60) NOT NULL,
    name varchar(120) NOT NULL,
    description varchar(500),
    status varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_recommendation_tags_group CHECK (tag_group IN ('TOPIC', 'STYLE', 'SEGMENT', 'GOAL')),
    CONSTRAINT ck_recommendation_tags_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_recommendation_tags_code
    ON recommendation_tags (tag_group, COALESCE(subject_id, '00000000-0000-0000-0000-000000000000'::uuid), code)
    WHERE deleted_at IS NULL;

-- ===== 5. Tag gắn với hồ sơ / tin đăng =====
CREATE TABLE recommendation_entity_tags (
    id uuid PRIMARY KEY,
    entity_type varchar(30) NOT NULL,         -- STUDENT | TUTOR | STUDYING_REQUEST | TEACHING_REQUEST
    entity_id uuid NOT NULL,
    tag_id uuid NOT NULL REFERENCES recommendation_tags (id),
    role varchar(20) NOT NULL,                -- NEED | PREFERRED | SELF | STRENGTH | OFFERED | TARGET
    confidence decimal(3,2) NOT NULL DEFAULT 1.00,
    source varchar(10) NOT NULL,              -- USER | LLM
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_ret_entity_type CHECK (entity_type IN ('STUDENT', 'TUTOR', 'STUDYING_REQUEST', 'TEACHING_REQUEST')),
    CONSTRAINT ck_ret_role CHECK (role IN ('NEED', 'PREFERRED', 'SELF', 'STRENGTH', 'OFFERED', 'TARGET')),
    CONSTRAINT ck_ret_source CHECK (source IN ('USER', 'LLM')),
    CONSTRAINT ck_ret_confidence CHECK (confidence >= 0 AND confidence <= 1)
);
CREATE UNIQUE INDEX uq_ret_entity_tag_role
    ON recommendation_entity_tags (entity_type, entity_id, tag_id, role) WHERE deleted_at IS NULL;
CREATE INDEX idx_ret_entity ON recommendation_entity_tags (entity_type, entity_id) WHERE deleted_at IS NULL;

-- ===== 6. Hàng đợi job nền (outbox) =====
CREATE TABLE ai_enrichment_jobs (
    id uuid PRIMARY KEY,
    job_type varchar(20) NOT NULL,            -- ENRICH | EMBED | MATCH_ALERT | RERANK_PREFETCH | PROFILE_UPDATE
    entity_type varchar(30) NOT NULL,
    entity_id uuid NOT NULL,
    content_hash varchar(64),
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at timestamp,
    CONSTRAINT ck_ai_jobs_type CHECK (job_type IN ('ENRICH', 'EMBED', 'MATCH_ALERT', 'RERANK_PREFETCH', 'PROFILE_UPDATE')),
    CONSTRAINT ck_ai_jobs_entity CHECK (entity_type IN ('STUDENT', 'TUTOR', 'STUDYING_REQUEST', 'TEACHING_REQUEST',
                                                        'SAVED_SEARCH', 'USER')),
    CONSTRAINT ck_ai_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED'))
);
CREATE INDEX idx_ai_jobs_pending ON ai_enrichment_jobs (status, next_attempt_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_ai_jobs_pending_entity
    ON ai_enrichment_jobs (job_type, entity_type, entity_id) WHERE status IN ('PENDING', 'PROCESSING');

-- ===== 7. Bộ nhớ đệm điểm rerank (dùng chung cho tìm kiếm và tiến trình nền) =====
CREATE TABLE recommendation_rerank_cache (
    query_hash varchar(64) NOT NULL,          -- sha256 của văn bản nhu cầu / cung cấp
    candidate_type varchar(30) NOT NULL,      -- TEACHING_REQUEST | STUDYING_REQUEST
    candidate_id uuid NOT NULL,
    candidate_content_hash varchar(64) NOT NULL,
    model_version varchar(50) NOT NULL,
    rerank_score decimal(5,4) NOT NULL,       -- [0,1]
    computed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (query_hash, candidate_type, candidate_id, model_version),
    CONSTRAINT ck_rerank_cache_type CHECK (candidate_type IN ('TEACHING_REQUEST', 'STUDYING_REQUEST'))
);
CREATE INDEX idx_rerank_cache_candidate ON recommendation_rerank_cache (candidate_type, candidate_id);

-- ===== 8. Tìm kiếm đã lưu =====
CREATE TABLE recommendation_saved_searches (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    direction varchar(20) NOT NULL,           -- STUDENT_TO_TUTOR | TUTOR_TO_STUDENT
    name varchar(120),
    query_context jsonb NOT NULL,
    query_text_hash varchar(64),
    notify_enabled boolean NOT NULL DEFAULT true,
    last_notified_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp,
    CONSTRAINT ck_saved_searches_direction CHECK (direction IN ('STUDENT_TO_TUTOR', 'TUTOR_TO_STUDENT'))
);
CREATE INDEX idx_saved_searches_user ON recommendation_saved_searches (user_id) WHERE deleted_at IS NULL;

-- ===== 9. Thông báo ghép đôi đã gửi (C7) =====
CREATE TABLE recommendation_match_alerts (
    id uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL REFERENCES users (id),
    source_type varchar(30) NOT NULL,         -- STUDYING_REQUEST | TEACHING_REQUEST | SAVED_SEARCH
    source_id uuid NOT NULL,
    candidate_type varchar(30) NOT NULL,
    candidate_id uuid NOT NULL,
    final_score decimal(5,4) NOT NULL,
    candidate_rank integer NOT NULL,
    notification_id uuid REFERENCES notifications (id),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_match_alerts_source CHECK (source_type IN ('STUDYING_REQUEST', 'TEACHING_REQUEST', 'SAVED_SEARCH')),
    CONSTRAINT ck_match_alerts_candidate CHECK (candidate_type IN ('TEACHING_REQUEST', 'STUDYING_REQUEST')),
    CONSTRAINT uq_match_alerts UNIQUE (source_type, source_id, candidate_type, candidate_id)
);
CREATE INDEX idx_match_alerts_recipient ON recommendation_match_alerts (recipient_user_id, created_at DESC);

-- ===== 10. Hồ sơ sở thích dài hạn (H1) =====
CREATE TABLE recommendation_user_profiles (
    user_id uuid PRIMARY KEY REFERENCES users (id),
    role varchar(20) NOT NULL,                -- STUDENT | TUTOR
    preference_embedding vector(1024),
    tag_affinity jsonb,                       -- {"luyen_tap_nhieu": 0.8, "kien_nhan": 0.3, ...}
    signal_count integer NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_profiles_role CHECK (role IN ('STUDENT', 'TUTOR'))
);

-- ===== 11. Thống kê hiển thị (R6) =====
CREATE TABLE recommendation_exposure_stats (
    candidate_type varchar(30) NOT NULL,
    candidate_id uuid NOT NULL,
    impressions_7d integer NOT NULL DEFAULT 0,
    clicks_7d integer NOT NULL DEFAULT 0,
    impressions_total integer NOT NULL DEFAULT 0,
    clicks_total integer NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_type, candidate_id),
    CONSTRAINT ck_exposure_type CHECK (candidate_type IN ('TEACHING_REQUEST', 'STUDYING_REQUEST'))
);

-- ===== 12. Mở rộng log =====
ALTER TABLE recommendation_logs
    ADD COLUMN direction varchar(20),
    ADD COLUMN algorithm_version varchar(30),
    ADD COLUMN query_text text,
    ADD COLUMN parsed_query jsonb,
    ADD COLUMN relaxation_suggestions jsonb,
    ADD COLUMN saved_search_id uuid REFERENCES recommendation_saved_searches (id),
    ADD COLUMN latency_ms integer,
    ADD CONSTRAINT ck_recommendation_logs_direction
        CHECK (direction IS NULL OR direction IN ('STUDENT_TO_TUTOR', 'TUTOR_TO_STUDENT'));

ALTER TABLE recommendation_click_logs
    ADD COLUMN candidate_teaching_request_id uuid REFERENCES teaching_requests (id),
    ADD COLUMN candidate_studying_request_id uuid REFERENCES studying_requests (id);
ALTER TABLE recommendation_click_logs DROP CONSTRAINT IF EXISTS ck_recommendation_click_logs_action;
ALTER TABLE recommendation_click_logs ADD CONSTRAINT ck_recommendation_click_logs_action
    CHECK (action IN ('VIEWED', 'CLICKED', 'FAVORITED', 'CONTACTED', 'DISMISSED'));

-- ===== 13. Loại thông báo mới cho C7 (khai báo lại ĐỦ danh sách hiện có, rồi thêm loại mới) =====
-- Danh sách lấy theo V30 (migration mới nhất của dev định nghĩa lại ràng buộc này) cộng loại mới của tính năng đề
-- xuất. Migration nào tạo lại ràng buộc sau này cũng phải giữ đủ các giá trị dưới đây.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_event_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_event_type CHECK (
    event_type IS NULL OR event_type IN (
        'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
        'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
        'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
        'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
        'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
        'CERTIFICATE_REVIEW_REQUIRED', 'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
        'TUTOR_PROFILE_APPROVED', 'TUTOR_PROFILE_REJECTED',
        'TEACHING_REQUEST_REVIEW_REQUIRED',
        'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
        'STUDYING_REQUEST_REVIEW_REQUIRED',
        'STUDYING_REQUEST_APPROVED', 'STUDYING_REQUEST_REJECTED',
        'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
        'COMPLAINT_RECEIVED_AGAINST_YOU', 'COMPLAINT_AGAINST_YOU_RESOLVED',
        'COMPLAINT_AGAINST_YOU_REJECTED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
        'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
        'WITHDRAWAL_REVIEW_REQUIRED',
        'RECOMMENDATION_MATCH_FOUND'
    )
);
