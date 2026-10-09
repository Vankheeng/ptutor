# 8. API

Theo quy ước hiện có của dự án: tiền tố `/api/v1`, cần access token, response bọc trong
`{ success, code, message, data }`. Ví dụ dưới đây chỉ ghi phần `data`.

## 8.1. Danh sách endpoint

| Method | Endpoint | Role | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/students/me/tutor-recommendations` | STUDENT | Tìm gia sư (bộ lọc, câu tự nhiên, hoặc theo tin học) |
| `POST` | `/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-recommendations` | TUTOR | Tìm học viên cho một tin dạy `OPEN` của mình |
| `POST` | `/api/v1/recommendations/{logId}/events` | STUDENT, TUTOR | Ghi sự kiện xem / tym / liên hệ / không quan tâm |
| `POST` | `/api/v1/recommendations/{logId}/refine` | STUDENT, TUTOR | Xếp lại phần chưa xem (B5) |
| `GET` | `/api/v1/admin/recommendations/logs` | ADMIN | Tra cứu log để phân tích (lọc theo thời gian, chiều, phiên bản) |

Chỉ chủ của `logId` mới gọi được `events` và `refine` (kiểm tra `searcher_*_id`).

**Quan hệ với API tìm kiếm có sẵn:** `GET /api/v1/studying-requests` (và `GET /api/v1/teaching-requests`)
là danh sách duyệt theo bộ lọc, sắp xếp theo thời gian. Giao diện nên có hai chế độ: **"Gợi ý cho bạn"**
dùng endpoint đề xuất, và **"Tất cả"** dùng API có sẵn. Không sửa hành vi của API có sẵn.

## 8.2. Học sinh tìm gia sư

`POST /api/v1/students/me/tutor-recommendations?page=0&size=20`

```json
{
  "queryText": "Gia sư Toán lớp 9 kiên nhẫn, ôn thi vào 10, tối thứ 3 và 5, dưới 200k",
  "studyingRequestId": null,
  "filters": {
    "subjectId": null,
    "gradeId": null,
    "learningMode": "OFFLINE",
    "districtId": null,
    "maxPrice": null,
    "availabilities": null,
    "styleTagCodes": null
  }
}
```

Mọi trường đều tuỳ chọn, nhưng sau khi gộp (05, mục 5.5) phải xác định được môn và lớp.

**Response `data`:**

```json
{
  "logId": "1d7c…",
  "algorithmVersion": "v2.0",
  "interpretedQuery": {
    "subject": {"id": "…", "name": "Toán"},
    "grade": {"id": "…", "name": "Lớp 9"},
    "learningMode": "OFFLINE",
    "district": {"id": "…", "name": "Cầu Giấy"},
    "maxPrice": 200000,
    "availabilities": [{"dayOfWeek": 2, "startTime": "19:00", "endTime": "21:00"}],
    "styleTags": [{"code": "kien_nhan", "name": "Kiên nhẫn, nhẹ nhàng"}],
    "goalTags": [{"code": "thi_vao_10", "name": "Thi vào lớp 10"}],
    "sources": {"subject": "QUERY_TEXT", "learningMode": "FILTER", "district": "PROFILE"}
  },
  "needsInput": [],
  "lowConfidence": false,
  "items": [
    {
      "rank": 1,
      "teachingRequestId": "…",
      "tutor": {"userId": "…", "displayName": "Nguyễn Văn Y", "avatarUrl": "…",
                "averageRating": 4.7, "totalReviews": 12},
      "title": "Dạy kèm Toán 8–9, ôn thi vào 10",
      "expectedPrice": 180000,
      "teachingMode": "OFFLINE",
      "score": 0.681,
      "isExploration": false,
      "reasons": [
        "Mạnh về Hình học phẳng, đúng phần bạn cần cải thiện",
        "Trùng lịch rảnh: Thứ Ba, Thứ Năm 19:00–21:00",
        "Còn 2 chỗ, chưa có nhiều người đăng ký"
      ],
      "caution": null
    }
  ],
  "relaxations": [
    {
      "code": "AREA_PROVINCE",
      "message": "Mở rộng ra toàn Hà Nội: thêm 7 gia sư phù hợp",
      "gain": 7,
      "apply": {"districtScope": "PROVINCE"}
    }
  ],
  "page": 0, "size": 20, "totalElements": 34
}
```

- `score` hiển thị cho mục đích minh bạch/debug; giao diện có thể ẩn.
- `sources` cho biết mỗi tiêu chí đến từ đâu (`FILTER`, `QUERY_TEXT`, `STUDYING_REQUEST`,
  `PROFILE`) để giao diện vẽ chip.
- Phân trang: trang sau dùng lại danh sách đã xếp hạng lưu trong log (không tính lại), để thứ tự
  ổn định giữa các trang.

**Lỗi:**

| HTTP | code | Khi nào |
|---|---|---|
| 200 | `SUCCESS` với `needsInput: ["subject"]`, `items: []` | Không xác định được môn/lớp |
| 404 | `STUDYING_REQUEST_NOT_FOUND` | `studyingRequestId` không thuộc học sinh |
| 422 | `QUERY_TEXT_TOO_LONG` | Câu dài quá 500 ký tự |

Lỗi AI service **không bao giờ** trả lỗi cho người dùng: hệ thống dùng phương án dự phòng
(05, mục 5.2; 06, mục 6.9).

## 8.3. Gia sư tìm học viên

`POST /api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-recommendations?page=0&size=20`

```json
{ "filters": { "districtScope": "REQUEST", "ignoreSchedule": false } }
```

Response cùng cấu trúc, mỗi item thay `tutor` bằng thông tin **giới hạn** của tin học:

```json
{
  "rank": 1,
  "studyingRequestId": "…",
  "student": {"displayName": "Học sinh lớp 9", "district": "Cầu Giấy"},
  "title": "Tìm gia sư Toán 9 ôn thi vào 10",
  "learningGoals": "…",
  "priceRange": {"min": 150000, "max": 200000},
  "learningMode": "OFFLINE",
  "score": 0.71,
  "reasons": ["Cần cải thiện Hình học phẳng — thế mạnh của bạn", "…"]
}
```

**Quyền riêng tư:** gia sư **không thấy** họ tên đầy đủ, ảnh, số điện thoại, địa chỉ chi tiết
của học sinh trong danh sách đề xuất. Thông tin chi tiết chỉ mở theo luồng nghiệp vụ hiện có
(sau khi hai bên kết nối qua đơn/đề nghị).

Lỗi: `404 TEACHING_REQUEST_NOT_FOUND` (không thuộc gia sư), `409 TEACHING_REQUEST_NOT_OPEN`.

## 8.4. Sự kiện

`POST /api/v1/recommendations/{logId}/events`

```json
{ "action": "VIEWED", "rank": 3, "candidateId": "…teachingRequestId hoặc studyingRequestId…" }
```

`action` ∈ `VIEWED`, `CLICKED`, `FAVORITED`, `CONTACTED`, `DISMISSED`. Ghi vào
`recommendation_click_logs` (`clicked_tutor_id`/`clicked_student_id` và
`candidate_*_request_id`). `FAVORITED` đồng thời ghi `favorite_tutors` (chiều S→T).

## 8.5. Xếp lại trong phiên

`POST /api/v1/recommendations/{logId}/refine`

```json
{ "seenUpToRank": 8 }
```

Response: `items` mới cho các hạng **sau** `seenUpToRank`, tính theo 07, mục 7.2.

## 8.6. Nội dung lưu trong `recommendation_logs`

| Cột | Nội dung |
|---|---|
| `searcher_student_id` / `searcher_tutor_id` | Người tìm |
| `source_studying_request_id` / `source_teaching_request_id` | Ngữ cảnh |
| `direction`, `algorithm_version` | `STUDENT_TO_TUTOR`, `v2.0` … |
| `query_text`, `parsed_query` | Câu tự nhiên (đã lọc PII), kết quả tách câu |
| `query_context` | `QueryContext` cuối cùng (sau khi gộp) |
| `returned_candidate_ids` | Mảng tối đa 50 phần tử: `{candidateId, candidateUserId, rank, score, a, b, q, g, features:{sem, need, style, segment, price, sched}, rerank, isExploration}` |
| `relaxation_suggestions` | Các gợi ý nới đã trả |
| `latency_ms` | Tổng thời gian xử lý |

Lưu đủ đặc trưng của từng ứng viên giúp: (1) phân trang và `/refine` không phải tính lại;
(2) sau này huấn luyện learning-to-rank mà không cần đổi schema.
