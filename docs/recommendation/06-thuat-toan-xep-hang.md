# 6. Thuật toán xếp hạng (R1, R2, R3, R6, B4, H1)

## 6.1. Tổng quan

Người tìm **không cần có tin đăng**. Họ nhập tiêu chí (bộ lọc, có thể kèm câu tự nhiên — xem
[05](./05-dau-vao-tim-kiem.md)); hệ thống ghép tiêu chí với hồ sơ của họ thành một **nhu cầu**
(`QueryContext`) rồi xếp hạng **tin đăng `OPEN` của phía bên kia**:

| Người tìm | Ứng viên |
|---|---|
| Học sinh (S→T) | Tin dạy `teaching_requests` đang `OPEN` (gia sư đã trả phí, đã được duyệt) |
| Gia sư (T→S) | Tin học `studying_requests` đang `OPEN` |

```
QueryContext (bộ lọc + câu tự nhiên + hồ sơ người tìm [+ tin đăng của chính họ, nếu chọn])
  │
  ├─ Pha 1  Lọc cứng (SQL)                         → tập ứng viên hợp lệ
  ├─ Pha 2  Lấy ứng viên theo vector (≤ 200)       → tập ứng viên
  ├─ Pha 3  Chấm điểm sơ bộ
  │           a  = độ phù hợp theo người tìm         (6.4, gồm lịch sử hành vi H1)
  │           b  = khả năng phía kia đồng ý          (6.6, gồm chống dồn B4)
  │           R  = 2ab / (a + b)                     (6.7, reciprocal R1)
  │           score = R × (0.7 + 0.3·Q) × G          (6.8, cấu trúc nhân R2)
  ├─ Pha 4  Rerank top-20 bằng cross-encoder (R3)   (6.9)
  ├─ Pha 5  Gộp theo người, chèn slot khám phá (R6) (6.10, 6.11)
  └─ Pha 6  Sinh lý do khớp, ghi log                (6.13)
```

Mục 6.2–6.11 mô tả chiều **học sinh tìm gia sư (S→T)**. Chiều ngược lại ở mục 6.12.

## 6.2. Pha 1 — Lọc cứng (S→T)

Các tiêu chí khu vực và hình thức học do **người tìm tự chọn** (05, mục 5.2):
- `learningMode` ∈ `ONLINE` / `OFFLINE` / `ANY` (cả hai);
- `areaScope` ∈ `DISTRICTS` (một hoặc nhiều quận/huyện) / `PROVINCE` (cả tỉnh/thành) / `ANY`
  (không giới hạn). Mặc định điền sẵn quận nơi ở, người dùng đổi được.

Điều kiện bắt buộc với tin dạy `tr`:

| Điều kiện | Lý do |
|---|---|
| `tr.status = 'OPEN'`, chưa xoá | Chỉ tin đã trả phí, đã duyệt, còn chỗ |
| `tutors.profile_status = 'VERIFIED'`, `users.status = 'ACTIVE'` | Quy tắc đã có trong README dự án |
| `tr.subject_id` = môn; lớp thuộc `grade_teaching_requests` của `tr` | |
| Hình thức: `ONLINE`/`OFFLINE` phải khớp; `ANY` nhận cả hai | |
| Nếu `tr` là `OFFLINE`: khu vực dạy của `tr` giao với phạm vi người dùng chọn (`DISTRICTS`: có quận chung; `PROVINCE`: có quận thuộc tỉnh đó; `ANY`: bỏ qua) | Người dùng **chủ động** quyết định phạm vi, không bị bó theo nơi ở |
| `tr.expected_price ≤ 1,5 × max_price` (nếu có ngân sách) | Dưới mức đó chấm mềm ở `f_price` |
| Không có hợp đồng `PENDING`/`ACTIVE` giữa hai người cho cùng môn | |
| Học sinh chưa có đơn `PENDING`/`ACCEPTED` vào `tr`, không bị từ chối ở `tr` trong 30 ngày | |

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
  AND (:learningMode = 'ANY' OR tr.teaching_mode = :learningMode)
  AND (tr.teaching_mode = 'ONLINE'
       OR :areaScope = 'ANY'
       OR EXISTS (SELECT 1 FROM teaching_request_districts d
                  JOIN districts dd ON dd.id = d.district_id
                  WHERE d.teaching_request_id = tr.id AND d.deleted_at IS NULL
                    AND ((:areaScope = 'DISTRICTS' AND d.district_id = ANY(CAST(:districtIds AS uuid[])))
                      OR (:areaScope = 'PROVINCE'  AND dd.province_id = CAST(:provinceId AS uuid)))))
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

