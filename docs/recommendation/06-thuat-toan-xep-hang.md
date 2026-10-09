# 6. Thuật toán xếp hạng (R1, R2, R3, R6, B4)

## 6.1. Tổng quan

```
QueryContext
  │
  ├─ Pha 1  Lọc cứng (SQL)                         → tập ứng viên hợp lệ
  ├─ Pha 2  Lấy ứng viên theo vector (≤ 200)       → tập ứng viên
  ├─ Pha 3  Chấm điểm sơ bộ cho mọi ứng viên
  │           a  = độ phù hợp theo người tìm        (6.4)
  │           b  = khả năng phía kia đồng ý         (6.6, gồm chống dồn B4)
  │           R  = 2ab / (a + b)                    (6.7, reciprocal R1)
  │           score = R × (0.7 + 0.3·Q) × G         (6.8, cấu trúc nhân R2)
  ├─ Pha 4  Rerank top-20 bằng cross-encoder (R3)  (6.9)
  ├─ Pha 5  Gộp theo gia sư, chèn slot khám phá (R6) (6.10, 6.11)
  └─ Pha 6  Sinh lý do khớp, ghi log               (6.13)
```

Mục 6.2–6.11 mô tả chiều **học sinh tìm gia sư (S→T)**. Chiều ngược lại ở mục 6.12.

## 6.2. Pha 1 — Lọc cứng (S→T)

Điều kiện bắt buộc với tin dạy `tr`:

| Điều kiện | Lý do |
|---|---|
| `tr.status = 'OPEN'`, chưa xoá | Chỉ tin đã trả phí, đã duyệt, còn chỗ (đủ chỗ thì hệ thống tự chuyển `MATCHED`) |
| `tutors.profile_status = 'VERIFIED'`, `users.status = 'ACTIVE'` | Quy tắc đã có trong README |
| `tr.subject_id = môn cần học` | |
| Lớp cần học thuộc `grade_teaching_requests` của `tr` | |
| `tr.teaching_mode = hình thức học` (nếu người dùng đã chọn) | Chỉ có `OFFLINE`/`ONLINE` |
| Nếu `OFFLINE`: quận của học sinh thuộc `teaching_request_districts` của `tr` | Không dùng toạ độ |
| `tr.expected_price ≤ 1,5 × max_price` (nếu có `max_price`) | Lệch quá 50% coi như không hợp; dưới mức đó chấm mềm ở `f_price` |
| Không có hợp đồng `PENDING`/`ACTIVE` giữa hai người cho cùng môn | Không đề xuất lại người đang học |
| Học sinh chưa có đơn `PENDING`/`ACCEPTED` vào `tr`, và không bị từ chối ở `tr` trong 30 ngày | Tránh gợi ý lại tin đã gửi |

```sql
SELECT tr.id AS teaching_request_id, tr.tutor_id
FROM teaching_requests tr
JOIN tutors t ON t.id = tr.tutor_id AND t.deleted_at IS NULL
JOIN users  u ON u.id = t.user_id  AND u.deleted_at IS NULL
WHERE tr.deleted_at IS NULL
  AND tr.status = 'OPEN'
  AND t.profile_status = 'VERIFIED'
  AND u.status = 'ACTIVE'
  AND tr.subject_id = :subjectId
  AND EXISTS (SELECT 1 FROM grade_teaching_requests g
              WHERE g.teaching_request_id = tr.id AND g.grade_id = :gradeId AND g.deleted_at IS NULL)
  AND (CAST(:mode AS varchar) IS NULL OR tr.teaching_mode = :mode)
  AND (tr.teaching_mode = 'ONLINE'
       OR EXISTS (SELECT 1 FROM teaching_request_districts d
                  WHERE d.teaching_request_id = tr.id AND d.deleted_at IS NULL
                    AND d.district_id = ANY(CAST(:districtIds AS uuid[]))))
  AND (CAST(:maxPrice AS numeric) IS NULL OR tr.expected_price IS NULL
       OR tr.expected_price <= :maxPrice * 1.5)
  AND NOT EXISTS (SELECT 1 FROM contracts c
                  WHERE c.student_id = :studentId AND c.tutor_id = tr.tutor_id
                    AND c.subject_id = tr.subject_id AND c.status IN ('PENDING', 'ACTIVE')
                    AND c.deleted_at IS NULL)
  AND NOT EXISTS (SELECT 1 FROM student_tutor_requests s
                  WHERE s.student_id = :studentId AND s.teaching_request_id = tr.id
                    AND s.deleted_at IS NULL
                    AND (s.status IN ('PENDING', 'ACCEPTED')
                         OR (s.status = 'REJECTED' AND s.updated_at > CURRENT_TIMESTAMP - INTERVAL '30 days')));
```

