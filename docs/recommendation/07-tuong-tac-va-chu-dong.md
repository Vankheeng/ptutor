# 7. Tương tác, chủ động và lịch sử hành vi (C6, B5, C7, H1, C8)

> 💡 **File này nói về gì:** Những gì xảy ra **xung quanh** việc xếp hạng: gợi ý nới tiêu chí khi ít kết quả (C6), sắp lại danh sách ngay khi người dùng bấm xem (B5), tự gửi thông báo khi có tin mới rất hợp (C7), ghi nhớ sở thích qua nhiều lần tìm (H1), và tùy chọn cho LLM viết lý do (C8). Đọc khi làm các tính năng tương tác này.

## 7.1. C6 — Gợi ý nới tiêu chí

> 💡 Khi kết quả tốt quá ít, hệ thống tự thử nới từng tiêu chí (mở rộng khu vực, nhận cả online, tăng ngân sách, bỏ ràng buộc lịch) rồi báo phép nới nào mang thêm nhiều gia sư tốt nhất, kèm nút áp dụng một lần bấm.

### Khi nào kích hoạt

> 💡 Có ít hơn 5 kết quả đạt từ 0,5 điểm trở lên (`τ_good` = ngưỡng "kết quả tốt"), hoặc tổng cộng ít hơn 10 kết quả.

Sau khi xếp hạng, nếu **số kết quả có `score ≥ τ_good` ít hơn 5** (τ_good = 0,50) hoặc tổng số
kết quả ít hơn 10.

### Các phép nới

> 💡 Năm kiểu nới, mỗi kiểu chỉ thử khi có ý nghĩa (ví dụ đang chọn vài quận thì mới thử "toàn tỉnh"). Với giá: học sinh thử tăng ngân sách 15% và 30%; gia sư thử giảm giá mong muốn 10% và 20%. Ví dụ ngân sách 200.000 đ → thử 230.000 đ và 260.000 đ.

Mỗi phép nới thử **độc lập** trên `QueryContext` hiện tại:

| Mã | Phép nới | Áp dụng khi |
|---|---|---|
| `AREA_PROVINCE` | Phạm vi "Các quận" → "Toàn tỉnh/thành" | Đang chọn `DISTRICTS` |
| `AREA_ANY` | Phạm vi → "Không giới hạn" | Đang chọn `DISTRICTS` hoặc `PROVINCE` |
| `MODE_ANY` | Hình thức → "Cả hai" | Đang chọn riêng Online hoặc Offline |
| `PRICE_15` / `PRICE_30` | Học sinh: ngân sách tối đa × 1,15 / × 1,30. Gia sư: giá mong muốn × 0,9 / × 0,8 | Có giá |
| `SCHEDULE_FLEX` | Bỏ lịch rảnh khỏi tiêu chí (`f_sched` thiếu, `g_sched = 1`) | Có lịch |

### Đánh giá một phép nới

> 💡 Với mỗi phép nới, chạy lại phần lọc và chấm điểm, đếm `gain` = số kết quả tốt **mới** có thêm. Giữ phép nới nếu thêm ít nhất 2 kết quả tốt, hoặc có kết quả tốt hơn hẳn (hơn 0,10 điểm). Trả tối đa 3 gợi ý, ưu tiên phép nới nhẹ nhàng hơn khi ngang nhau.

1. Chạy lại **pha 1–3** (06) với `QueryContext` đã nới; không gọi LLM, chỉ dùng rerank có sẵn
   trong cache.
2. `gain` = số ứng viên **mới** có `score ≥ τ_good`; `bestScore` = điểm cao nhất trong số đó.
3. Giữ nếu `gain ≥ 2` hoặc `bestScore` cao hơn điểm tốt nhất hiện tại ≥ 0,10.
4. Sắp theo `gain`; bằng nhau thì ưu tiên phép nới nhẹ hơn
   (`SCHEDULE_FLEX` < `PRICE_15` < `AREA_PROVINCE` < `MODE_ANY` < `AREA_ANY` < `PRICE_30`). Trả tối đa 3.