Ghi chú triển khai: mốc thời gian từ chối dùng `COALESCE(s.responded_at, s.updated_at)` (có từ V25); câu
truy vấn trả thêm `1 − (tr.embedding <=> :queryVector)` để gộp pha 1 và pha 2 trong một lần đọc.

## 6.3. Pha 2 — Lấy ứng viên theo vector

- **Vector truy vấn** được sinh từ "văn bản nhu cầu" của `QueryContext` (05, mục 5.5) bằng một
  lời gọi `/embed` (~100 ms), cache theo mã băm văn bản. Không có văn bản nào → dùng
  `students.profile_embedding`.
- Ứng viên hợp lệ ≤ 200: lấy hết. > 200: lấy 180 gần nhất theo `tr.embedding <=> :queryVector`,
  cộng 20 tin **chưa có embedding** mới nhất.

Quét tuần tự, không index ANN — chính xác, đủ nhanh ở quy mô đồ án.

## 6.4. Độ phù hợp a (theo góc nhìn học sinh)

Mỗi đặc trưng nằm trong [0, 1] hoặc **thiếu**.

### `f_sem` — ngữ nghĩa

```
cos   = 1 − (queryVector <=> tr.embedding)
s     = clip((cos − 0.30) / (0.80 − 0.30), 0, 1)
f_sem = 0.3·s + 0.7·rerank     nếu có điểm rerank (6.9)
f_sem = s                      nếu chưa có
```

Ngưỡng cố định (0,30 / 0,80) thay cho min-max từng lần tìm: điểm giữa các lần tìm so được với
nhau, dùng chung ngưỡng cho C6, C7. Hai ngưỡng hiệu chỉnh trên bộ dữ liệu đánh giá (09).

### `f_need` — thế mạnh của gia sư có phủ nhu cầu của học sinh

N = tag TOPIC/GOAL nhu cầu của học sinh (từ bộ lọc, câu tự nhiên, tin học nếu chọn dùng, hồ sơ —
role `NEED`/`SELF`), kèm độ tin cậy. S = tag TOPIC/GOAL `STRENGTH` của tin dạy và hồ sơ gia sư.

```
match(n) = 1.0  nếu n ∈ S;   0.6 nếu cha của n ∈ S hoặc n là cha của một phần tử S;   0 nếu không
f_need   = Σ conf(n)·match(n) / Σ conf(n)
```

Thiếu khi N rỗng. S rỗng → 0,4.

### `f_style` — phong cách dạy

P = STYLE học sinh mong muốn (bộ lọc + hồ sơ); O = STYLE `OFFERED` của gia sư.
`f_style = |P ∩ O| / |P|` (O rỗng → 0,5). Thiếu khi P rỗng.

### `f_segment` — trình độ có thuộc đối tượng gia sư nhắm tới

Thứ tự `mat_goc < trung_binh < kha < gioi` (`can_kem_cap` ≈ `mat_goc`).
`1.0` nếu thuộc; `0.5` nếu kề; `0.1` nếu không; `0.5` nếu gia sư không ghi đối tượng. Thiếu khi
không biết trình độ học sinh.

### `f_price` — giá (góc nhìn học sinh)

```
f_price = 1                                   nếu p ≤ hi
        = max(0, 1 − (p − hi) / (0.5·hi))     nếu p > hi
```

p = `tr.expected_price`, hi = ngân sách tối đa. Thiếu khi một trong hai rỗng.

### `f_sched` — lịch giao nhau

