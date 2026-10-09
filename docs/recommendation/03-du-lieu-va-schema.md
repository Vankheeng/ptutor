# 3. Dữ liệu và schema bổ sung

Phần này mô tả **dữ liệu đầu vào** của thuật toán và **migration bổ sung** (dự kiến `V25`, `V26`).
Mọi tên bảng/cột hiện có lấy theo migration V1–V24 trên nhánh `dev` (cập nhật 09/10/2026).

> Lưu ý đánh số: `dev` đã có `V22` (đối chiếu lại ràng buộc notification/wallet), `V23`, `V24` (thanh
> toán bằng ví). Trước khi tạo migration mới, luôn kiểm tra số lớn nhất trên `dev` để tránh trùng; nên
> thống nhất với nhóm dành một dải số cho tính năng đề xuất.

## 3.1. Dữ liệu đầu vào đã có

| Nhóm | Nguồn (bảng.cột) | Dùng cho |
|---|---|---|
| Ngữ cảnh tin học | `studying_requests`: `subject_id`, `grade_id`, `learning_mode`, `district_id`, `min_price`, `max_price`, `learning_goals`, `title`, `description`, `note`, `preferred_schedule`, `quantity`, `status` | Lọc cứng, a, b, văn bản nhu cầu |
| Ngữ cảnh tin dạy | `teaching_requests`: `subject_id`, `teaching_mode`, `expected_price`, `quantity`, `title`, `description`, `note`, `preferred_schedule`, `status`; `grade_teaching_requests`; `teaching_request_districts` | Lọc cứng, a, b, văn bản cung cấp |
| Lịch rảnh có cấu trúc | `studying_request_availabilities`, `teaching_request_availabilities` (`day_of_week`, `start_time`, `end_time`) | `f_sched`, gate |
| Hồ sơ học sinh | `students`: `introduction`, `learning_style`, `personality_tags`, `goals_description`, `current_level`, `weak_points` | Văn bản nhu cầu, A1 |
| Hồ sơ gia sư | `tutors`: `introduction`, `education`, `teaching_style_tags`, `teaching_methodology`, `strength_subjects`, `target_student_type`, `profile_status`, `recommendation_score` | Văn bản cung cấp, A1, Q |
| Lịch sử quyết định | `student_tutor_requests` (học sinh gửi → gia sư quyết định), `tutor_student_requests` (gia sư gửi → học sinh quyết định): `status` ∈ `PENDING/ACCEPTED/REJECTED/CANCELLED` | b, chống dồn |
| Quan hệ hiện tại | `contracts` (`status` ∈ `PENDING/ACTIVE/COMPLETED/CANCELLED`) | Loại trùng |
| Tài khoản | `users.status` (`ACTIVE/INACTIVE/BLOCKED`), `users.district_id`, `districts.province_id` | Lọc cứng, nới tiêu chí |
| Hành vi | `recommendation_logs`, `recommendation_click_logs`, `favorite_tutors` | R6, B5, đánh giá |

## 3.2. Văn bản dùng để sinh embedding

Bộ cũ chỉ embed **hồ sơ chung**. Bản này embed **theo tin đăng**, vì ứng viên là tin đăng và phần
mô tả tin đăng mang nhiều thông tin nhất.

**Văn bản nhu cầu** (cho mỗi tin học; bản không có phần tin đăng dùng cho hồ sơ học sinh):

```
Môn {subject.name}, {grade.name}.
Mục tiêu: {sr.learning_goals}. {student.goals_description}
Tin đăng: {sr.title}. {sr.description}. {sr.note}
Trình độ hiện tại: {student.current_level}. Điểm yếu: {student.weak_points}
Phong cách học mong muốn: {student.learning_style}; {student.personality_tags}
Nhu cầu đã chuẩn hoá: {tên các tag NEED, GOAL, PREFERRED, SELF}
```

**Văn bản cung cấp** (cho mỗi tin dạy; bản không có phần tin đăng dùng cho hồ sơ gia sư):

