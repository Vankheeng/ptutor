# 7. Tương tác, chủ động và lịch sử hành vi (C6, B5, C7, H1, C8)

## 7.1. C6 — Gợi ý nới tiêu chí

### Khi nào kích hoạt

Sau khi xếp hạng, nếu **số kết quả có `score ≥ τ_good` ít hơn 5** (τ_good = 0,50) hoặc tổng số
kết quả ít hơn 10.

### Các phép nới

Mỗi phép nới thử **độc lập** trên `QueryContext` hiện tại:

| Mã | Phép nới | Áp dụng khi |
|---|---|---|
| `AREA_PROVINCE` | Phạm vi "Các quận" → "Toàn tỉnh/thành" | Đang chọn `DISTRICTS` |
| `AREA_ANY` | Phạm vi → "Không giới hạn" | Đang chọn `DISTRICTS` hoặc `PROVINCE` |
| `MODE_ANY` | Hình thức → "Cả hai" | Đang chọn riêng Online hoặc Offline |
| `PRICE_15` / `PRICE_30` | Học sinh: ngân sách tối đa × 1,15 / × 1,30. Gia sư: giá mong muốn × 0,9 / × 0,8 | Có giá |
| `SCHEDULE_FLEX` | Bỏ lịch rảnh khỏi tiêu chí (`f_sched` thiếu, `g_sched = 1`) | Có lịch |

### Đánh giá một phép nới

1. Chạy lại **pha 1–3** (06) với `QueryContext` đã nới; không gọi LLM, chỉ dùng rerank có sẵn
   trong cache.
2. `gain` = số ứng viên **mới** có `score ≥ τ_good`; `bestScore` = điểm cao nhất trong số đó.
3. Giữ nếu `gain ≥ 2` hoặc `bestScore` cao hơn điểm tốt nhất hiện tại ≥ 0,10.
4. Sắp theo `gain`; bằng nhau thì ưu tiên phép nới nhẹ hơn
   (`SCHEDULE_FLEX` < `PRICE_15` < `AREA_PROVINCE` < `MODE_ANY` < `AREA_ANY` < `PRICE_30`). Trả tối đa 3.

Chạy song song, ngân sách 400 ms. Không có ứng viên nào qua lọc cứng → trả danh sách rỗng kèm gợi ý.

### Câu hiển thị (kèm nút áp dụng một lần bấm)

| Mã | Mẫu |
|---|---|
| `AREA_PROVINCE` | "Mở rộng ra toàn {tỉnh}: thêm {gain} gia sư phù hợp" |
| `AREA_ANY` | "Bỏ giới hạn khu vực: thêm {gain} gia sư phù hợp" |
| `MODE_ANY` | "Nhận cả học online: thêm {gain} gia sư, trong đó {k} người đánh giá trên 4,5" |
| `PRICE_*` | "Nếu ngân sách {giá mới}: thêm {gain} gia sư phù hợp" |
| `SCHEDULE_FLEX` | "Nếu linh hoạt lịch học: thêm {gain} gia sư phù hợp" |

### Ghi chú triển khai (12/10/2026)

- **Đếm theo người**: `gain` là số gia sư (học viên) *chưa có trong kết quả hiện tại* đạt `score ≥ τ_good`,
  sau khi gộp mỗi người một tin.
- **Chạy lần lượt, không song song**, theo thứ tự nhẹ → nặng, dừng khi hết ngân sách 400 ms (phép nới chưa
  kịp thử thì bỏ). Lý do: chạy song song phải mở thêm kết nối database ngoài transaction của lần tìm.
- **Không dùng rerank** khi đánh giá phép nới (kể cả từ cache): tra cache cần dựng lại văn bản của từng tin,
  quá đắt so với ngân sách. `gain` vì vậy tính theo điểm pha 3.
