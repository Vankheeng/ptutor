# 8. API

Theo quy ước của dự án: tiền tố `/api/v1`, cần access token, response bọc trong
`{ success, code, message, data }`. Ví dụ dưới đây chỉ ghi phần `data`.

## 8.1. Danh sách endpoint

| Method | Endpoint | Role | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/recommendations/parse-query` | STUDENT, TUTOR | Tách câu tự nhiên thành giá trị bộ lọc (A2) để giao diện điền vào form |
| `POST` | `/api/v1/students/me/tutor-recommendations` | STUDENT | Học sinh tìm gia sư theo bộ lọc |
| `POST` | `/api/v1/tutors/me/student-recommendations` | TUTOR | Gia sư tìm học viên theo bộ lọc (**không cần tin dạy**) |
| `POST` | `/api/v1/recommendations/{logId}/events` | STUDENT, TUTOR | Ghi xem / tym / liên hệ / không quan tâm |
| `POST` | `/api/v1/recommendations/{logId}/refine` | STUDENT, TUTOR | Xếp lại phần chưa xem (B5) |
| `GET` / `POST` | `/api/v1/users/me/saved-searches` | STUDENT, TUTOR | Xem / lưu tìm kiếm (tối đa 5) |
| `PUT` / `DELETE` | `/api/v1/users/me/saved-searches/{id}` | STUDENT, TUTOR | Sửa (đổi tên, bật/tắt thông báo) / xoá |
| `DELETE` | `/api/v1/users/me/recommendation-profile` | STUDENT, TUTOR | Xoá lịch sử gợi ý (H1) |
| `GET` | `/api/v1/admin/recommendations/logs` | ADMIN | Tra cứu log để phân tích |

Chỉ chủ của `logId` mới gọi được `events`, `refine`.

**Quan hệ với API có sẵn:** `GET /api/v1/studying-requests` và `GET /api/v1/teaching-requests` là
danh sách duyệt theo bộ lọc, sắp theo thời gian. Giao diện có hai chế độ: **"Gợi ý cho bạn"** (endpoint
đề xuất) và **"Tất cả"** (API có sẵn). Không sửa hành vi của API có sẵn.

## 8.2. Tách câu tự nhiên

`POST /api/v1/recommendations/parse-query`

```json
{ "text": "Gia sư Toán lớp 9 kiên nhẫn, ôn thi vào 10, tối thứ 3 và 5, dưới 200k, học ở Cầu Giấy",
  "direction": "STUDENT_TO_TUTOR" }