```
Môn {subject.name}, dạy {danh sách grade.name}.
Tin đăng: {tr.title}. {tr.description}. {tr.note}
Giới thiệu: {tutor.introduction}. Học vấn: {tutor.education}
Phương pháp: {tutor.teaching_methodology}. Thế mạnh: {tutor.strength_subjects}
Đối tượng phù hợp: {tutor.target_student_type}. Phong cách: {tutor.teaching_style_tags}
Thế mạnh đã chuẩn hoá: {tên các tag STRENGTH, OFFERED, TARGET}
```

Quy tắc:
- Bỏ trường rỗng; cắt tối đa 512 token.
- **Không đưa** họ tên, số điện thoại, email, địa chỉ chi tiết, ngày sinh. Chạy thêm bộ lọc regex
  số điện thoại/email trên văn bản tự do trước khi gửi.
- Lưu `content_hash` (SHA-256 của văn bản) để chỉ embed lại khi nội dung đổi.

## 3.3. Migration V25 — dữ liệu cho đề xuất

```sql
-- ===== 1. Đổi số chiều vector sang 1024 (bge-m3). An toàn vì chưa có dữ liệu. =====
ALTER TABLE students ALTER COLUMN profile_embedding TYPE vector(1024) USING NULL::vector(1024);
ALTER TABLE tutors   ALTER COLUMN profile_embedding TYPE vector(1024) USING NULL::vector(1024);
ALTER TABLE students ADD COLUMN embedding_content_hash varchar(64);
ALTER TABLE tutors   ADD COLUMN embedding_content_hash varchar(64);

-- ===== 2. Embedding theo tin đăng =====
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

-- ===== 3. Nguồn gốc của lịch rảnh (người dùng nhập hay LLM suy ra) =====
ALTER TABLE studying_request_availabilities
    ADD COLUMN source varchar(10) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_sr_availabilities_source CHECK (source IN ('USER', 'LLM'));
ALTER TABLE teaching_request_availabilities
    ADD COLUMN source varchar(10) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_tr_availabilities_source CHECK (source IN ('USER', 'LLM'));

-- ===== 4. Bộ từ vựng tag có kiểm soát =====
CREATE TABLE recommendation_tags (
    id uuid PRIMARY KEY,
    tag_group varchar(20) NOT NULL,          -- TOPIC | STYLE | SEGMENT | GOAL
    subject_id uuid REFERENCES subjects (id),-- chỉ dùng cho TOPIC (và GOAL theo môn, nếu có)
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
    entity_type varchar(30) NOT NULL,        -- STUDENT | TUTOR | STUDYING_REQUEST | TEACHING_REQUEST
    entity_id uuid NOT NULL,
    tag_id uuid NOT NULL REFERENCES recommendation_tags (id),
    role varchar(20) NOT NULL,               -- NEED | PREFERRED | SELF | STRENGTH | OFFERED | TARGET
    confidence decimal(3,2) NOT NULL DEFAULT 1.00,
    source varchar(10) NOT NULL,             -- USER | LLM
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

-- ===== 6. Hàng đợi job nền (outbox) cho A1 + embedding =====
CREATE TABLE ai_enrichment_jobs (
    id uuid PRIMARY KEY,
    job_type varchar(20) NOT NULL,           -- ENRICH | EMBED | PAIR_SCORE
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
    CONSTRAINT ck_ai_jobs_type CHECK (job_type IN ('ENRICH', 'EMBED', 'PAIR_SCORE')),
    CONSTRAINT ck_ai_jobs_entity CHECK (entity_type IN ('STUDENT', 'TUTOR', 'STUDYING_REQUEST', 'TEACHING_REQUEST')),
    CONSTRAINT ck_ai_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED'))
);
CREATE INDEX idx_ai_jobs_pending ON ai_enrichment_jobs (status, next_attempt_at) WHERE status = 'PENDING';
-- Không xếp hàng trùng: một entity chỉ có tối đa 1 job đang chờ cho mỗi loại
CREATE UNIQUE INDEX uq_ai_jobs_pending_entity
    ON ai_enrichment_jobs (job_type, entity_type, entity_id) WHERE status IN ('PENDING', 'PROCESSING');

-- ===== 7. Điểm cặp đã tính trước (cache rerank + nền cho C7) =====
CREATE TABLE recommendation_pair_scores (
    id uuid PRIMARY KEY,
    studying_request_id uuid NOT NULL REFERENCES studying_requests (id),
    teaching_request_id uuid NOT NULL REFERENCES teaching_requests (id),
    rerank_score decimal(5,4),               -- xác suất từ cross-encoder, [0,1]
    relevance_a decimal(5,4),                -- a theo chiều S→T
    willingness_b decimal(5,4),
    final_score decimal(5,4),
    sr_content_hash varchar(64) NOT NULL,
    tr_content_hash varchar(64) NOT NULL,
    model_version varchar(50) NOT NULL,
    notified_at timestamp,                   -- đã gửi thông báo C7 cho cặp này chưa
    computed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pair_scores UNIQUE (studying_request_id, teaching_request_id)
);
CREATE INDEX idx_pair_scores_tr ON recommendation_pair_scores (teaching_request_id);

-- ===== 8. Thống kê hiển thị (R6) =====
CREATE TABLE recommendation_exposure_stats (
    candidate_type varchar(30) NOT NULL,     -- TEACHING_REQUEST | STUDYING_REQUEST
    candidate_id uuid NOT NULL,
    impressions_7d integer NOT NULL DEFAULT 0,
    clicks_7d integer NOT NULL DEFAULT 0,
    impressions_total integer NOT NULL DEFAULT 0,
    clicks_total integer NOT NULL DEFAULT 0,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_type, candidate_id),
    CONSTRAINT ck_exposure_type CHECK (candidate_type IN ('TEACHING_REQUEST', 'STUDYING_REQUEST'))
);

-- ===== 9. Mở rộng log =====
ALTER TABLE recommendation_logs
    ADD COLUMN direction varchar(20),        -- STUDENT_TO_TUTOR | TUTOR_TO_STUDENT
    ADD COLUMN algorithm_version varchar(30),
    ADD COLUMN query_text text,              -- câu tự nhiên (A2), đã lọc PII
    ADD COLUMN parsed_query jsonb,
    ADD COLUMN relaxation_suggestions jsonb,
    ADD COLUMN latency_ms integer,
    ADD CONSTRAINT ck_recommendation_logs_direction
        CHECK (direction IS NULL OR direction IN ('STUDENT_TO_TUTOR', 'TUTOR_TO_STUDENT'));

ALTER TABLE recommendation_click_logs
    ADD COLUMN candidate_teaching_request_id uuid REFERENCES teaching_requests (id),
    ADD COLUMN candidate_studying_request_id uuid REFERENCES studying_requests (id);
ALTER TABLE recommendation_click_logs DROP CONSTRAINT IF EXISTS ck_recommendation_click_logs_action;
ALTER TABLE recommendation_click_logs ADD CONSTRAINT ck_recommendation_click_logs_action
    CHECK (action IN ('VIEWED', 'CLICKED', 'FAVORITED', 'CONTACTED', 'DISMISSED'));

-- ===== 10. Loại thông báo mới cho C7 (khai báo lại ĐỦ danh sách hiện có từ V22, rồi thêm loại mới) =====
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_event_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_event_type CHECK (
    event_type IS NULL OR event_type IN (
        'PAYMENT_SUCCEEDED', 'PAYMENT_INSTALLMENT_DUE',
        'TUTOR_REQUEST_RECEIVED', 'TUTOR_REQUEST_ACCEPTED', 'TUTOR_REQUEST_REJECTED',
        'STUDENT_REQUEST_RECEIVED', 'STUDENT_REQUEST_ACCEPTED', 'STUDENT_REQUEST_REJECTED',
        'CONTRACT_SIGNATURE_REQUIRED', 'CONTRACT_SIGNED', 'CONTRACT_REJECTED',
        'CONTRACT_CANCELLED', 'CONTRACT_RENEWAL_PROPOSED',
        'CERTIFICATE_REVIEW_REQUIRED', 'CERTIFICATE_APPROVED', 'CERTIFICATE_REJECTED',
        'TEACHING_REQUEST_REVIEW_REQUIRED',
        'TEACHING_REQUEST_APPROVED', 'TEACHING_REQUEST_REJECTED',
        'COMPLAINT_EVIDENCE_REQUESTED', 'COMPLAINT_RESOLVED', 'COMPLAINT_REJECTED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_REACTIVATED',
        'LESSON_MARKED_TAUGHT', 'LESSON_CANCELLED',
        'WITHDRAWAL_REVIEW_REQUIRED',
        'RECOMMENDATION_MATCH_FOUND'
    )
);
```