A = lịch rảnh học sinh chọn trên lưới (hoặc từ tin học/câu tự nhiên); B = lịch của tin dạy.

```
overlap = Σ_ngày |A_ngày ∩ B_ngày| (phút);   need = min(tổng phút của A, 180)
f_sched = min(1, overlap / need)
```

Thiếu khi A hoặc B rỗng.

### `f_area` — mức thuận tiện về khu vực (chỉ áp dụng tin `OFFLINE`)

Lọc cứng đã đảm bảo tin nằm trong phạm vi người dùng chọn. `f_area` phân biệt **trong** phạm vi đó:

```
P = các quận người dùng chọn (nếu areaScope = DISTRICTS), hoặc {quận nơi ở} (nếu PROVINCE/ANY)
f_area = 1.0  nếu khu vực dạy của tr chứa một quận thuộc P
       = 0.6  nếu chỉ cùng tỉnh/thành
       = 0.2  nếu khác tỉnh/thành
f_area = 1.0  với tin ONLINE
```

Thiếu khi người dùng chọn `ANY` mà không có quận nơi ở. Không dùng toạ độ — chỉ so mã quận/tỉnh.

### `f_hist` — lịch sử hành vi (H1)

Từ **hồ sơ sở thích dài hạn** của người tìm (cách cập nhật ở 07, mục 7.4): vector sở thích `v_pref`
và mức ưa thích từng tag `aff(tag)` ∈ [0, 1].

```
f_hist = 0.6 · clip((cos(v_pref, tr.embedding) − 0.30) / 0.50, 0, 1)
       + 0.4 · Σ_{tag ∈ tag(tr)} aff(tag) / max(1, Σ_tag aff(tag))
```

Thiếu khi người dùng chưa có hồ sơ sở thích.

### Gộp thành a

| Đặc trưng | Trọng số cơ sở w |
|---|---|
| `f_sem` | 0,30 |
| `f_need` | 0,20 |
| `f_price` | 0,20 |
| `f_style` | 0,10 |
| `f_sched` | 0,10 |
| `f_area` | 0,05 |
| `f_segment` | 0,05 |
| **Tổng** | **1,00** |

**Trọng số của `f_hist` tăng dần theo lượng lịch sử**, phần còn lại co lại tương ứng:

```
w_hist = 0.15 × min(1, số tín hiệu đã tích luỹ / 10)
a      = (1 − w_hist) · a_cơ_sở + w_hist · f_hist
```

Người mới: `w_hist = 0`, không bị ảnh hưởng. Người có ≥ 10 tín hiệu: lịch sử đóng góp 15%.

Lý do chọn trọng số: ngữ nghĩa lớn nhất vì gom nhiều thông tin nhất; nhu cầu và giá là hai điều
kiện học sinh quan tâm trực tiếp; phong cách và lịch thường thiếu dữ liệu nên vừa phải; khu vực
và đối tượng đã được lọc/ước lượng thô nên nhỏ. Đây là **trọng số cho giai đoạn khởi đầu lạnh**,
được hiệu chỉnh trên dữ liệu mô phỏng (09); học trọng số từ dữ liệu thật là hướng phát triển (10).

## 6.5. Quy tắc khi thiếu dữ liệu

```
c_a     = Σ_{k có dữ liệu} w_k                                  (độ tin cậy: đã "nhìn thấy" bao nhiêu phần)
ā       = Σ_{k có dữ liệu} w_k·f_k / c_a                        (trung bình các đặc trưng có dữ liệu)
a_cơ_sở = c_a·ā + (1 − c_a)·0,5  =  Σ_{k có dữ liệu} w_k·f_k + (1 − c_a)·0,5
```

Đặc trưng thiếu được coi là **trung tính (0,5)** chứ không bị bỏ qua. Nếu chỉ chuẩn hoá lại theo các đặc
trưng có dữ liệu thì ứng viên mà hệ thống biết rất ít sẽ được điểm ảo: một tin online chưa có embedding
chỉ còn `f_area = 1` sẽ có a = 1,0, ngang tin khớp tốt nhất. Với công thức trên tin đó được
0,05·1 + 0,95·0,5 = 0,525; ứng viên có đủ 7 đặc trưng (`c_a = 1`) không đổi. Mức trung tính là tham số
`confidence-prior` (quyết định 12/10/2026, phát hiện khi lập trình Bước 7).

