# 8. API

> 💡 **File này nói về gì:** Danh sách các **API** (địa chỉ mà giao diện gọi tới backend để lấy hay gửi dữ liệu) của tính năng: gửi gì lên, nhận gì về, lỗi nào có thể xảy ra. Đọc khi làm giao diện hoặc viết test API. "Endpoint" là một địa chỉ API cụ thể; `POST`, `GET`, `PUT`, `DELETE` là loại thao tác (gửi, lấy, sửa, xóa).

Theo quy ước của dự án: tiền tố `/api/v1`, cần access token, response bọc trong
`{ success, code, message, data }`. Ví dụ dưới đây chỉ ghi phần `data`.

## 8.1. Danh sách endpoint

> 💡 Bảng tổng hợp mọi endpoint và vai trò được gọi (STUDENT, TUTOR, ADMIN). Nhóm chính: tìm kiếm của hai phía, ghi hành vi (`events`), xếp lại trong phiên (`refine`), tìm kiếm đã lưu, xóa lịch sử gợi ý, quản lý tag. Các API trọng số (`weights`) thuộc phần học trọng số **đang thiết kế**. Chế độ "Tất cả" vẫn dùng API danh sách có sẵn, không bị sửa.

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
| `GET` | `/api/v1/admin/recommendations/weights` | ADMIN | Trọng số L1: prior, bản đang dùng, lịch sử version, đánh giá (tuỳ chọn — 12, mục 12.9) |
| `POST` | `/api/v1/admin/recommendations/weights/{model}/activate/{version}` | ADMIN | Kích hoạt một version (quay lui) |
| `PUT` | `/api/v1/admin/recommendations/weights/{model}/mode` | ADMIN | Đổi chế độ `OFF` / `SHADOW` / `ACTIVE` của một mô hình |
| `GET` | `/api/v1/recommendation-tags` | đã đăng nhập | Tag `ACTIVE` để chọn làm bộ lọc (`group`, `subjectId`) |
| `GET` / `POST` | `/api/v1/admin/recommendation-tags` | ADMIN, EMPLOYEE (`CONTENT_REVIEWER`) | Xem bộ từ vựng / tạo một hoặc nhiều tag |
| `PUT` | `/api/v1/admin/recommendation-tags/{id}` | như trên | Đổi tên, mô tả, tag cha (không đổi được mã) |
| `PATCH` | `/api/v1/admin/recommendation-tags/{id}/status` | như trên | Bật / tắt tag |
| `POST` | `/api/v1/admin/recommendation-tags/suggestions` | như trên | Nhờ LLM gợi ý TOPIC cho một môn (không lưu) |

Chỉ chủ của `logId` mới gọi được `events`, `refine`. Quy tắc của nhóm endpoint tag: xem `04` mục 4.11.

**Quan hệ với API có sẵn:** `GET /api/v1/studying-requests` và `GET /api/v1/teaching-requests` là
danh sách duyệt theo bộ lọc, sắp theo thời gian. Giao diện có hai chế độ: **"Gợi ý cho bạn"** (endpoint
đề xuất) và **"Tất cả"** (API có sẵn). Không sửa hành vi của API có sẵn.

## 8.2. Tách câu tự nhiên

> 💡 Gửi câu người dùng gõ, nhận về giá trị đã điền cho các ô bộ lọc (đã đổi tên môn, quận… thành mã trong hệ thống). Giao diện điền vào form để người dùng xem lại. Lỗi thì trả bộ lọc rỗng, không báo lỗi.

`POST /api/v1/recommendations/parse-query`

```json
{ "text": "Gia sư Toán lớp 9 kiên nhẫn, ôn thi vào 10, tối thứ 3 và 5, dưới 200k, học ở Cầu Giấy",
  "direction": "STUDENT_TO_TUTOR" }
```

Response `data`: các giá trị bộ lọc đã ánh xạ ID (cùng cấu trúc `filters` ở 8.3), cộng
`semanticText` và `notes` (mục không nhận ra). Lỗi LLM → `filters` rỗng, `semanticText` = cả câu.
Giao diện điền vào form; người dùng xem lại rồi mới bấm tìm.