```

Response `data`: các giá trị bộ lọc đã ánh xạ ID (cùng cấu trúc `filters` ở 8.3), cộng
`semanticText` và `notes` (mục không nhận ra). Lỗi LLM → `filters` rỗng, `semanticText` = cả câu.
Giao diện điền vào form; người dùng xem lại rồi mới bấm tìm.

## 8.3. Học sinh tìm gia sư

`POST /api/v1/students/me/tutor-recommendations?page=0&size=20[&logId=…]`

```json
{
  "filters": {
    "subjectId": "…",
    "gradeId": "…",
    "learningMode": "ANY",
    "area": { "scope": "DISTRICTS", "districtIds": ["…"], "provinceId": null },
    "price": { "min": null, "max": 200000 },
    "availabilities": [{ "dayOfWeek": 2, "startTime": "19:00", "endTime": "21:00" }],
    "styleTagCodes": ["kien_nhan"],
    "goalTagCodes": ["thi_vao_10"],
    "topicTagCodes": ["hinh_hoc_phang"]
  },
  "semanticText": "ôn thi vào lớp 10",
  "queryText": "Gia sư Toán lớp 9 kiên nhẫn, …",
  "useStudyingRequestId": null,
  "savedSearchId": null
}
```

- `learningMode` ∈ `ONLINE` / `OFFLINE` / `ANY`; `area.scope` ∈ `DISTRICTS` / `PROVINCE` / `ANY`.
- `queryText` chỉ để ghi log (câu gốc); `semanticText` là phần ngữ nghĩa từ 8.2.
- `useStudyingRequestId`: tuỳ chọn, dùng tin học của chính học sinh để điền giá trị còn trống.
- Thiếu môn/lớp sau khi gộp → `needsInput: ["subjectId"]`, `items: []`.

**Response `data`:**

```json
{
  "logId": "…",
  "algorithmVersion": "v2.1",
  "queryContext": { "...": "tiêu chí cuối cùng sau khi gộp, kèm nguồn của từng tiêu chí (FILTER / STUDYING_REQUEST / PROFILE)" },
  "needsInput": [],
  "lowConfidence": false,
  "items": [
    {
      "rank": 1,
      "teachingRequestId": "…",
      "tutor": { "userId": "…", "displayName": "Nguyễn Văn Y", "avatarUrl": "…",
                 "averageRating": 4.7, "totalReviews": 12 },
      "title": "Dạy kèm Toán 8–9, ôn thi vào 10",
      "expectedPrice": 180000,
      "teachingMode": "OFFLINE",
      "districts": ["Cầu Giấy", "Đống Đa"],
      "score": 0.681,
      "isExploration": false,
      "reasons": ["Mạnh về Hình học phẳng, đúng phần bạn cần cải thiện",
                  "Trùng lịch rảnh: Thứ Ba, Thứ Năm 19:00–21:00",
                  "Còn 2 chỗ, chưa có nhiều người đăng ký"],
      "caution": null
    }
  ],
  "relaxations": [
    { "code": "AREA_PROVINCE", "message": "Mở rộng ra toàn Hà Nội: thêm 7 gia sư phù hợp",
      "gain": 7, "apply": { "area": { "scope": "PROVINCE", "provinceId": "…" } } }
  ],
  "page": 0, "size": 20, "totalElements": 34
}
```

**Phân trang:** lần gọi **không có `logId`** là một lần tìm mới: tính, ghi log, trả `logId`. Trang sau gọi
kèm **`?logId=…&page=n`** (không cần body): đọc lại danh sách đã xếp hạng lưu trong log (tối đa 50 ứng
viên, `size` ≤ 50), không tính lại nên thứ tự ổn định dù dữ liệu đổi; tin đã bị xoá thì rơi khỏi trang.
`logId` của người khác → `404 RECOMMENDATION_LOG_NOT_FOUND`. Lỗi AI service không bao giờ trả lỗi cho
người dùng: không lấy được vector truy vấn thì dùng `students.profile_embedding`, vẫn không có thì xếp
hạng không có `f_sem`.

`dayOfWeek` theo **ISO-8601: 1 = Thứ Hai … 7 = Chủ nhật** (đã ghi trong OpenAPI cho mọi API có lịch rảnh).
`savedSearchId` chưa được nhận cho tới khi có tính năng lưu tìm kiếm.

## 8.4. Gia sư tìm học viên

`POST /api/v1/tutors/me/student-recommendations?page=0&size=20[&logId=…]`

```json
{
  "filters": {
    "subjectId": "…",
    "gradeIds": ["…", "…"],
    "learningMode": "ANY",
    "area": { "scope": "PROVINCE", "provinceId": "…" },
    "expectedPrice": 180000,
    "availabilities": [ ... ],
    "targetSegmentCodes": ["trung_binh", "kha"]
  },
  "useTeachingRequestId": null,
  "savedSearchId": null
}
```

Mỗi item là một tin học, chỉ kèm thông tin **giới hạn** của học sinh:

```json
{
  "rank": 1,
  "studyingRequestId": "…",
  "student": { "displayName": "Học sinh lớp 9", "district": "Cầu Giấy" },
  "title": "Tìm gia sư Toán 9 ôn thi vào 10",
  "learningGoals": "…",
  "priceRange": { "min": 150000, "max": 200000 },
  "learningMode": "OFFLINE",
  "score": 0.71,
  "reasons": ["Cần cải thiện Hình học phẳng — thế mạnh của bạn", "…"]
}
```

Gia sư chưa được duyệt hồ sơ → `403 TUTOR_PROFILE_NOT_VERIFIED`. Phân trang bằng `logId` như 8.3. `student.displayName` là "Học sinh {tên lớp}"; response không chứa mã
học sinh hay mã tài khoản. `savedSearchId` chưa được nhận cho tới khi có tính năng lưu tìm kiếm.

Gia sư **không thấy** họ tên đầy đủ, ảnh, SĐT, địa chỉ chi tiết của học sinh trong danh sách đề xuất;
thông tin chi tiết chỉ mở theo luồng nghiệp vụ hiện có.

## 8.5. Sự kiện và xếp lại trong phiên

`POST /api/v1/recommendations/{logId}/events`

```json
{ "action": "VIEWED", "rank": 3, "candidateId": "…" }
```

`action` ∈ `VIEWED`, `CLICKED`, `FAVORITED`, `CONTACTED`, `DISMISSED`. Ghi `recommendation_click_logs`;
`FAVORITED` (S→T) đồng thời ghi `favorite_tutors`; mỗi sự kiện tạo job `PROFILE_UPDATE` cho H1.

`POST /api/v1/recommendations/{logId}/refine` với `{ "seenUpToRank": 8 }` → `items` mới cho các hạng
sau `seenUpToRank` (07, mục 7.2).

## 8.6. Tìm kiếm đã lưu

`POST /api/v1/users/me/saved-searches`

```json
{ "direction": "STUDENT_TO_TUTOR", "name": "Toán 9 – Cầu Giấy", "filters": { ... },
  "semanticText": "ôn thi vào lớp 10", "notifyEnabled": true }
```

Lỗi `409 SAVED_SEARCH_LIMIT_REACHED` khi đã có 5. Mở một tìm kiếm đã lưu = gọi 8.3/8.4 với
`savedSearchId`.

## 8.7. Nội dung lưu trong `recommendation_logs`

| Cột | Nội dung |
|---|---|
| `searcher_student_id` / `searcher_tutor_id` | Người tìm |
| `source_studying_request_id` / `source_teaching_request_id` | Tin đăng của người tìm, **chỉ khi** họ chọn dùng làm tiêu chí (thường để trống) |
| `saved_search_id` | Nếu mở từ tìm kiếm đã lưu |
| `direction`, `algorithm_version` | `STUDENT_TO_TUTOR`, `v2.1` … |
| `query_text`, `parsed_query`, `query_context` | Câu gốc (đã lọc PII), kết quả A2, tiêu chí cuối cùng |
| `returned_candidate_ids` | ≤ 50 phần tử: `{candidateId, candidateUserId, rank, score, a, b, q, g, wHist, features:{sem, need, style, segment, price, sched, area, hist}, rerank, isExploration}` |
| `relaxation_suggestions`, `latency_ms` | |

Lưu đủ đặc trưng từng ứng viên để: phân trang và `/refine` không phải tính lại; giải thích "vì sao
người này đứng đầu"; sau này học trọng số từ dữ liệu mà không đổi schema (10, mục 10.5).
