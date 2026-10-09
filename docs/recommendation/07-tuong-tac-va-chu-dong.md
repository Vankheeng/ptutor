# 7. Tương tác và chủ động (C6, B5, C7, C8)

## 7.1. C6 — Gợi ý nới tiêu chí

### Khi nào kích hoạt

Sau khi xếp hạng xong, nếu **số kết quả có `score ≥ τ_good` ít hơn 5** (τ_good = 0,50) hoặc tổng
số kết quả ít hơn 10.

### Các phép nới

Mỗi phép nới được thử **độc lập** trên `QueryContext` hiện tại:

| Mã | Phép nới | Áp dụng khi |
|---|---|---|
| `PRICE_15` | `max_price × 1,15` | Có `max_price` |
| `PRICE_30` | `max_price × 1,30` | Có `max_price` |
| `MODE_ONLINE` | Chuyển `OFFLINE` → `ONLINE` | Đang chọn `OFFLINE` |
| `AREA_PROVINCE` | `districtIds` = mọi quận cùng tỉnh (`districts.province_id`) | Đang chọn `OFFLINE` |
| `SCHEDULE_FLEX` | Bỏ lịch của học sinh (`f_sched` thiếu, `g_sched = 1`) | Học sinh có lịch |

### Cách đánh giá một phép nới

1. Chạy lại **pha 1–3** (06) với `QueryContext` đã nới. Không gọi LLM; chỉ dùng điểm rerank đã
   có trong cache, không gọi rerank mới.
2. `gain` = số ứng viên **mới** có `score ≥ τ_good` (không có trong tập tốt hiện tại).
3. `bestScore` = điểm cao nhất trong số ứng viên mới.
4. Giữ phép nới nếu `gain ≥ 2`, hoặc `bestScore` cao hơn điểm tốt nhất hiện tại ít nhất 0,10.
5. Sắp xếp theo `gain` giảm dần; khi bằng nhau, ưu tiên phép nới "nhẹ" hơn
   (`SCHEDULE_FLEX` < `PRICE_15` < `AREA_PROVINCE` < `MODE_ONLINE` < `PRICE_30`). Trả tối đa 3.

Các phép nới chạy **song song**, tổng ngân sách 400 ms; phép nào quá giờ thì bỏ.

### Câu hiển thị

| Mã | Mẫu |
|---|---|
| `PRICE_15` / `PRICE_30` | "Nếu tăng ngân sách lên {giá mới}, có thêm {gain} gia sư phù hợp" |
| `MODE_ONLINE` | "Nếu học online, có thêm {gain} gia sư phù hợp, trong đó {k} người đánh giá trên 4,5" |
| `AREA_PROVINCE` | "Mở rộng ra toàn {tên tỉnh}: thêm {gain} gia sư phù hợp" |
| `SCHEDULE_FLEX` | "Nếu linh hoạt lịch học, có thêm {gain} gia sư phù hợp" |

Mỗi gợi ý kèm `apply` — phần thay đổi bộ lọc để giao diện áp dụng bằng một lần bấm (08).

Trường hợp **không có ứng viên nào** qua lọc cứng: trả danh sách rỗng kèm luôn các gợi ý nới.

Chiều T→S chỉ dùng `AREA_PROVINCE` và `SCHEDULE_FLEX`: giá và hình thức dạy là nội dung của tin
dạy đã được duyệt, gia sư không đổi ngay được.

## 7.2. B5 — Xếp lại trong phiên (Rocchio)

### Tín hiệu

Trong một lần tìm (một `recommendation_logs.id`), giao diện gửi các sự kiện:

| Sự kiện | Loại | Cách ghi |
|---|---|---|
| Mở chi tiết | Dương | `VIEWED` |
| Thả tim | Dương | `FAVORITED` (và ghi `favorite_tutors`) |
| Gửi đơn / nhắn liên hệ | Dương | `CONTACTED` |
| Bấm "Không quan tâm" | Âm | `DISMISSED` |
| Bị lướt qua (nằm **trên** một kết quả được mở mà không được mở) | Âm yếu | Suy ra ở backend, không ghi riêng |

### Cập nhật vector truy vấn

```
q′ = normalize( 1.0·q + 0.6·mean(vector các ứng viên dương) − 0.3·mean(vector các ứng viên âm) )
```

Âm "bị lướt qua" có trọng số một nửa so với `DISMISSED`.

### Chấm lại