Ghi chú:
- Đổi kiểu vector cần sửa luôn `columnDefinition = "vector(1024)"` trong entity `Student`, `Tutor`
  (dự án dùng `ddl-auto: validate`).
- Không tạo index ANN cho vector: quy mô nhỏ, quét tuần tự cho kết quả chính xác.
- Bảng `recommendation_entity_tags` dùng khoá đa hình (`entity_type`, `entity_id`) nên không có FK.
  Tính toàn vẹn do tầng ứng dụng đảm bảo; khi xoá mềm entity thì xoá mềm tag tương ứng.

## 3.4. Migration V26 — điều kiện tiên quyết P2, P5

```sql
-- P2: mỗi hợp đồng tối đa một đánh giá
ALTER TABLE reviews ADD COLUMN contract_id uuid REFERENCES contracts (id);
CREATE UNIQUE INDEX uq_reviews_contract ON reviews (contract_id)
    WHERE contract_id IS NOT NULL AND deleted_at IS NULL;
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);

-- P5: thời điểm phản hồi đơn
ALTER TABLE student_tutor_requests ADD COLUMN responded_at timestamp;
ALTER TABLE tutor_student_requests ADD COLUMN responded_at timestamp;
```

## 3.5. Cột tổng hợp trên `tutors`

`AggregateRefreshJob` (P1) chạy một câu `UPDATE` duy nhất, ngay trước khi tính lại
`recommendation_score`:

```sql
WITH rv AS (
    SELECT tutor_id, AVG(rating) AS avg_rating, COUNT(*) AS cnt
    FROM reviews WHERE deleted_at IS NULL AND rating IS NOT NULL
    GROUP BY tutor_id
), ct AS (
    SELECT tutor_id,
           COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
           COUNT(DISTINCT student_id) FILTER (WHERE status IN ('ACTIVE', 'COMPLETED')) AS students
    FROM contracts WHERE deleted_at IS NULL
    GROUP BY tutor_id
), rq AS (   -- quyết định của gia sư với đơn học sinh gửi tới tin dạy của mình
    SELECT tr.tutor_id,
           COUNT(*) FILTER (WHERE s.status = 'ACCEPTED') AS accepted,
           COUNT(*) FILTER (WHERE s.status IN ('ACCEPTED', 'REJECTED')) AS decided,
           AVG(EXTRACT(EPOCH FROM (COALESCE(s.responded_at, s.updated_at) - s.created_at)) / 3600.0)
               FILTER (WHERE s.status IN ('ACCEPTED', 'REJECTED')) AS resp_hours
    FROM student_tutor_requests s
    JOIN teaching_requests tr ON tr.id = s.teaching_request_id
    WHERE s.deleted_at IS NULL
    GROUP BY tr.tutor_id
)
UPDATE tutors t SET
    average_rating            = COALESCE(ROUND(rv.avg_rating, 2), 0),
    total_reviews             = COALESCE(rv.cnt, 0),
    completed_contracts_count = COALESCE(ct.completed, 0),
    total_students_taught     = COALESCE(ct.students, 0),
    acceptance_rate           = CASE WHEN rq.decided >= 3
                                     THEN ROUND(100.0 * rq.accepted / rq.decided, 2) END,
    avg_response_time_hours   = LEAST(ROUND(rq.resp_hours::numeric, 2), 9999.99),
    updated_at                = CURRENT_TIMESTAMP
FROM tutors x
LEFT JOIN rv ON rv.tutor_id = x.id
LEFT JOIN ct ON ct.tutor_id = x.id
LEFT JOIN rq ON rq.tutor_id = x.id
WHERE t.id = x.id AND t.deleted_at IS NULL;
```

- `acceptance_rate` để `NULL` khi gia sư mới có dưới 3 quyết định, để công thức
  `recommendation_score` dùng giá trị trung lập (7,5 điểm).
- `avg_response_time_hours` dùng `updated_at` thay cho `responded_at` với dữ liệu cũ (gần đúng).