`c_a < 0,4` → đánh dấu `lowConfidence`, giao diện gợi ý "Chọn thêm tiêu chí / bổ sung hồ sơ để
gợi ý chính xác hơn".

## 6.6. Khả năng gia sư nhận học sinh — b (R1 + B4)

```
b = (0.5·b_hist + 0.5·b_fit) × b_cap
```

**Ngoại lệ:** học sinh có tin học `OPEN` và gia sư **đã chủ động gửi đề nghị** (`tutor_student_requests`
`PENDING`) vào tin đó → `b = 1`.

### `b_hist` — học từ lịch sử quyết định của gia sư (theo RECON)

D = các đơn học sinh gửi tới **mọi tin dạy** của gia sư, trạng thái `ACCEPTED`/`REJECTED`, 180 ngày.

```
grade_sim = 1 nếu cùng lớp; 0.5 nếu lệch 1 lớp (grades.level); 0 nếu xa hơn
price_sim = 1 − min(1, |price_x − price_d| / price_d)
            price_d = proposed_price của đơn cũ; price_x = ngân sách tối đa của học sinh (hoặc tr.expected_price nếu thiếu)
mode_sim  = 1 nếu cùng hình thức học (với ANY: tính là khớp), 0 nếu khác
k(x, d)   = 0.5·grade_sim + 0.3·price_sim + 0.2·mode_sim

b_hist = (Σ_{d ∈ D, ACCEPTED} k(x,d) + α·p0) / (Σ_{d ∈ D} k(x,d) + α)
α = 2;  p0 = tỉ lệ chấp nhận chung của nền tảng 180 ngày (0,6 nếu < 20 quyết định)
```

Gia sư chưa có lịch sử → `b_hist = p0`.

Ghi chú triển khai: mốc thời gian của quyết định là `COALESCE(responded_at, updated_at)`; thành phần nào
thiếu dữ liệu ở một trong hai phía (đơn cũ không ghi giá đề nghị, lớp không có `level`) thì độ giống của
thành phần đó là **0,5**; `p0` chỉ được đo khi nền tảng có ≥ 20 quyết định trong 180 ngày
(`willingness.p0-min-decisions`).

### `b_fit` — học sinh có hợp tiêu chí công khai của gia sư

Trung bình các thành phần có dữ liệu: đối tượng (như `f_segment`), lịch (`f_sched`), giá theo góc
nhìn gia sư (`1` nếu `hi ≥ p`, nếu không `max(0, 1 − (p − hi)/(0.3·p))`). Không có dữ liệu → `p0`.

### `b_cap` — chống dồn đơn (B4)

```
r     = quantity − số đơn ACCEPTED vào tr        (≥ 1 vì tr đang OPEN; quantity rỗng → 1)
n     = số đơn PENDING vào tr
b_cap = r / (r + 0.5·n)
```

| Còn chỗ r | Đang chờ n | b_cap |
|---|---|---|
| 1 | 0 | 1,00 |
| 1 | 2 | 0,50 |
| 1 | 6 | 0,25 |
| 3 | 2 | 0,75 |

Hai cờ phục vụ so sánh các cấu hình (09, mục 9.4): `reciprocal.enabled = false` → chỉ dùng a (V2-a);
`willingness.congestion-enabled = false` → `b_cap = 1` (V2-R); cả hai bật → V2-RC (mặc định).

## 6.7. Điểm reciprocal (R1)

```
R = 2·a·b / (a + b)          (R = 0 nếu a + b = 0)
```

Trung bình điều hoà bị kéo về giá trị nhỏ: a = 0,9, b = 0,2 → R ≈ 0,33. Phải hợp **cả hai phía**.

## 6.8. Điểm cuối (R2)