Chỉ chấm lại **các kết quả người dùng chưa xem** trong tập ứng viên đã lưu của lần tìm đó (tối đa
50 ứng viên đầu, lưu cùng đặc trưng trong `returned_candidate_ids`):

```
f_sem′ = 0.5·f_sem (ban đầu) + 0.5·s( cos(q′, vector ứng viên) )
```

Các đặc trưng khác giữ nguyên; tính lại a, R, `score`; trả về thứ tự mới cho phần chưa xem.
Phần đã hiển thị không bị đảo, để giao diện không "nhảy".

### Phạm vi

- Chỉ áp dụng trong phiên (không ghi vào hồ sơ) — tránh trôi sở thích vì vài cú bấm ngẫu nhiên.
- Cần ít nhất 1 tín hiệu dương. Giao diện gọi `/refine` sau mỗi tín hiệu dương, gộp trong 1 giây.
- Chi phí: chỉ phép tính vector trên ≤ 50 phần tử, không gọi AI service.

## 7.3. C7 — Chủ động thông báo ghép đôi tốt

### Kích hoạt

| Sự kiện | Hành động |
|---|---|
| Tin dạy được duyệt → `OPEN` (đã có sự kiện `TEACHING_REQUEST_APPROVED`) | Job `PAIR_SCORE` cho tin dạy |
| Tin học thanh toán xong → `OPEN` | Job `PAIR_SCORE` cho tin học |
| Tin đăng có embedding mới (sau khi sửa nội dung) | Job `PAIR_SCORE` |
| Hằng đêm | Tính lại các cặp có `content_hash` lệch; xoá cặp của tin không còn `OPEN` |

### `PairScoringWorker` — với một tin dạy mới

1. Tìm mọi tin học `OPEN` mà tin dạy này qua được lọc cứng **theo góc nhìn học sinh** (06, 6.2).
2. Tính a, b, R, score cho từng cặp (pha 3).
3. Với 30 cặp điểm cao nhất: gọi `/rerank` theo lô (không cần timeout chặt vì chạy nền), tính lại,
   lưu `recommendation_pair_scores`.
4. **Gửi thông báo cho học sinh** nếu đồng thời:
   - `final_score ≥ τ_notify` (τ_notify = 0,65);
   - tin dạy mới này nằm trong **top-3** các cặp của tin học đó (so với các dòng khác của cùng
     `studying_request_id` trong `recommendation_pair_scores`);
   - cặp này chưa từng được thông báo (`notified_at IS NULL`);
   - học sinh chưa nhận thông báo loại này trong 24 giờ qua.
5. Thông báo dùng hạ tầng hiện có:
   - `event_type = 'RECOMMENDATION_MATCH_FOUND'`, `reference_type = 'TEACHING_REQUEST'`,
     `reference_id = tr.id`;
   - `deduplication_key = 'rec-match:{sr.id}:{tr.id}'` (đã có unique index);
   - tiêu đề: "Có gia sư mới rất phù hợp với tin tìm gia sư của bạn"; nội dung kèm 1 lý do khớp.
6. Ghi `notified_at`.

### Với một tin học mới

Tương tự theo chiều T→S: chấm tin học mới với mọi tin dạy `OPEN`, lưu `recommendation_pair_scores`
(nhờ vậy lần tìm đầu tiên của học sinh gần như không phải gọi rerank), và **thông báo cho gia sư**
"Có học viên mới phù hợp với tin dạy '{title}'" theo cùng các điều kiện ngưỡng, top-3 và giới hạn
24 giờ.

### Khối lượng

Mỗi tin mới × vài trăm tin phía bên kia → vài trăm cặp tính pha 3 (vài chục ms) và 30 cặp rerank
(vài giây CPU, chạy nền). Không ảnh hưởng người dùng đang tìm.

### Tác dụng nghiệp vụ

Gia sư trả phí đăng tin được "chủ động giới thiệu" tới đúng học sinh phù hợp ngay khi tin được
duyệt — tăng giá trị của tin trả phí; học sinh không phải quay lại tìm thủ công.

## 7.4. C8 — Lý do khớp do LLM viết (tuỳ chọn)

Mặc định dùng câu mẫu (06, mục 6.13). Nếu còn thời gian:
- Chỉ sinh cho **top-5**, gửi LLM các thành phần điểm và tên tag (không gửi văn bản tự do của
  người dùng) để viết 1 câu ≤ 30 từ.
- Cache theo `(studying_request_id, teaching_request_id, model_version)`.
- Gọi bất đồng bộ: trả kết quả với câu mẫu trước, giao diện thay bằng câu LLM khi có.