`:districtIds` là một mảng: bình thường chỉ có quận của học sinh; khi nới tiêu chí (07) có thể là
mọi quận trong tỉnh. Nếu chưa biết quận của học sinh (hồ sơ trống, câu tìm kiếm không nói), chỉ
còn tin `ONLINE` qua được lọc, và giao diện nhắc người dùng chọn khu vực.

## 6.3. Pha 2 — Lấy ứng viên theo vector

- Nếu số ứng viên hợp lệ ≤ 200: lấy hết.
- Nếu > 200: lấy 180 ứng viên gần nhất theo `tr.embedding <=> :queryVector`, cộng 20 ứng viên
  **chưa có embedding** mới nhất (để tin mới chưa kịp embed không bị loại oan).

Quét tuần tự, không index ANN — kết quả chính xác, đủ nhanh ở quy mô đồ án.

## 6.4. Độ phù hợp a (theo góc nhìn học sinh)

Mỗi đặc trưng nằm trong [0, 1] hoặc **thiếu** (không đủ dữ liệu).

### `f_sem` — ngữ nghĩa

```
cos   = 1 − (queryVector <=> tr.embedding)
s     = clip((cos − 0.30) / (0.80 − 0.30), 0, 1)
f_sem = 0.3·s + 0.7·rerank     nếu có điểm rerank (6.9)
f_sem = s                      nếu chưa có
```

Dùng **ngưỡng cố định** (0,30 và 0,80) thay cho min-max trong từng lần tìm: điểm của các lần tìm
khác nhau so sánh được với nhau, nên dùng chung được ngưỡng ở C6, C7. Hai ngưỡng được hiệu chỉnh
theo phân bố cosine thực tế của `bge-m3` trên bộ dữ liệu đánh giá (09).

Thiếu khi vector truy vấn hoặc `tr.embedding` rỗng.

### `f_need` — thế mạnh của gia sư có phủ nhu cầu của học sinh không

N = các tag TOPIC/GOAL có `role = NEED` hoặc `SELF` (GOAL) của tin học và hồ sơ học sinh, kèm
`confidence`. S = các tag TOPIC/GOAL có `role = STRENGTH` của tin dạy và hồ sơ gia sư.

```
match(n) = 1.0   nếu n ∈ S
         = 0.6   nếu cha của n ∈ S, hoặc n là cha của một phần tử thuộc S
         = 0     còn lại
f_need   = Σ conf(n)·match(n) / Σ conf(n)
```

Thiếu khi N rỗng. Nếu S rỗng (gia sư không mô tả thế mạnh) → 0,4 (trung lập thấp hơn trung bình,
khuyến khích gia sư mô tả rõ).

### `f_style` — phong cách dạy có hợp mong muốn không

P = STYLE `PREFERRED` của học sinh; O = STYLE `OFFERED` của gia sư.

```
f_style = |P ∩ O| / |P|      (O rỗng → 0,5)
```

Thiếu khi P rỗng.

### `f_segment` — trình độ học sinh có thuộc đối tượng gia sư nhắm tới không

s = SEGMENT `SELF` của học sinh; T = SEGMENT `TARGET` của gia sư. Thứ tự:
`mat_goc < trung_binh < kha < gioi`; `can_kem_cap` coi như gần `mat_goc`.

```
f_segment = 1.0 nếu s ∈ T;  0.5 nếu s kề một phần tử của T;  0.1 nếu không;  0.5 nếu T rỗng
```

Thiếu khi s không có.

### `f_price` — giá (góc nhìn học sinh)

p = `tr.expected_price`; hi = `max_price` của học sinh.

```
f_price = 1                                   nếu p ≤ hi
        = max(0, 1 − (p − hi) / (0.5·hi))     nếu p > hi   (về 0 khi đắt hơn 50%)
```

Thiếu khi p hoặc hi rỗng. Rẻ hơn `min_price` không bị trừ điểm.

### `f_sched` — lịch giao nhau