```
score    = R × (0.7 + 0.3·Q) × G
Q        = tutors.recommendation_score / 100     (rỗng → 0,5)
G        = g_sched × g_reject
g_sched  = 0.4 nếu cả hai có lịch và overlap = 0;  1 nếu ngược lại
g_reject = 0.5 nếu gia sư đã từ chối học sinh này (bất kỳ tin nào) trong 90 ngày;  1 nếu không
```

Q chỉ dịch điểm ±15%; `recommendation_score` đã gồm chứng chỉ nên không cộng thêm thưởng chứng chỉ.

## 6.9. Pha 4 — Rerank bằng cross-encoder (R3)

1. Sau pha 3, lấy **top-20**.
2. Tra `recommendation_rerank_cache` theo (mã băm văn bản nhu cầu, ứng viên, mã băm nội dung ứng
   viên, phiên bản model).
3. Cặp chưa có → gọi `/rerank` theo lô, **timeout 800 ms**. Đầu vào: (văn bản nhu cầu, văn bản
   cung cấp), mỗi bên ≤ 256 token. Model `bge-reranker-v2-m3` ONNX int8; logit → sigmoid.
4. Tính lại `f_sem`, a, R, `score`, sắp lại **trong nhóm top-20**; từ hạng 21 giữ nguyên.
5. Quá giờ/lỗi → giữ thứ tự pha 3 (log `rerankStatus = TIMEOUT`). Điểm mới được ghi vào cache.

Các tin học `OPEN` và các tìm kiếm đã lưu được rerank sẵn ở tiến trình nền (07, mục 7.3), nên khi
học sinh tìm lại với tiêu chí tương tự thường đã có cache.

## 6.10. Gộp theo người

Một gia sư có nhiều tin dạy cùng khớp → chỉ giữ tin có `score` cao nhất.

## 6.11. Pha 5 — Slot khám phá (R6)

Trang đầu N = 20. **Vị trí 5 và 12** dành cho slot khám phá nếu tập ứng viên > N + 5.

1. E = ứng viên ngoài top-N, `score ≥ 0,8 × score hạng N`, `impressions_7d` dưới trung vị.
2. Rút mẫu Thompson `θ_e ~ Beta(1 + clicks_total, 1 + impressions_total − clicks_total)`; chọn
   `θ_e × score_e` lớn nhất cho slot 5, lặp lại (không trùng) cho slot 12.
3. Chèn vào, đẩy phần còn lại xuống; ghi `isExploration = true`.

Tin mới (`Beta(1,1)`, phân bố rộng) có cơ hội xuất hiện; tin hiển thị nhiều mà ít click dần hết
được chọn. Không đụng 4 vị trí đầu. Slot khám phá cũng là nguồn dữ liệu ít thiên lệch cho việc
học trọng số sau này (10).

## 6.12. Chiều ngược lại — gia sư tìm học viên (T→S)

Gia sư **không cần có tin dạy**. Họ chọn tiêu chí: môn (bắt buộc), một hoặc nhiều lớp, hình thức,
phạm vi khu vực dạy được, mức giá mong muốn (tuỳ chọn — để trống thì lấy trung vị
`expected_price` các tin dạy trước đây của họ), lịch rảnh, đối tượng muốn dạy. Ứng viên là tin
học `OPEN`.

**Lọc cứng:** `sr.status = 'OPEN'`; học sinh `ACTIVE`; đúng môn; `sr.grade_id` thuộc các lớp đã
chọn; hình thức khớp (`ANY` nhận cả hai); nếu `sr` `OFFLINE` thì `sr.district_id` nằm trong phạm vi
gia sư chọn; giá mong muốn ≤ 1,5 × `sr.max_price` (nếu có); không có hợp đồng `PENDING/ACTIVE`;
gia sư chưa có đề nghị `PENDING/ACCEPTED` vào `sr`, không bị từ chối ở `sr` trong 30 ngày.

**Vector truy vấn:** "văn bản cung cấp" của gia sư (hồ sơ + tiêu chí vừa chọn), so với
`studying_requests.embedding`.