Ghi chú triển khai (13/10/2026): `semanticText` trong response **luôn là nguyên câu người dùng** (đã lọc SĐT,
email), không do LLM viết lại — xem 05 mục 5.4. Response có thêm `understood` (false khi không phân tích được); với
`direction = TUTOR_TO_STUDENT`, `filters` theo cấu trúc 8.4. Chi tiết ánh xạ: 05 mục 5.4.

## 8.3. Học sinh tìm gia sư

> 💡 Endpoint chính của học sinh. Gửi `filters` (bộ lọc); nhận về `logId` (mã của lần tìm), danh sách kết quả — mỗi kết quả có điểm, lý do, lưu ý — và gợi ý nới tiêu chí. **Phân trang** bằng `logId`: trang sau đọc lại đúng danh sách đã xếp và lưu ở lần tìm đầu, nên thứ tự không bị xáo dù dữ liệu thay đổi. AI service lỗi cũng không làm lần tìm báo lỗi.

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
  "algorithmVersion": "v2.2-rcx",
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
`savedSearchId`: xem 8.6.

## 8.4. Gia sư tìm học viên

> 💡 Endpoint của gia sư, cấu trúc tương tự. Để bảo vệ học sinh (có thể chưa đủ 18 tuổi), mỗi kết quả chỉ hiện thông tin **giới hạn**: "Học sinh lớp 9", quận, tiêu đề tin, khoảng giá — không có họ tên, ảnh, số điện thoại, mã tài khoản. Gia sư chưa được duyệt hồ sơ bị từ chối (lỗi 403 = không có quyền).

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
  "isExploration": false,
  "reasons": ["Học sinh cần đúng phần bạn mạnh: Hình học phẳng", "…"],
  "caution": null
}
```

Gia sư chưa được duyệt hồ sơ → `403 TUTOR_PROFILE_NOT_VERIFIED`. Phân trang bằng `logId` như 8.3. `student.displayName` là "Học sinh {tên lớp}"; response không chứa mã
học sinh hay mã tài khoản. `savedSearchId`: xem 8.6.

Gia sư **không thấy** họ tên đầy đủ, ảnh, SĐT, địa chỉ chi tiết của học sinh trong danh sách đề xuất;
thông tin chi tiết chỉ mở theo luồng nghiệp vụ hiện có.

## 8.5. Sự kiện và xếp lại trong phiên

> 💡 `events` ghi hành vi trên một kết quả (xem, bấm, tym, liên hệ, không quan tâm) — dữ liệu này nuôi tính năng xếp lại và hồ sơ sở thích. `refine` trả danh sách mới cho các hạng **sau** vị trí người dùng đã xem tới (07 mục 7.2).

`POST /api/v1/recommendations/{logId}/events`

```json
{ "action": "VIEWED", "rank": 3, "candidateId": "…" }
```

`action` ∈ `VIEWED`, `CLICKED`, `FAVORITED`, `CONTACTED`, `DISMISSED`. Ghi `recommendation_click_logs`;
`FAVORITED` (S→T) đồng thời ghi `favorite_tutors`; mỗi sự kiện tạo job `PROFILE_UPDATE` cho H1.

`POST /api/v1/recommendations/{logId}/refine` với `{ "seenUpToRank": 8 }` → `items` mới cho các hạng
sau `seenUpToRank` (07, mục 7.2).

Ghi chú triển khai (13/10/2026): `rank` trong body bị bỏ qua, hạng được lấy từ log; `candidateId` phải là
một kết quả của lần tìm đó (`400 CANDIDATE_NOT_IN_RESULTS`); `logId` của người khác → `404`. `/refine` trả
response cùng cấu trúc với lần tìm (8.3 hoặc 8.4), `items` là các hạng sau `seenUpToRank`.
`DELETE /api/v1/users/me/recommendation-profile` xoá lịch sử gợi ý (07, mục 7.4).

## 8.6. Tìm kiếm đã lưu

> 💡 Lưu, xem, đổi tên, bật / tắt thông báo, xóa tìm kiếm đã lưu (tối đa 5; quá thì lỗi 409 = xung đột). Lưu **bộ lọc** chứ không lưu kết quả: mỗi lần mở lại là một lần tìm mới, phần thiếu được điền từ hồ sơ hiện tại.

`POST /api/v1/users/me/saved-searches`

```json
{ "direction": "STUDENT_TO_TUTOR", "name": "Toán 9 – Cầu Giấy", "filters": { ... },
  "semanticText": "ôn thi vào lớp 10", "notifyEnabled": true }