Chạy song song, ngân sách 400 ms. Không có ứng viên nào qua lọc cứng → trả danh sách rỗng kèm gợi ý.

### Câu hiển thị (kèm nút áp dụng một lần bấm)

> 💡 Mẫu câu cho từng phép nới, ví dụ "Mở rộng ra toàn Hà Nội: thêm 7 gia sư phù hợp".

| Mã | Mẫu |
|---|---|
| `AREA_PROVINCE` | "Mở rộng ra toàn {tỉnh}: thêm {gain} gia sư phù hợp" |
| `AREA_ANY` | "Bỏ giới hạn khu vực: thêm {gain} gia sư phù hợp" |
| `MODE_ANY` | "Nhận cả học online: thêm {gain} gia sư, trong đó {k} người đánh giá trên 4,5" |
| `PRICE_*` | "Nếu ngân sách {giá mới}: thêm {gain} gia sư phù hợp" |
| `SCHEDULE_FLEX` | "Nếu linh hoạt lịch học: thêm {gain} gia sư phù hợp" |

### Ghi chú triển khai (12/10/2026)

> 💡 Khác thiết kế ở vài điểm khi lập trình: đếm theo **người** (không theo tin); chạy lần lượt từ phép nhẹ tới nặng trong 400 ms thay vì chạy song song; không dùng rerank khi thử nới vì quá chậm.

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

> 💡 Ngay trong một lần tìm, sau khi người dùng mở vài tin, phần danh sách **chưa xem** được sắp lại cho giống các tin vừa mở hơn. Dùng kỹ thuật **Rocchio** — một kỹ thuật kinh điển trong tìm kiếm: dịch "hướng tìm" về phía kết quả người dùng thích, ra xa kết quả họ bỏ qua.

### Tín hiệu trong một lần tìm

> 💡 Hành động nào được coi là thích (dương: mở xem, thả tim, gửi đơn) hoặc không thích (âm: bấm "Không quan tâm"; âm yếu: bị lướt qua — nằm trên một tin được mở mà không được mở).

| Sự kiện | Loại | Ghi |
|---|---|---|
| Mở chi tiết | Dương | `VIEWED` |
| Thả tim | Dương | `FAVORITED` (+ `favorite_tutors` với chiều S→T) |
| Gửi đơn / đề nghị / nhắn liên hệ | Dương | `CONTACTED` |
| Bấm "Không quan tâm" | Âm | `DISMISSED` |
| Bị lướt qua (nằm trên một kết quả được mở mà không được mở) | Âm yếu | Suy ra ở backend |

### Cập nhật vector truy vấn và chấm lại

> 💡 q là vector nhu cầu ban đầu. Vector mới q′ = giữ nguyên q, cộng thêm 0,6 lần trung bình vector các tin được thích, trừ 0,3 lần trung bình vector các tin bị bỏ qua (`normalize` = đưa vector về độ dài 1). Sau đó điểm nội dung của mỗi tin chưa xem = nửa điểm cũ + nửa điểm so với q′. Ví dụ: người dùng mở hai tin kiểu luyện đề → q′ nghiêng về "luyện đề" → các tin luyện đề chưa xem được đẩy lên. Phần đã hiển thị không bị đảo, không gọi AI service.

```
q′ = normalize( 1.0·q + 0.6·mean(vector ứng viên dương) − 0.3·mean(vector ứng viên âm) )
f_sem′ = 0.5·f_sem (ban đầu) + 0.5·s( cos(q′, vector ứng viên) )
```

Âm "bị lướt qua" có trọng số một nửa `DISMISSED`. Chỉ chấm lại **phần chưa xem** trong ≤ 50 ứng
viên đã lưu cùng lần tìm; phần đã hiển thị không bị đảo. Cần ≥ 1 tín hiệu dương; giao diện gọi
`/refine` sau mỗi tín hiệu dương (gộp trong 1 giây). Không gọi AI service.

B5 là thích ứng **ngắn hạn** (trong phiên). Thích ứng **dài hạn** qua nhiều phiên là H1 (mục 7.4).