**a′ — độ phù hợp theo góc nhìn gia sư:**

| Đặc trưng | Trọng số | Ghi chú |
|---|---|---|
| `f_sem` | 0,30 | |
| `f_price` (góc gia sư) | 0,25 | `1` nếu `sr.max_price ≥` giá mong muốn |
| `f_need` | 0,20 | Nhu cầu của học sinh có thuộc thế mạnh của gia sư |
| `f_segment` | 0,10 | Học sinh có thuộc đối tượng gia sư muốn dạy |
| `f_sched` | 0,10 | |
| `f_area` | 0,05 | Quận của tin học so với phạm vi gia sư chọn |
| `f_hist` | 0 → 0,15 | Như chiều S→T, từ hồ sơ sở thích của gia sư |

**b′ — khả năng học sinh đồng ý:**

```
b′      = (0.5·b′_hist + 0.5·b′_fit) × b′_cap
b′_fit  = 0.4·Q + 0.3·f_style + 0.3·f_price (góc học sinh: giá mong muốn so với sr.max_price)
b′_hist = lịch sử học sinh nhận/từ chối đề nghị (tutor_student_requests),
          k = 0.5·price_sim + 0.5·(1 − |Q_x − Q_d|),  α = 2,  p0 = tỉ lệ chung
b′_cap  = r / (r + 0.5·n);  r = sr.quantity − đề nghị ACCEPTED vào sr;  n = đề nghị PENDING vào sr
```

Ngoại lệ: học sinh đã gửi đơn `PENDING` vào một tin dạy `OPEN` của chính gia sư này → `b′ = 1`.

**Điểm cuối:** `score′ = R′ × G′` (không có hệ số chất lượng cho học sinh). Rerank, gộp (theo học
sinh), slot khám phá dùng lại y nguyên.

Ghi chú triển khai (12/10/2026):
- `G′ = g_sched × g_reject`: 0,4 khi hai bên có lịch mà không giao nhau; 0,5 khi học sinh đã từ chối đề
  nghị của gia sư này (bất kỳ tin nào) trong 90 ngày.
- a′ áp dụng cùng quy tắc thiếu dữ liệu ở 6.5 (đặc trưng thiếu = 0,5).
- `f_price` góc gia sư: `1` nếu `sr.max_price ≥ p`, nếu không `max(0, 1 − (p − hi)/(0,3·p))`; góc học sinh
  (trong `b′_fit`): `1` nếu `p ≤ hi`, nếu không `max(0, 1 − (p − hi)/(0,5·hi))`.
- `b′_fit`: thành phần thiếu (`f_style`, `f_price`) lấy 0,5; Q là `recommendation_score/100` của gia sư đang
  tìm (rỗng → 0,5).
- `b′_hist`: `Q_d` là điểm chất lượng **hiện tại** của gia sư đã gửi đề nghị cũ; giá hoặc Q thiếu → độ
  giống 0,5; `p0` đo trên `tutor_student_requests` (0,6 khi < 20 quyết định).
- **Chỉ gia sư có hồ sơ `VERIFIED` mới tìm được học viên** (`403 TUTOR_PROFILE_NOT_VERIFIED`, kể cả khi lật
  trang bằng `logId`), cho nhất quán với chiều S→T chỉ hiện gia sư đã duyệt (quyết định 12/10/2026).
- Giá mong muốn để trống → trung vị `expected_price` của mọi tin dạy của gia sư; không có tin → không lọc
  và không chấm theo giá.

## 6.13. Lý do khớp

Tối đa **3 lý do tích cực** và **1 lưu ý**, theo bảng mẫu (S→T):