- Văn bản nhu cầu và vector truy vấn giữ nguyên; chỉ điều kiện lọc và đặc trưng liên quan thay đổi.
- Giá mới làm tròn theo nghìn (học sinh làm tròn lên, gia sư làm tròn xuống).
- `AREA_PROVINCE` lấy tỉnh của quận đã chọn (nếu không có thì tỉnh nơi ở).
- `MODE_ANY` khi đang chọn riêng Online: phần gặp trực tiếp cần khu vực, lấy quận nơi ở (như lần tìm không
  chọn khu vực); `apply` ghi rõ cả `learningMode` và `area` để bấm áp dụng ra đúng kết quả đã đếm.
- `SCHEDULE_FLEX`: `apply` là `{"availabilities": []}` — danh sách rỗng nghĩa là "không xét lịch" (khác với
  không gửi trường này: khi đó lịch lấy từ tin học / tin dạy).
- Mẫu câu chiều gia sư: thay "gia sư" bằng "học viên"; giá: "Nếu nhận mức {giá mới} mỗi buổi: thêm {gain}
  học viên phù hợp"; lịch: "Nếu linh hoạt lịch dạy: …"; hình thức: "Nhận cả dạy online / trực tiếp: …".
- Gợi ý được lưu vào `recommendation_logs.relaxation_suggestions` và trả lại ở các trang sau.
- Phép nới lỗi khi đánh giá thì bị bỏ qua, không làm hỏng lần tìm. Cờ tắt: `relaxation.enabled=false`.

## 7.2. B5 — Xếp lại trong phiên (Rocchio)

### Tín hiệu trong một lần tìm

| Sự kiện | Loại | Ghi |
|---|---|---|
| Mở chi tiết | Dương | `VIEWED` |
| Thả tim | Dương | `FAVORITED` (+ `favorite_tutors` với chiều S→T) |
| Gửi đơn / đề nghị / nhắn liên hệ | Dương | `CONTACTED` |
| Bấm "Không quan tâm" | Âm | `DISMISSED` |
| Bị lướt qua (nằm trên một kết quả được mở mà không được mở) | Âm yếu | Suy ra ở backend |

### Cập nhật vector truy vấn và chấm lại

```
q′ = normalize( 1.0·q + 0.6·mean(vector ứng viên dương) − 0.3·mean(vector ứng viên âm) )
f_sem′ = 0.5·f_sem (ban đầu) + 0.5·s( cos(q′, vector ứng viên) )
```

Âm "bị lướt qua" có trọng số một nửa `DISMISSED`. Chỉ chấm lại **phần chưa xem** trong ≤ 50 ứng
viên đã lưu cùng lần tìm; phần đã hiển thị không bị đảo. Cần ≥ 1 tín hiệu dương; giao diện gọi
`/refine` sau mỗi tín hiệu dương (gộp trong 1 giây). Không gọi AI service.

B5 là thích ứng **ngắn hạn** (trong phiên). Thích ứng **dài hạn** qua nhiều phiên là H1 (mục 7.4).

### Ghi chú triển khai (13/10/2026)

- Tín hiệu dương: `VIEWED`, `CLICKED`, `FAVORITED`, `CONTACTED`. Tin vừa có tín hiệu dương vừa bị `DISMISSED`
  thì tính là âm.
- "Bị lướt qua" = tin có hạng nhỏ hơn hạng thấp nhất trong các tin được mở, mà không được mở cũng không bị
  bỏ qua; trọng số 0,5 trong trung bình âm.
- Chấm lại không cần tính lại đặc trưng: `a′ = a + (1 − w_hist)·w_sem·(f_sem′ − f_sem)`, rồi R, `score` từ các
  giá trị b, Q, G đã lưu trong log. Tin không có vector hoặc không có `f_sem` giữ nguyên.
- `f_sem (ban đầu)` và `a` ban đầu được giữ trong log (`initial`), nên gọi `/refine` nhiều lần không cộng dồn.
- Thứ tự mới **ghi đè vào log**: lật trang bằng `logId` theo đúng thứ tự người dùng đang thấy.
- Vector truy vấn lấy lại từ văn bản nhu cầu lưu trong log, qua bộ nhớ đệm của lần tìm (không gọi AI service
  khi còn trong bộ nhớ đệm; nếu đã mất và AI service không trả lời thì trả về thứ tự cũ).