### Ghi chú triển khai (13/10/2026)

> 💡 Chi tiết khi lập trình: chỉ tính lại điểm nội dung rồi suy ra điểm mới từ các con số đã lưu trong nhật ký (không tính lại các đặc trưng khác); thứ tự mới được ghi đè vào nhật ký để lật trang vẫn đúng thứ tự người dùng đang thấy.

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

> 💡 Khi một tin mới được đăng, hệ thống tự kiểm tra nó có rất hợp với ai đang tìm không, và gửi thông báo cho người đó — kể cả người chưa có tin đăng (nhờ tìm kiếm đã lưu).

### Nguồn "nhu cầu" được theo dõi

> 💡 Những gì được coi là "nhu cầu đang chờ" để đối chiếu với tin mới: tin đăng đang mở của người dùng, và các tìm kiếm họ đã lưu có bật thông báo.

| Phía nhận thông báo | Nguồn nhu cầu |
|---|---|
| Học sinh | (1) Tin học `OPEN` của học sinh; (2) tìm kiếm đã lưu (chiều S→T, bật thông báo) |
| Gia sư | (1) Tin dạy `OPEN` của gia sư (dùng như tiêu chí); (2) tìm kiếm đã lưu (chiều T→S, bật thông báo) |

Nhờ có "tìm kiếm đã lưu", người **không có tin đăng** vẫn nhận được thông báo.

### Kích hoạt

> 💡 Thời điểm kiểm tra: khi tin dạy được duyệt, khi tin học thanh toán xong, khi tin được sửa và có vector mới.

| Sự kiện | Việc làm |
|---|---|
| Tin dạy được duyệt → `OPEN` (sự kiện `TEACHING_REQUEST_APPROVED` đã có) | Job `MATCH_ALERT` cho tin dạy: đối chiếu với mọi nguồn nhu cầu phía học sinh |
| Tin học thanh toán xong → `OPEN` | Job `MATCH_ALERT` cho tin học: đối chiếu với mọi nguồn nhu cầu phía gia sư |
| Tin đăng có embedding mới (sau khi sửa) | Như trên |
| Hằng đêm | Rerank sẵn top-30 cho các tin học `OPEN` và tìm kiếm đã lưu → `recommendation_rerank_cache` |

### `MatchAlertWorker` — với một tin dạy mới

> 💡 Với mỗi nhu cầu đang chờ: chạy thuật toán xếp hạng như một lần tìm thật, xem tin mới đứng hạng mấy. Chỉ gửi thông báo khi đủ cả bốn điều kiện: điểm ≥ 0,65 (`τ_notify`), nằm trong top-3, cặp này chưa từng được báo, và người nhận chưa nhận thông báo loại này trong 24 giờ — để không làm phiền.

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

### Ghi chú triển khai (13/10/2026)

> 💡 Chi tiết khi lập trình: việc kiểm tra được kích hoạt ngay sau khi tính xong vector của tin (một điểm phủ mọi trường hợp tin mới hoặc tin sửa); nội dung thông báo cho gia sư không có tên học sinh; mỗi người tối đa một thông báo loại này mỗi 24 giờ.

- **Kích hoạt:** job `MATCH_ALERT` được xếp khi job embedding của một tin `OPEN` / `MATCHED` chạy xong (có
  vector mới, hoặc vector vẫn đúng). Một điểm này phủ cả "tin vừa thành `OPEN`" (duyệt xong, thanh toán
  xong) lẫn "tin được sửa", vì cả hai đều xếp job embedding; không phải sửa các service tin đăng. Hệ quả: tin
  vừa đăng được đối chiếu hai lần (sau embedding đầu, và sau khi LLM gắn tag làm embedding đổi); lần hai
  không gửi trùng nhờ quy tắc "cặp (nguồn, tin) chỉ thông báo một lần".
- **Chỉ tin đang `OPEN`** mới sinh thông báo; nguồn nhu cầu được lọc trước theo **môn** của tin.
- Nguồn phía gia sư chỉ lấy gia sư `VERIFIED`; người nhận phải có tài khoản `ACTIVE`.
- **Hạng** tính sau khi gộp theo người và rerank (ngân sách nền 120 s), không có slot khám phá. Tin bị một
  tin khác của cùng người đăng xếp trên thì không được thông báo.