```

Lỗi `409 SAVED_SEARCH_LIMIT_REACHED` khi đã có 5. Mở một tìm kiếm đã lưu = gọi 8.3/8.4 với
`savedSearchId`.

Ghi chú triển khai (13/10/2026):
- Lưu lại **bộ lọc người dùng gửi** (`filters`, `semanticText`), không lưu `QueryContext` đã gộp: mỗi lần mở,
  phần còn thiếu vẫn được điền từ hồ sơ hiện tại.
- Bộ lọc được kiểm tra như một lần tìm và phải đủ để tìm (môn, lớp): `400 SAVED_SEARCH_INCOMPLETE`,
  `400 INVALID_SAVED_SEARCH`. Học sinh chỉ lưu chiều `STUDENT_TO_TUTOR`, gia sư chỉ lưu `TUTOR_TO_STUDENT`
  (`400 SAVED_SEARCH_DIRECTION_NOT_ALLOWED`).
- `PUT /{id}` chỉ đổi `name` và `notifyEnabled`; muốn đổi tiêu chí thì lưu tìm kiếm mới.
- Gửi `savedSearchId` trong 8.3 / 8.4: tiêu chí đã lưu **thay cho** `filters` trong body; log ghi
  `saved_search_id`. Của người khác hoặc sai chiều → `404 SAVED_SEARCH_NOT_FOUND`.
- `semanticText` chỉ có tác dụng với chiều học sinh tìm gia sư.

## 8.7. Nội dung lưu trong `recommendation_logs`

> 💡 Mỗi lần tìm ghi một dòng **nhật ký** (log): ai tìm, tiêu chí gì, và tối đa 50 kết quả kèm **đủ các con số** (a, b, Q, G, từng đặc trưng…). Nhờ vậy lật trang không phải tính lại, giải thích được "vì sao người này đứng đầu", và có dữ liệu để học trọng số sau này.

| Cột | Nội dung |
|---|---|
| `searcher_student_id` / `searcher_tutor_id` | Người tìm |
| `source_studying_request_id` / `source_teaching_request_id` | Tin đăng của người tìm, **chỉ khi** họ chọn dùng làm tiêu chí (thường để trống) |
| `saved_search_id` | Nếu mở từ tìm kiếm đã lưu |
| `direction`, `algorithm_version` | `STUDENT_TO_TUTOR`, `v2.2-rcx` … |
| `query_text`, `parsed_query`, `query_context` | Câu gốc (đã lọc PII), kết quả A2, tiêu chí cuối cùng |
| `returned_candidate_ids` | ≤ 50 phần tử: `{candidateId, candidateUserId, rank, score, a, b, willingness:{hist, fit, cap, invited}, q, g, wHist, confidence, features:{sem, need, style, segment, price, sched, area, hist}, rerank, isExploration}`; L1 thêm `willingness.fitSegment`, `fitSched`, `fitPrice` (chiều T→S: `fitQuality`, `fitStyle`, `fitPrice`) |
| `query_context.weights` (L1) | `{"A": {model, version, mode, sampled, values}, "B": {…}}` — trọng số **thực sự đã dùng** cho lần tìm |
| `relaxation_suggestions`, `latency_ms` | |

Lưu đủ đặc trưng từng ứng viên để: phân trang và `/refine` không phải tính lại; giải thích "vì sao
người này đứng đầu"; học trọng số từ dữ liệu (L1). L1 bổ sung `query_context.weights` (trọng số đã dùng)
và các thành phần fit của từng ứng viên; API quản trị trọng số ở 12, mục 12.9.