- Không có tín hiệu dương → trả về thứ tự đang lưu. Cờ tắt: `refine.enabled=false`.

## 7.3. C7 — Chủ động thông báo ghép đôi tốt

### Nguồn "nhu cầu" được theo dõi

| Phía nhận thông báo | Nguồn nhu cầu |
|---|---|
| Học sinh | (1) Tin học `OPEN` của học sinh; (2) tìm kiếm đã lưu (chiều S→T, bật thông báo) |
| Gia sư | (1) Tin dạy `OPEN` của gia sư (dùng như tiêu chí); (2) tìm kiếm đã lưu (chiều T→S, bật thông báo) |

Nhờ có "tìm kiếm đã lưu", người **không có tin đăng** vẫn nhận được thông báo.

### Kích hoạt

| Sự kiện | Việc làm |
|---|---|
| Tin dạy được duyệt → `OPEN` (sự kiện `TEACHING_REQUEST_APPROVED` đã có) | Job `MATCH_ALERT` cho tin dạy: đối chiếu với mọi nguồn nhu cầu phía học sinh |
| Tin học thanh toán xong → `OPEN` | Job `MATCH_ALERT` cho tin học: đối chiếu với mọi nguồn nhu cầu phía gia sư |
| Tin đăng có embedding mới (sau khi sửa) | Như trên |
| Hằng đêm | Rerank sẵn top-30 cho các tin học `OPEN` và tìm kiếm đã lưu → `recommendation_rerank_cache` |

### `MatchAlertWorker` — với một tin dạy mới

1. Với mỗi nguồn nhu cầu phía học sinh: dựng `QueryContext`, kiểm tra tin dạy mới có qua lọc cứng
   không.
2. Nếu qua: chạy pha 1–4 cho nguồn đó (rerank không giới hạn thời gian chặt vì chạy nền), xác định
   **hạng** của tin dạy mới.
3. **Gửi thông báo** nếu đồng thời: `score ≥ τ_notify` (0,65); tin mới nằm trong **top-3** của nguồn;
   cặp (nguồn, tin) chưa từng được thông báo; người nhận chưa nhận thông báo loại này trong 24 giờ.
4. Ghi `recommendation_match_alerts` và tạo notification:
   `event_type = 'RECOMMENDATION_MATCH_FOUND'`, `reference_type = 'TEACHING_REQUEST'`,
   `reference_id = tr.id`, `deduplication_key = 'rec-match:{sourceType}:{sourceId}:{tr.id}'`.
   Tiêu đề: "Có gia sư mới rất phù hợp với tiêu chí của bạn"; nội dung kèm 1 lý do khớp.

Với tin học mới: tương tự theo chiều T→S, thông báo cho gia sư "Có học viên mới phù hợp với tiêu
chí của bạn".

Khối lượng: mỗi tin mới × vài trăm nguồn nhu cầu → chạy nền vài giây đến vài chục giây, không ảnh
hưởng người đang tìm.

## 7.4. H1 — Hồ sơ sở thích dài hạn (gợi ý theo lịch sử hành vi)

### Tín hiệu

| Hành vi | Trọng số | Nguồn |
|---|---|---|
| Mở xem chi tiết | +1 | `recommendation_click_logs` (`VIEWED`, `CLICKED`) |
| Thả tim | +2 | `favorite_tutors`, click log `FAVORITED` |
| Gửi đơn / đề nghị | +3 | `student_tutor_requests` / `tutor_student_requests` (người này là bên gửi) |
| Được chấp nhận / ký hợp đồng | +4 | Trạng thái `ACCEPTED`, `contracts` |
| Không quan tâm | −2 | Click log `DISMISSED` |

Chỉ xét 180 ngày gần nhất. Mỗi tín hiệu giảm theo thời gian với chu kỳ bán rã 30 ngày:
`d_i = 0,5^(tuổi_ngày / 30)`.

### Tổng hợp

Gọi `e_i` là vector của ứng viên trong tín hiệu i (tin dạy với học sinh, tin học với gia sư), `w_i`
là trọng số:

```
v_pref    = normalize( Σ_i w_i · d_i · e_i )
aff(tag)  = clip( Σ_i w_i · d_i · [tag ∈ tag(i)] / Σ_i |w_i| · d_i , 0, 1 )     (tag STYLE/TOPIC/GOAL/SEGMENT)
signals   = số tín hiệu dương trong 180 ngày
```

Lưu vào `recommendation_user_profiles` (`preference_embedding`, `tag_affinity` dạng JSON,
`signal_count`). Thuật toán dùng qua đặc trưng `f_hist` và trọng số tăng dần `w_hist` (06, mục 6.4).

### Cập nhật

- Listener sự kiện (sau commit, transaction riêng `REQUIRES_NEW`) khi có click/tym/đơn/đổi trạng
  thái đơn → tính lại hồ sơ của người đó từ dữ liệu 180 ngày (vài chục đến vài trăm dòng, rẻ).
- Job hằng đêm tính lại toàn bộ để áp dụng giảm theo thời gian.

### Ghi chú triển khai (13/10/2026)

- **Cập nhật qua hàng đợi** (quyết định: theo 08, không dùng listener): mỗi sự kiện xếp một job
  `PROFILE_UPDATE` cho người đó trong `ai_enrichment_jobs`; nhiều sự kiện liên tiếp gộp thành một job.
  `ProfileUpdateWorker` chạy mỗi 10 giây, có thử lại. 19:30 UTC hằng đêm xếp lại mọi hồ sơ để áp dụng giảm
  theo thời gian.
- **Mỗi tin một tín hiệu**: lấy hành vi mạnh nhất với tin đó (mở rồi gửi đơn là một dấu hiệu quan tâm, không
  phải hai). `DISMISSED` thắng nếu xảy ra sau cùng. `signals` = số tin có tín hiệu dương.
- Trọng số: `VIEWED`/`CLICKED` +1, `FAVORITED` +2, `CONTACTED` +3, đã gửi đơn / đề nghị +3, đơn `ACCEPTED`
  +4, `DISMISSED` −2. Hợp đồng không tính riêng (đã thể hiện qua đơn `ACCEPTED`).
- `favorite_tutors` hiện chỉ được ghi qua sự kiện `FAVORITED`, nên không đọc bảng này lần nữa.
- Vector và tag của một tín hiệu là của tin đăng tại thời điểm tính lại (cộng tag của người đăng).
- `tag_affinity` lưu theo mã tag; chỉ lưu giá trị dương.
- Đổi trạng thái đơn chưa xếp job ngay (không sửa các service đơn có sẵn); job đêm cập nhật.
- **Xoá lịch sử** cần thêm cột `history_reset_at` (migration V27): hồ sơ được dựng lại từ dữ liệu 180 ngày nên
  chỉ xoá dòng là chưa đủ. Tín hiệu trước mốc này bị bỏ qua vĩnh viễn; tym, đơn, hợp đồng không bị xoá.

### Quyền của người dùng

- Nút **"Xoá lịch sử gợi ý"**: xoá hồ sơ sở thích, `w_hist` về 0.
- Lý do khớp nói rõ khi kết quả được đẩy lên nhờ lịch sử ("Giống các gia sư bạn đã quan tâm…").

### Vì sao không phải collaborative filtering

H1 chỉ dùng **lịch sử của chính người đó** kết hợp nội dung (vector, tag), nên chạy được khi dữ liệu
còn ít. "Người giống bạn cũng thích…" cần lượng tương tác lớn giữa nhiều người — không phù hợp
giai đoạn này.

## 7.5. C8 — Lý do khớp do LLM viết (tuỳ chọn)

Mặc định dùng câu mẫu (06, mục 6.13). Nếu còn thời gian: chỉ cho top-5, gửi LLM các thành phần
điểm và tên tag (không gửi văn bản tự do của người dùng), cache theo (mã băm nhu cầu, ứng viên,
model), gọi bất đồng bộ — trả câu mẫu trước, thay bằng câu LLM khi có.
