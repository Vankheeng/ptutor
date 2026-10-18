# 3. Dữ liệu và schema bổ sung

> 💡 **File này nói về gì:** Thuật toán đọc những dữ liệu nào, văn bản nào được biến thành vector, và các bảng / cột mới phải thêm vào database. "Schema" là cấu trúc database; "migration" là file SQL có đánh số (V27, V28…) mà công cụ **Flyway** chạy lần lượt để thay đổi cấu trúc đó. Đọc khi cần biết dữ liệu nằm ở bảng nào.

Phần này mô tả **dữ liệu đầu vào** của thuật toán và **migration bổ sung**: `V27` (điều kiện tiên quyết P2, P5, mục 3.3) chạy trước `V28` (dữ liệu cho đề xuất, mục 3.4); `V30` (dự kiến — học trọng số L1, mục 3.6) thiết kế ngày 13/10/2026, chưa tạo file.
Mọi tên bảng/cột hiện có lấy theo migration V1–V24 trên nhánh `dev` (cập nhật 09/10/2026).

> Lưu ý đánh số: `dev` đã có `V22` (đối chiếu lại ràng buộc notification/wallet), `V23`, `V24` (thanh
> toán bằng ví). Trước khi tạo migration mới, luôn kiểm tra số lớn nhất trên `dev`; Flyway **không** bật `out-of-order`, nên
> `V27` phải được tạo và áp dụng trước `V28`.
>
> **Đổi số lần hai (13/10/2026).** Sau khi merge `dev` (#49), `dev` đã dùng `V25` (bỏ `studying_requests.quantity`)
> và `V26` (tin học được ghi môn ngoài danh mục, thêm trạng thái `PENDING_REVIEW`, `REJECTED`). Ba migration của
> nhánh này vì vậy là **`V27`** (điều kiện tiên quyết), **`V28`** (dữ liệu đề xuất), **`V29`** (mốc xoá lịch sử).
> Nội dung ba file không đổi; chúng không đụng tới cột hay ràng buộc mà `V25`, `V26` của `dev` sửa, nên thứ tự
> áp dụng không ảnh hưởng kết quả.
>
> **Cần đổi số lần ba (ghi nhận 15/10/2026).** `dev` nay đã có `V27__add_studying_request_review.sql` (PR #51), trùng
> số với `V27` của nhánh này; nhánh `feat/complaint-structured-resolution` dùng `V28__add_structured_complaint_resolution.sql`.
> Khi merge, ba migration của nhánh này phải lấy số **sau số lớn nhất của `dev` lúc đó** (ví dụ `V29`–`V31` nếu
> khiếu nại vào `dev` trước), và `V30` dự kiến của L1 đổi theo. **Ràng buộc `ck_notifications_event_type`:** cả
> `V27` của `dev`, `V28` của nhánh khiếu nại và migration dữ liệu đề xuất của nhánh này đều xoá rồi tạo lại ràng buộc
> này với danh sách đầy đủ. Migration chạy **sau cùng** phải chứa **hợp** của mọi danh sách (thêm
> `STUDYING_REQUEST_REVIEW_REQUIRED`, `STUDYING_REQUEST_APPROVED`, `STUDYING_REQUEST_REJECTED`,
> `COMPLAINT_RECEIVED_AGAINST_YOU`, `COMPLAINT_AGAINST_YOU_RESOLVED`, `COMPLAINT_AGAINST_YOU_REJECTED` cùng
> `RECOMMENDATION_MATCH_FOUND`), nếu không thông báo của tính năng khác sẽ bị database từ chối.

## 3.1. Dữ liệu đầu vào

> 💡 Bảng liệt kê từng nhóm dữ liệu, lấy từ bảng nào, dùng cho bước nào. Ví dụ: tin dạy (`teaching_requests`) là ứng viên khi học sinh tìm; lịch sử đơn được nhận / từ chối dùng để ước lượng b (khả năng phía kia đồng ý); hành vi xem / tym dùng để cá nhân hóa.

| Nhóm | Nguồn | Dùng cho |
|---|---|---|
| Tiêu chí tìm kiếm | Bộ lọc + câu tự nhiên (05), lưu trong `recommendation_logs.query_context` | Lọc cứng, a, văn bản nhu cầu |
| Ứng viên phía gia sư | `teaching_requests` (`subject_id`, `teaching_mode`, `expected_price`, `quantity`, `title`, `description`, `note`, `status`), `grade_teaching_requests`, `teaching_request_districts`, `teaching_request_availabilities` | Lọc cứng, a, b, văn bản cung cấp |
| Ứng viên phía học sinh | `studying_requests` (`subject_id`, `grade_id`, `learning_mode`, `district_id`, `min_price`, `max_price`, `learning_goals`, `title`, `description`, `status`), `studying_request_availabilities` (mỗi tin học nhận đúng một gia sư — cột `quantity` đã bị `dev` xoá ở V25) | Lọc cứng (T→S), a′, b′ |
| Hồ sơ học sinh | `students` (`introduction`, `learning_style`, `personality_tags`, `goals_description`, `current_level`, `weak_points`), `users.district_id` | Giá trị mặc định cho bộ lọc, văn bản nhu cầu, A1 |
| Hồ sơ gia sư | `tutors` (`introduction`, `education`, `teaching_style_tags`, `teaching_methodology`, `strength_subjects`, `target_student_type`, `profile_status`, `recommendation_score`) | Văn bản cung cấp, A1, Q |
| Lịch sử quyết định | `student_tutor_requests`, `tutor_student_requests` (`PENDING/ACCEPTED/REJECTED/CANCELLED`) | b, b′, chống dồn, H1 |
| Khiếu nại | `complaints` (`user_id` người gửi, `contract_id`, `status`, `category`, `fault_party`, `resolved_at`) — cột `category`, `fault_party` do tính năng xử lý khiếu nại thêm (nhánh `feat/complaint-structured-resolution`) | Phạt khiếu nại có lỗi trong Q (06, mục 6.8) |
| Quan hệ hiện tại | `contracts` | Loại trùng, H1 |
| Hành vi | `recommendation_logs`, `recommendation_click_logs`, `favorite_tutors` | R6, B5, H1, đánh giá |
| Khu vực | `districts.province_id` | Lọc phạm vi tỉnh, `f_area` |

## 3.2. Văn bản dùng để sinh embedding

> 💡 **Embedding** là việc biến một đoạn văn thành vector, để hai đoạn cùng nghĩa có vector gần nhau. Mục này nói đoạn văn nào được ghép lại để tính vector: mỗi **tin đăng** một vector (gồm môn, lớp, tiêu đề, mô tả…). Tên, số điện thoại, email bị bỏ ra; `content_hash` là "dấu vân tay" của đoạn văn, giúp chỉ tính lại khi nội dung thật sự đổi.

**Ứng viên** được embed **theo tin đăng** (tin đăng mang nhiều thông tin nhất):

```
Tin dạy:  Môn {subject}, dạy {danh sách lớp}. Tin đăng: {tr.title}. {tr.description}. {tr.note}
          Giới thiệu: {tutor.introduction}. Học vấn: {tutor.education}
          Phương pháp: {tutor.teaching_methodology}. Thế mạnh: {tutor.strength_subjects}
          Đối tượng: {tutor.target_student_type}. Phong cách: {tutor.teaching_style_tags}
          Đã chuẩn hoá: {tên tag STRENGTH, OFFERED, TARGET}

Tin học:  Môn {subject}, {grade}. Mục tiêu: {sr.learning_goals}. Tin đăng: {sr.title}. {sr.description}. {sr.note}
          Học sinh: {student.goals_description}. Điểm yếu: {student.weak_points}. Trình độ: {student.current_level}
          Phong cách mong muốn: {student.learning_style}; {student.personality_tags}
          Đã chuẩn hoá: {tên tag NEED, GOAL, PREFERRED, SELF}
```

**Hồ sơ** (`students.profile_embedding`, `tutors.profile_embedding`) là bản không có phần tin đăng —
dùng khi người tìm không có tiêu chí văn bản nào.

**Truy vấn** (văn bản nhu cầu / cung cấp của người tìm) được sinh lúc tìm kiếm — xem 05, mục 5.5.

Quy tắc chung: bỏ trường rỗng; cắt ≤ 512 token; không đưa họ tên, SĐT, email, địa chỉ chi tiết,
ngày sinh; lọc regex SĐT/email trong văn bản tự do; lưu `content_hash` để chỉ embed lại khi đổi.

## 3.3. Migration V27 — điều kiện tiên quyết P2, P5

> 💡 Hai thay đổi nhỏ: gắn mỗi đánh giá với đúng một hợp đồng (để mỗi hợp đồng chỉ được đánh giá một lần) và thêm cột `responded_at` ghi lúc phía kia trả lời đơn (để đo tốc độ phản hồi chính xác). Lưu ý số migration đã phải đổi mấy lần vì nhánh khác dùng trùng số (xem khung ghi chú đầu file).

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

## 3.4. Migration V28 — dữ liệu cho đề xuất

> 💡 Toàn bộ bảng mới cho tính năng, đánh số từ 1 đến 13 trong file SQL: cột vector cho tin đăng (1024 số mỗi vector, theo mô hình `bge-m3`), bộ từ vựng **tag** (mã chuẩn như `kien_nhan`, `hinh_hoc_phang`), hàng đợi job nền, bộ nhớ đệm điểm rerank, tìm kiếm đã lưu, thông báo đã gửi, hồ sơ sở thích, thống kê lượt hiển thị, và các cột thêm vào bảng nhật ký. Không cần đọc hết SQL; mỗi khối có dòng chú thích `-- =====` nói nó là gì.

```sql
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
-- Bổ sung 13/10/2026 (migration V29): mốc "Xoá lịch sử gợi ý"; tín hiệu trước mốc bị bỏ qua.
ALTER TABLE recommendation_user_profiles ADD COLUMN history_reset_at timestamp;

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

-- ===== 13. Loại thông báo mới cho C7 (khai báo lại ĐỦ danh sách hiện có từ V22, rồi thêm loại mới) =====
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
- Đổi kiểu vector cần sửa `columnDefinition = "vector(1024)"` trong entity `Student`, `Tutor`
  (`ddl-auto: validate`).
- Không tạo index ANN cho vector ở quy mô đồ án.
- `recommendation_click_logs` vẫn giữ ràng buộc cũ `ck_recommendation_click_logs_candidate_xor` (đúng một
  trong `clicked_student_id` / `clicked_tutor_id`). Khi ghi sự kiện trên một tin đăng: điền **chủ tin** vào
  cột người tương ứng và điền tin đăng vào `candidate_teaching_request_id` / `candidate_studying_request_id`.
- `recommendation_entity_tags`, `ai_enrichment_jobs` dùng khoá đa hình (`entity_type`, `entity_id`)
  nên không có FK; tầng ứng dụng đảm bảo toàn vẹn.
- Tag người dùng **tự chọn trên bộ lọc** khi tìm kiếm không ghi vào `recommendation_entity_tags`
  (chỉ nằm trong `query_context`); tag ghi vào hồ sơ khi người dùng sửa hồ sơ (`source = 'USER'`) hoặc
  do A1 suy ra (`source = 'LLM'`).

## 3.5. Cột tổng hợp trên `tutors`

> 💡 Một câu SQL tính lại 6 con số tóm tắt của mỗi gia sư từ dữ liệu gốc: điểm đánh giá trung bình, số đánh giá, số hợp đồng hoàn thành, số học sinh đã dạy, tỉ lệ nhận đơn, thời gian phản hồi trung bình. Các con số này là đầu vào của điểm uy tín gia sư (Q). Chạy mỗi đêm cho mọi gia sư, và chạy ngay cho một gia sư khi có sự kiện liên quan (đánh giá mới, đơn được trả lời…). Dưới 3 quyết định thì chưa tính tỉ lệ nhận (để trống, công thức dùng giá trị trung lập).

`AggregateRefreshJob` (P1) chạy một câu `UPDATE`, ngay trước khi tính lại `recommendation_score`:
- **hằng đêm** cho mọi gia sư (trong job 02:30 UTC);
- **theo sự kiện** cho riêng một gia sư (thêm `AND t.id = :tutorId`), trong cùng transaction với lần tính
  lại điểm do listener kích hoạt (01, mục 1.5, P3–P4) — để điểm phản ánh ngay đánh giá / hợp đồng / đơn mới.

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
), rq AS (
    SELECT tr.tutor_id,
           COUNT(*) FILTER (WHERE s.status = 'ACCEPTED') AS accepted,
           COUNT(*) FILTER (WHERE s.status IN ('ACCEPTED', 'REJECTED')) AS decided,
           AVG(GREATEST(EXTRACT(EPOCH FROM (COALESCE(s.responded_at, s.updated_at) - s.created_at)), 0)
                   / 3600.0)
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
    avg_response_time_hours   = CASE WHEN rq.resp_hours IS NOT NULL
                                     THEN LEAST(ROUND(rq.resp_hours::numeric, 2), 9999.99) END,
    updated_at                = CURRENT_TIMESTAMP
FROM tutors x
LEFT JOIN rv ON rv.tutor_id = x.id
LEFT JOIN ct ON ct.tutor_id = x.id
LEFT JOIN rq ON rq.tutor_id = x.id
WHERE t.id = x.id AND t.deleted_at IS NULL;
```

`acceptance_rate` để `NULL` khi dưới 3 quyết định (công thức `recommendation_score` dùng giá trị
trung lập); `avg_response_time_hours` dùng `updated_at` cho dữ liệu cũ (gần đúng), và để `NULL` khi gia sư chưa
quyết định đơn nào (`LEAST` của PostgreSQL bỏ qua `NULL` nên phải bọc `CASE`, nếu không sẽ thành 9999,99).
Thời gian phản hồi âm (hai mốc thời gian lệch nguồn giờ) được tính là 0 nhờ `GREATEST(…, 0)`.

## 3.6. Migration V30 — học trọng số (L1)

> 💡 Phần **đang thiết kế**: hai bảng cho việc tự học trọng số — một bảng lưu các phiên bản trọng số đã học, một bảng lưu "ảnh chụp" các con số tại lúc đơn được tạo (để sau này biết lúc đó hệ thống đã ước lượng gì).

Hai bảng mới: `recommendation_learned_weights` (mỗi lần cập nhật một dòng, có version, thống kê A, c và
cờ `is_active`) và `recommendation_decision_snapshots` (ảnh chụp thành phần b khi đơn / đề nghị được tạo).
SQL đầy đủ và các trường bổ sung vào `recommendation_logs` ở 12, mục 12.8. Số migration kiểm tra lại với
`dev` trước khi tạo file.