| Điều kiện | Câu mẫu |
|---|---|
| `f_need ≥ 0,7` | "Mạnh về {tag khớp}, đúng phần bạn cần cải thiện" |
| `f_sem ≥ 0,7` | "Nội dung tin đăng rất sát với nhu cầu của bạn" |
| `f_style ≥ 0,5` | "Phong cách {tag}, đúng như bạn mong muốn" |
| `f_price = 1` | "Học phí {p} — nằm trong ngân sách" |
| `f_sched ≥ 0,8` | "Trùng lịch rảnh: {khung giờ}" |
| `f_area = 1` và `OFFLINE` | "Dạy tại {quận}" |
| `f_hist ≥ 0,6` và `w_hist > 0` | "Giống các gia sư bạn đã quan tâm: {tag chung}" |
| `b_hist ≥ 0,75` và ≥ 3 quyết định | "Thường nhận học sinh {lớp} như bạn" |
| `b_cap = 1` | "Còn {r} chỗ, chưa có nhiều người đăng ký" |
| `Q ≥ 0,8` | "Đánh giá {average_rating}/5 từ {total_reviews} học sinh" |
| `isExploration` | "Gia sư mới trên nền tảng" |
| **Lưu ý:** `g_sched < 1` | "Lịch chưa trùng — có thể trao đổi thêm" |
| **Lưu ý:** `f_price < 1` | "Học phí cao hơn ngân sách {chênh lệch}" |
| **Lưu ý:** `f_area ≤ 0,6` | "Ngoài khu vực bạn chọn ưu tiên ({quận})" |

Thứ tự ưu tiên: đóng góp `w_k·f_k`, rồi lý do từ b, rồi Q.

## 6.14. Ví dụ tính tay

Minh, lớp 9, chọn trên bộ lọc: Toán, lớp 9, offline, quận Cầu Giấy, ngân sách ≤ 200k, rảnh tối T3,
T5 19–21h, phong cách "kiên nhẫn", "giải thích kỹ"; hồ sơ: yếu hình học phẳng (0,95), bài chứng
minh (0,9), mục tiêu thi vào 10 (0,95), trình độ trung bình. Minh chưa có tin học nào.

**Gia sư X** (tin dạy 180k, dạy tại Cầu Giấy): mạnh hình học phẳng, thi vào 10; phong cách kiên
nhẫn, luyện tập nhiều; nhắm học sinh trung bình–khá; rảnh T3 18–20h, T5 19–21h, T7; cosine 0,68,
rerank 0,85; lịch sử Σk(ACCEPTED) = 2,6, Σk = 3,4; còn 1 chỗ, 3 đơn chờ; `recommendation_score` 82.

| Bước | Tính | Kết quả |
|---|---|---|
| `f_sem` | 0,3·0,76 + 0,7·0,85 | 0,823 |
| `f_need` | (0,95·1 + 0,9·0,6 + 0,95·1) / 2,8 | 0,871 |
| `f_style`, `f_segment`, `f_price`, `f_sched`, `f_area` | | 0,5 · 1 · 1 · 1 · 1 |
| **a** (Minh chưa có lịch sử, `w_hist = 0`) | 0,30·0,823 + 0,20·0,871 + 0,20 + 0,10·0,5 + 0,10 + 0,05 + 0,05 | **0,871** |
| `b_hist` | (2,6 + 2·0,6)/(3,4 + 2) | 0,704 |
| `b_fit` · `b_cap` | 1 · 1/(1 + 0,5·3) | 1 · 0,4 |
| **b** | (0,5·0,704 + 0,5·1) × 0,4 | **0,341** |
| **R** | 2·0,871·0,341/(0,871 + 0,341) | **0,490** |
| **score** | 0,490 × (0,7 + 0,3·0,82) | **0,463** |

**Gia sư Y**: a = 0,75, b = 0,80 (tin không bị dồn, hay nhận học sinh như Minh),
`recommendation_score` 60 → R = 0,774; score = 0,774 × 0,88 = **0,681** → Y đứng trên X.

**Sau vài tuần**, Minh đã có 12 tín hiệu (`w_hist = 0,15`) và chủ yếu quan tâm gia sư kiểu luyện
đề; với X, `f_hist = 0,4` → a = 0,85·0,871 + 0,15·0,4 = 0,801; R = 0,478; score = 0,452. Một gia sư
luyện đề có `f_hist` cao sẽ được đẩy lên tương ứng — hệ thống "học" dần sở thích thật của Minh.