- **Một người tối đa một thông báo loại này trong 24 giờ** (tính trên mọi nguồn của người đó). Tin đến trong
  khoảng đó không được bù lại sau; đây là đánh đổi có chủ ý để không làm phiền.
- Nội dung: học sinh thấy "{tên gia sư} — {tiêu đề tin}. {lý do khớp đầu tiên}."; gia sư thấy "{tiêu đề tin
  học}. {lý do}." — **không có tên học sinh**. Loại hiển thị `REQUEST`.
- `recommendation_saved_searches.last_notified_at` được cập nhật khi nguồn là tìm kiếm đã lưu.
- Mỗi nguồn chạy trong transaction riêng: một tìm kiếm đã lưu không còn chạy được (môn bị tắt…) không chặn
  các nguồn khác. Cờ tắt: `proactive.enabled=false`.
- `RerankPrefetchJob` nay chấm sẵn cả cho tìm kiếm đã lưu của hai chiều (việc còn mở từ bước rerank).
- **Giao diện (20/10/2026):** trên trang Thông báo, bấm một thông báo `RECOMMENDATION_MATCH_FOUND` sẽ mở đúng tin:
  học sinh → trang "Tìm gia sư" với `?posting=<id>` (khung tin dạy được gợi ý kèm form đăng ký học); gia sư → trang
  chi tiết tin tìm gia sư có sẵn (`/tutor/studying-requests/<id>`).

## 7.4. H1 — Hồ sơ sở thích dài hạn (gợi ý theo lịch sử hành vi)

> 💡 Qua nhiều lần tìm, hệ thống ghi nhớ người dùng hay quan tâm kiểu tin nào, lưu thành một **hồ sơ sở thích**, rồi dùng nó làm đặc trưng `f_hist` khi chấm điểm (06 mục 6.4). Chỉ dùng hành vi của chính người đó.

### Tín hiệu

> 💡 Mỗi hành vi có một điểm: xem +1, tym +2, gửi đơn +3, được nhận / ký hợp đồng +4, "Không quan tâm" −2. Chỉ xét 180 ngày gần nhất và **mờ dần theo thời gian**: sau 30 ngày một tín hiệu chỉ còn nửa giá trị ("chu kỳ bán rã"), sau 60 ngày còn một phần tư.

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

> 💡 Vector sở thích `v_pref` = tổng vector của các tin trong lịch sử, mỗi tin nhân với điểm hành vi và độ mờ theo thời gian. Mức ưa thích từng tag `aff(tag)` = tỉ lệ "điểm hành vi" rơi vào các tin có tag đó. Ví dụ phần lớn các tin người dùng tym đều có tag `luyen_tap_nhieu` → `aff(luyen_tap_nhieu)` cao.

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

> 💡 Hồ sơ được tính lại khi có hành vi mới, và tính lại toàn bộ mỗi đêm để áp dụng độ mờ theo thời gian.

- Listener sự kiện (sau commit, transaction riêng `REQUIRES_NEW`) khi có click/tym/đơn/đổi trạng
  thái đơn → tính lại hồ sơ của người đó từ dữ liệu 180 ngày (vài chục đến vài trăm dòng, rẻ).
- Job hằng đêm tính lại toàn bộ để áp dụng giảm theo thời gian.

### Ghi chú triển khai (13/10/2026)

> 💡 Khi lập trình: dùng hàng đợi việc nền thay cho listener; mỗi tin chỉ tính **một** tín hiệu mạnh nhất (mở rồi gửi đơn là một lần quan tâm, không phải hai); thêm mốc "xóa lịch sử" để tín hiệu cũ hơn mốc này bị bỏ qua vĩnh viễn.

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
- **Xoá lịch sử** cần thêm cột `history_reset_at` (migration V33): hồ sơ được dựng lại từ dữ liệu 180 ngày nên
  chỉ xoá dòng là chưa đủ. Tín hiệu trước mốc này bị bỏ qua vĩnh viễn; tym, đơn, hợp đồng không bị xoá.