A, B là tập khung giờ (theo phút, từng ngày) của học sinh và của tin dạy, lấy từ bảng
availability (nguồn `USER` hoặc `LLM`).

```
overlap = Σ_ngày |A_ngày ∩ B_ngày|      (phút)
need    = min(tổng phút của A, 180)       (mặc định cần khoảng 2 buổi × 90 phút/tuần)
f_sched = min(1, overlap / need)
```

Thiếu khi A hoặc B rỗng.

### Vì sao không có đặc trưng khu vực

Học offline đã bị lọc cứng theo quận, học online không cần khu vực. Một đặc trưng "cùng quận"
khi đó luôn bằng 1, không phân biệt được ai. Khu vực chỉ quay lại khi **nới tiêu chí** ra cả
tỉnh (07).

### Gộp thành a

| Đặc trưng | Trọng số w |
|---|---|
| `f_sem` | 0,35 |
| `f_need` | 0,20 |
| `f_price` | 0,20 |
| `f_style` | 0,10 |
| `f_sched` | 0,10 |
| `f_segment` | 0,05 |
| **Tổng** | **1,00** |

## 6.5. Quy tắc khi thiếu dữ liệu

```
a       = Σ_{k có dữ liệu} w_k·f_k / Σ_{k có dữ liệu} w_k
c_a     = Σ_{k có dữ liệu} w_k          (độ đầy đủ thông tin)
```

Nếu `c_a < 0,4`, kết quả bị đánh dấu `lowConfidence` và giao diện gợi ý "Bổ sung hồ sơ/lịch rảnh
để gợi ý chính xác hơn". Cách này giúp hệ thống chạy được ngay cả khi A1 chưa xử lý xong.

## 6.6. Khả năng gia sư nhận học sinh — b (R1 + B4)

```
b = (0.5·b_hist + 0.5·b_fit) × b_cap
```

**Ngoại lệ:** nếu gia sư **đã chủ động gửi đề nghị** (`tutor_student_requests` `PENDING`) tới tin
học của chính học sinh này thì `b = 1` — gia sư hiển nhiên muốn nhận.

### `b_hist` — học từ lịch sử quyết định của gia sư

Ý tưởng theo RECON (Pizzato và cộng sự, 2010): sở thích của một người được suy ra từ những người
họ từng chấp nhận/từ chối. Với gia sư, đó là các đơn học sinh gửi tới **mọi tin dạy** của gia sư
đó, trạng thái `ACCEPTED` hoặc `REJECTED`, trong 180 ngày (tập D).

Độ giống giữa học sinh hiện tại x và người nộp đơn trước đó d:

```
grade_sim = 1 nếu cùng lớp;  0.5 nếu lệch 1 lớp (theo grades.level);  0 nếu xa hơn
price_sim = 1 − min(1, |price_x − price_d| / price_d)
            price_d = proposed_price của đơn cũ;  price_x = max_price của học sinh (hoặc tr.expected_price nếu thiếu)
mode_sim  = 1 nếu cùng hình thức học, 0 nếu khác
k(x, d)   = 0.5·grade_sim + 0.3·price_sim + 0.2·mode_sim
```

Tỉ lệ chấp nhận có trọng số, làm trơn Bayes về mức chung của nền tảng:

```
b_hist = (Σ_{d ∈ D, ACCEPTED} k(x,d) + α·p0) / (Σ_{d ∈ D} k(x,d) + α)
α  = 2
p0 = tỉ lệ chấp nhận chung của nền tảng 180 ngày gần nhất (dùng 0,6 nếu có < 20 quyết định)
```

Gia sư chưa có lịch sử → `b_hist = p0`. Càng có nhiều quyết định giống trường hợp hiện tại,
`b_hist` càng dựa vào lịch sử thật của gia sư đó.

### `b_fit` — học sinh có hợp tiêu chí công khai của gia sư không

Trung bình các thành phần có dữ liệu:
- **đối tượng:** trình độ học sinh có thuộc `TARGET` của gia sư (công thức như `f_segment`);
- **lịch:** `f_sched` (lịch giao nhau là điều kiện của cả hai bên);
- **giá theo góc nhìn gia sư:** `1` nếu `hi ≥ p`, nếu không `max(0, 1 − (p − hi)/(0.3·p))`.

Không thành phần nào có dữ liệu → `b_fit = p0`.