### Quyền của người dùng

> 💡 Người dùng xóa được hồ sơ sở thích bất cứ lúc nào, và lý do khớp nói rõ khi một kết quả được đẩy lên nhờ lịch sử.

- Nút **"Xoá lịch sử gợi ý"**: xoá hồ sơ sở thích, `w_hist` về 0. Giao diện (20/10/2026): dòng cuối của menu
  "Đã lưu" trên hàng công cụ của hai trang tìm, có bước xác nhận ngay tại chỗ.
- Lý do khớp nói rõ khi kết quả được đẩy lên nhờ lịch sử ("Giống các gia sư bạn đã quan tâm…").

### Vì sao không phải collaborative filtering

> 💡 **Collaborative filtering** (lọc cộng tác) là kiểu "người giống bạn cũng thích…", cần rất nhiều tương tác giữa nhiều người. H1 chỉ dùng lịch sử của chính người đó cộng với nội dung tin, nên chạy được ngay cả khi dữ liệu còn ít.

H1 chỉ dùng **lịch sử của chính người đó** kết hợp nội dung (vector, tag), nên chạy được khi dữ liệu
còn ít. "Người giống bạn cũng thích…" cần lượng tương tác lớn giữa nhiều người — không phù hợp
giai đoạn này.

## 7.5. C8 — Lý do khớp do LLM viết (tuỳ chọn)

> 💡 Tùy chọn nếu còn thời gian: cho LLM viết lại câu lý do cho 5 kết quả đầu tự nhiên hơn. Chỉ gửi các con số và tên tag, không gửi văn bản người dùng; hiện câu mẫu trước, có câu LLM thì thay.

Mặc định dùng câu mẫu (06, mục 6.13). Nếu còn thời gian: chỉ cho top-5, gửi LLM các thành phần
điểm và tên tag (không gửi văn bản tự do của người dùng), cache theo (mã băm nhu cầu, ứng viên,
model), gọi bất đồng bộ — trả câu mẫu trước, thay bằng câu LLM khi có.

## 7.6. Quan hệ với L1 (học trọng số)

> 💡 Phân biệt hai việc "học": H1 / B5 học sở thích **của từng người**; L1 (đang thiết kế) học **mức quan trọng chung** của từng tiêu chí cho mọi người dùng. Hai bên dùng thang điểm tín hiệu khác nhau và các thang này cố định, không được học.

Các tín hiệu ở chương này còn được L1 dùng làm phần thưởng ([12](./12-hoc-trong-so.md), mục 12.4). Ba điểm
cần phân biệt:

| | H1 / B5 (chương này) | L1 |
|---|---|---|
| Học cái gì | Sở thích **của từng người** (vector, tag ưa thích) | Trọng số gộp **chung** của một chiều tìm kiếm |
| Thang tín hiệu | H1: xem 1, tym 2, liên hệ / gửi đơn 3, được nhận 4, bỏ qua −2 | Xem 0,3, tym 0,6, liên hệ 0,8, gửi đơn 1,0, bỏ qua −0,2 — chỉ hành động của người tìm; "được nhận" thuộc phía b |
| Khi nào cập nhật | H1: hằng đêm và theo sự kiện; B5: ngay trong phiên | Hằng đêm, theo lô |

Hai thang tín hiệu là **định nghĩa**, cố định, không được học (12, mục 12.2). Cá nhân hoá vẫn do H1 và B5 đảm
nhận; L1 chỉ chỉnh mức quan trọng chung của từng đặc trưng.

**C7 và L1:** để dùng tín hiệu tuỳ chọn "bấm vào thông báo ghép đôi" (12.4), thông báo cần mang mã alert
(`recommendation_match_alerts.id`) trong đường dẫn để sự kiện truy được về thông báo. Hiện thông báo chưa mang
mã này; tín hiệu để tắt (`rewards.alert-click = false`) cho tới khi bổ sung.