Giá và lịch xuất hiện ở cả a và b là **có chủ đích**: đây là điều kiện mà cả hai phía cùng quan
tâm, và mô hình reciprocal đánh giá từng phía độc lập.

### `b_cap` — chống dồn đơn (B4)

```
r     = quantity − số đơn ACCEPTED vào tr        (≥ 1 vì tr đang OPEN; quantity rỗng → 1)
n     = số đơn PENDING vào tr
b_cap = r / (r + λ·n),   λ = 0.5
```

| Còn chỗ r | Đang chờ n | b_cap |
|---|---|---|
| 1 | 0 | 1,00 |
| 1 | 2 | 0,50 |
| 1 | 6 | 0,25 |
| 3 | 2 | 0,75 |

Tin dạy đang bị nhiều người nộp so với số chỗ còn lại sẽ tụt hạng dần. Lượng đơn được trải sang
các gia sư khác, học sinh ít bị từ chối hơn — đây là phiên bản cục bộ, đơn giản của ý tưởng ghép
đôi có sức chứa (Hospital–Residents).

## 6.7. Điểm reciprocal (R1)

```
R = 2·a·b / (a + b)          (R = 0 nếu a + b = 0)
```

Trung bình điều hoà bị kéo về phía giá trị nhỏ: a = 0,9 và b = 0,2 cho R ≈ 0,33, trong khi trung
bình cộng là 0,55. Ứng viên phải hợp **cả hai phía** mới lên top.

## 6.8. Điểm cuối (R2)

```
score = R × (0.7 + 0.3·Q) × G
Q     = tutors.recommendation_score / 100        (rỗng → 0,5)
G     = g_sched × g_reject
g_sched  = 0.4  nếu cả hai có lịch và overlap = 0;   1 nếu ngược lại
g_reject = 0.5  nếu gia sư đã từ chối học sinh này (ở bất kỳ tin dạy nào) trong 90 ngày;  1 nếu không
```

- **Q là hệ số điều chỉnh**, chỉ dịch điểm trong khoảng ±15%. Gia sư điểm chất lượng cao nhưng
  không phù hợp vẫn không thể lên top.
- **G là chặn mềm.** Lịch hoàn toàn không giao nhau gần như là không học được, nhưng vẫn để
  0,4 thay vì 0 vì lịch có thể thương lượng.
- `recommendation_score` (đã có trong code, v1) đã gồm rating Bayes, hợp đồng hoàn thành, tỉ lệ
  chấp nhận, thời gian phản hồi, số học viên, xác minh, chứng chỉ, số lần bị khoá. Không cộng
  thêm điểm thưởng chứng chỉ ở đây để tránh tính hai lần.

## 6.9. Pha 4 — Rerank bằng cross-encoder (R3)

1. Sau pha 3, lấy **top-20** theo `score`.
2. Với mỗi cặp, tìm `rerank_score` trong `recommendation_pair_scores` (khớp `studying_request_id`,
   `teaching_request_id` và cả hai `content_hash`).
3. Các cặp chưa có → gom thành một lô, gọi `/rerank` với **timeout 800 ms**.
   - Đầu vào: (văn bản nhu cầu, văn bản cung cấp), mỗi bên cắt 256 token.
   - Model: `bge-reranker-v2-m3` dạng ONNX int8; đầu ra logit → sigmoid → [0, 1].
4. Tính lại `f_sem`, a, R, `score` cho 20 ứng viên này rồi sắp xếp lại **trong nhóm top-20**.
   Các ứng viên từ hạng 21 giữ nguyên thứ tự.
5. Quá thời gian chờ hoặc AI service lỗi → giữ thứ tự của pha 3 (log `rerankStatus = TIMEOUT`).
6. Nếu tìm theo một tin học cụ thể, điểm vừa tính được ghi vào `recommendation_pair_scores`.

Phần lớn cặp đã được **tính trước** bởi `PairScoringWorker` (07, mục 7.3) nên lúc tìm thường
không phải gọi rerank.

## 6.10. Gộp theo gia sư

Một gia sư có thể có nhiều tin dạy cùng khớp. Chỉ giữ tin có `score` cao nhất của mỗi gia sư.

## 6.11. Pha 5 — Slot khám phá cho gia sư ít được hiển thị (R6)

Trang đầu gồm N = 20 kết quả. **Vị trí 5 và 12** được dành cho "slot khám phá" nếu tập ứng viên
có hơn N + 5 phần tử.

1. Tập E: các ứng viên **ngoài top-N**, có `score ≥ 0,8 × score của hạng N` (không chọn ứng viên
   kém), và có `impressions_7d` nhỏ hơn trung vị của tập ứng viên.
2. Với mỗi e ∈ E, rút mẫu Thompson:
   ```
   θ_e ~ Beta(1 + clicks_total, 1 + impressions_total − clicks_total)
   ```
   chọn e có `θ_e × score_e` lớn nhất cho slot 5, lặp lại (không trùng) cho slot 12.
3. Chèn vào hai vị trí đó, đẩy các kết quả còn lại xuống. Ghi `isExploration = true` trong log.

Tác dụng:
- Tin mới (ít lượt hiển thị) có `Beta(1, 1)` — phân bố rộng, thường được rút mẫu cao, nên có cơ
  hội xuất hiện. Tin đã hiển thị nhiều mà ít click thì dần hết được chọn.
- Không đụng tới 4 vị trí đầu, nên không làm giảm chất lượng cảm nhận.
- Đánh dấu slot khám phá trong log giúp tách bạch khi phân tích CTR.

`recommendation_exposure_stats` được `ExposureStatsJob` tính lại hằng ngày từ
`recommendation_logs.returned_candidate_ids` (lượt hiển thị) và `recommendation_click_logs`.

## 6.12. Chiều ngược lại — gia sư tìm học viên (T→S)

Ngữ cảnh: một tin dạy `OPEN` của gia sư. Ứng viên: tin học `studying_requests` `OPEN`.

**Lọc cứng:** `sr.status = 'OPEN'`; học sinh `users.status = 'ACTIVE'`; `sr.subject_id =
tr.subject_id`; `sr.grade_id` thuộc các lớp của `tr`; `sr.learning_mode = tr.teaching_mode`; nếu
`OFFLINE` thì `sr.district_id` thuộc quận của `tr`; giá `tr.expected_price ≤ 1,5 × sr.max_price`;
không có hợp đồng `PENDING/ACTIVE` giữa hai người; gia sư chưa có đề nghị `PENDING/ACCEPTED` vào
`sr` và không bị từ chối ở `sr` trong 30 ngày.

**a′ — độ phù hợp theo góc nhìn gia sư:**

| Đặc trưng | Trọng số | Ghi chú |
|---|---|---|
| `f_sem` | 0,30 | Cùng cặp vector, dùng chung cache rerank |
| `f_price` (góc gia sư) | 0,25 | `1` nếu `max_price ≥ expected_price` |
| `f_need` | 0,20 | Gia sư cũng muốn học sinh mà mình giúp được |
| `f_segment` | 0,15 | Học sinh có thuộc đối tượng gia sư nhắm tới |
| `f_sched` | 0,10 | |

**b′ — khả năng học sinh đồng ý đề nghị của gia sư:**

```
b′ = (0.5·b′_hist + 0.5·b′_fit) × b′_cap
b′_fit  = trung bình có trọng số: 0.4·Q + 0.3·f_style + 0.3·f_price (góc học sinh)
b′_hist = lịch sử học sinh chấp nhận/từ chối đề nghị (tutor_student_requests),
          k = 0.5·price_sim + 0.5·(1 − |Q_x − Q_d|),  α = 2,  p0 = tỉ lệ chung
b′_cap  = r / (r + 0.5·n),  r = sr.quantity − đề nghị ACCEPTED vào sr,  n = đề nghị PENDING vào sr
```

Ngoại lệ: học sinh đã gửi đơn `PENDING` vào chính tin dạy này → `b′ = 1`.

**Điểm cuối:** `score′ = R′ × G′`, với `R′ = 2a′b′/(a′ + b′)`. Không có hệ số chất lượng vì hệ
thống không đánh giá học sinh (chất lượng của gia sư đã nằm trong b′ — học sinh quan tâm tới nó).
`G′ = g_sched × g_reject` (học sinh từng từ chối đề nghị của gia sư này trong 90 ngày → 0,5).

Rerank, gộp, slot khám phá dùng lại y nguyên.

## 6.13. Lý do khớp

Từ các thành phần điểm, chọn tối đa **3 lý do tích cực** và **1 lưu ý**, theo bảng mẫu:

| Điều kiện | Câu mẫu (S→T) |
|---|---|
| `f_need ≥ 0,7` | "Mạnh về {tên tag khớp}, đúng phần bạn cần cải thiện" |
| `f_sem ≥ 0,7` | "Nội dung tin đăng rất sát với nhu cầu của bạn" |
| `f_style ≥ 0,5` | "Phong cách {tên tag}, đúng như bạn mong muốn" |
| `f_price = 1` | "Học phí {p} — nằm trong ngân sách" |
| `f_sched ≥ 0,8` | "Trùng lịch rảnh: {các khung giờ giao nhau}" |
| `b_hist ≥ 0,75` và có ≥ 3 quyết định | "Thường nhận học sinh {lớp} như bạn" |
| `b_cap = 1` và r ≥ 1 | "Còn {r} chỗ, chưa có nhiều người đăng ký" |
| `Q ≥ 0,8` | "Đánh giá {average_rating}/5 từ {total_reviews} học sinh" |
| `isExploration` | "Gia sư mới trên nền tảng" |
| **Lưu ý:** `g_sched < 1` | "Lịch chưa trùng — có thể trao đổi thêm" |
| **Lưu ý:** `f_price < 1` | "Học phí cao hơn ngân sách {chênh lệch}" |

Thứ tự ưu tiên lý do: theo mức đóng góp `w_k·f_k`, rồi tới lý do từ b, rồi Q. Câu mẫu đảm bảo
nhất quán và không tốn chi phí. Có thể thay bằng câu do LLM viết (C8, tuỳ chọn) mà không đổi
thuật toán.

## 6.14. Ví dụ tính tay

Học sinh lớp 9, nhu cầu: hình học phẳng (0,95), chứng minh (0,9), thi vào 10 (0,95); muốn gia
sư kiên nhẫn, giải thích kỹ; trình độ trung bình; ngân sách ≤ 200k; rảnh tối T3, T5 19–21h.

**Gia sư X** (tin dạy 180k): mạnh hình học phẳng, thi vào 10; phong cách kiên nhẫn, luyện tập
nhiều; nhắm học sinh trung bình–khá; rảnh T3 18–20h, T5 19–21h, T7; cosine 0,68, rerank 0,85;
lịch sử: Σk(ACCEPTED) = 2,6, Σk(tất cả) = 3,4; tin còn 1 chỗ, 3 đơn đang chờ;
`recommendation_score` = 82.

| Bước | Tính | Kết quả |
|---|---|---|
| `f_sem` | s = (0,68 − 0,30)/0,5 = 0,76; 0,3·0,76 + 0,7·0,85 | 0,823 |
| `f_need` | (0,95·1 + 0,9·0,6 + 0,95·1) / 2,8 | 0,871 |
| `f_style` | {kiên nhẫn} / {kiên nhẫn, giải thích kỹ} | 0,5 |
| `f_segment` | trung bình ∈ {trung bình, khá} | 1 |
| `f_price` | 180k ≤ 200k | 1 |
| `f_sched` | giao 60 + 120 = 180 phút; need = min(240, 180) | 1 |
| **a** | 0,35·0,823 + 0,20·0,871 + 0,10·0,5 + 0,05·1 + 0,20·1 + 0,10·1 | **0,862** |
| `b_hist` | (2,6 + 2·0,6)/(3,4 + 2) | 0,704 |
| `b_fit` | đối tượng 1, lịch 1, giá 1 | 1 |
| `b_cap` | 1/(1 + 0,5·3) | 0,4 |
| **b** | (0,5·0,704 + 0,5·1) × 0,4 | **0,341** |
| **R** | 2·0,862·0,341/(0,862 + 0,341) | **0,489** |
| **score** | 0,489 × (0,7 + 0,3·0,82) × 1 | **0,462** |

**Gia sư Y** phù hợp kém hơn một chút (a = 0,75) nhưng tin không bị dồn và hay nhận học sinh như
vậy (b = 0,80), `recommendation_score` = 60:
R = 2·0,75·0,8/1,55 = 0,774; score = 0,774 × 0,88 = **0,681**.

→ Y đứng trên X. Với cách cộng tuyến tính cũ, X thắng nhờ a cao, nhưng thực tế học sinh nộp vào X
có khả năng bị từ chối cao vì chỉ còn 1 chỗ mà đã có 3 người chờ. Đây là đúng kiểu tình huống mà
mô hình reciprocal được thiết kế để xử lý.
