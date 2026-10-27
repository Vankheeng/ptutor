# 6. Thuật toán xếp hạng (R1, R2, R3, R6, B4, H1)

> 💡 **File này nói về gì:** Trái tim của tính năng: từ tiêu chí của người tìm, hệ thống lọc, chấm điểm và xếp thứ tự các tin đăng như thế nào. Có đủ công thức, mỗi công thức có khung giải thích ý nghĩa và ví dụ số. Nếu chỉ muốn hiểu trực giác (không công thức), đọc [00](./00-doc-truoc.md) mục 3–4 trước. Cuối file (6.14) có một ví dụ tính tay trọn vẹn.

## 6.1. Tổng quan

> 💡 Quy trình sáu pha. Pha 1 **lọc cứng** (loại hẳn tin không đạt điều kiện bắt buộc). Pha 2 lấy tối đa 200 tin gần nghĩa nhất. Pha 3 chấm điểm: **a** = người tìm có thích tin này không, **b** = phía kia có khả năng nhận không, **R** gộp a và b sao cho phải hợp cả hai, rồi nhân với **Q** (uy tín gia sư) và **G** (hệ số phạt nhẹ). Pha 4 chấm kỹ 10 tin đầu (**rerank**, mặc định tắt từ 26/10/2026). Pha 5 gộp mỗi người một tin và chèn tin cần được khám phá. Pha 6 viết lý do và ghi nhật ký. S→T = học sinh tìm gia sư; T→S = gia sư tìm học viên.

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
  │           R  = a^0,65 · b^0,35                   (6.7, reciprocal R1)
  │           score = R × (0.7 + 0.3·Q) × G          (6.8, cấu trúc nhân R2)
  │           (trọng số gộp của a, b lấy từ WeightProvider — prior hoặc bản học được, L1 — 12)
  ├─ Pha 4  Rerank top-10 (R3; mặc định OFF, bật thì chấm nền) (6.9)
  ├─ Pha 5  Gộp theo người, chèn slot khám phá (R6) (6.10, 6.11)
  └─ Pha 6  Sinh lý do khớp, ghi log                (6.13)
```

Mục 6.2–6.11 mô tả chiều **học sinh tìm gia sư (S→T)**. Chiều ngược lại ở mục 6.12.

## 6.2. Pha 1 — Lọc cứng (S→T)

> 💡 Loại ngay những tin chắc chắn không hợp, bằng một câu SQL: tin phải đang mở (`OPEN`), gia sư đã được duyệt (`VERIFIED`) và không bị khóa, đúng môn, có dạy lớp đó, đúng hình thức, nằm trong khu vực người tìm chọn (nếu học trực tiếp), học phí không vượt **1,5 lần** ngân sách, hai bên chưa có hợp đồng đang chạy, học sinh chưa gửi đơn vào tin đó và chưa bị từ chối ở đó trong 30 ngày. Ví dụ: ngân sách 200.000 đ thì tin 290.000 đ vẫn qua (chấm điểm giá thấp ở bước sau), tin 310.000 đ bị loại.

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

Ghi chú triển khai: mốc thời gian từ chối dùng `COALESCE(s.responded_at, s.updated_at)` (có từ V31); câu
truy vấn trả thêm `1 − (tr.embedding <=> :queryVector)` để gộp pha 1 và pha 2 trong một lần đọc.

## 6.3. Pha 2 — Lấy ứng viên theo vector

> 💡 Nhu cầu của người tìm được biến thành một **vector** (dãy số đại diện ý nghĩa). Nếu có hơn 200 tin qua lọc cứng, lấy 180 tin có vector gần nhất (`<=>` là phép đo "khoảng cách" giữa hai vector trong pgvector) cộng 20 tin mới chưa có vector — để tin mới không bị bỏ sót. Quy mô nhỏ nên so tuần tự với mọi tin, không cần chỉ mục tìm nhanh (ANN).

- **Vector truy vấn** được sinh từ "văn bản nhu cầu" của `QueryContext` (05, mục 5.5) bằng một
  lời gọi `/embed` (~100 ms), cache theo mã băm văn bản. Không có văn bản nào → dùng
  `students.profile_embedding`.
- Ứng viên hợp lệ ≤ 200: lấy hết. > 200: lấy 180 gần nhất theo `tr.embedding <=> :queryVector`,
  cộng 20 tin **chưa có embedding** mới nhất.

Quét tuần tự, không index ANN — chính xác, đủ nhanh ở quy mô đồ án.

## 6.4. Độ phù hợp a (theo góc nhìn học sinh)

> 💡 a trả lời câu hỏi "học sinh có thích tin này không", là trung bình có trọng số của 7 **đặc trưng** — mỗi đặc trưng (ký hiệu `f_…`) là điểm của một tiêu chí nhỏ, từ 0 (không hợp) tới 1 (rất hợp), hoặc **thiếu** khi không có dữ liệu. Các mục con dưới đây giải thích từng đặc trưng.

Mỗi đặc trưng nằm trong [0, 1] hoặc **thiếu**.

### `f_sem` — ngữ nghĩa

> 💡 Đo nội dung tin "nói cùng chuyện" với nhu cầu tới đâu. `cos` (cosine) là độ giống của hai vector, gần 1 là rất giống nghĩa. Vì cosine thực tế hay rơi vào khoảng 0,3–0,8, công thức kéo giãn khoảng này thành 0–1 (`clip` = cắt về trong đoạn [0, 1]). Ví dụ cos = 0,68 → s = (0,68 − 0,30) / 0,50 = 0,76. Nếu tin đã được chấm kỹ bằng rerank (6.9) thì điểm rerank chiếm 70%: rerank 0,85 → f_sem = 0,3·0,76 + 0,7·0,85 = 0,823.

```
cos   = 1 − (queryVector <=> tr.embedding)
s     = clip((cos − 0.30) / (0.80 − 0.30), 0, 1)
f_sem = 0.3·s + 0.7·rerank     nếu có điểm rerank (6.9)
f_sem = s                      nếu chưa có
```

Ngưỡng cố định (0,30 / 0,80) thay cho min-max từng lần tìm: điểm giữa các lần tìm so được với
nhau, dùng chung ngưỡng cho C6, C7. Hai ngưỡng hiệu chỉnh trên bộ dữ liệu đánh giá (09).

### `f_need` — thế mạnh của gia sư có phủ nhu cầu của học sinh

> 💡 N là các chủ đề / mục tiêu học sinh cần (mỗi cái kèm mức chắc chắn `conf`), S là các thế mạnh của gia sư. Mỗi nhu cầu được 1 điểm nếu gia sư mạnh đúng chủ đề đó, 0,6 nếu gia sư mạnh chủ đề "cha" hoặc "con" của nó, 0 nếu không; rồi lấy trung bình có trọng số theo `conf`. Ví dụ học sinh cần hình học phẳng (0,95), bài chứng minh (0,9), thi vào 10 (0,95); gia sư mạnh hình học phẳng và thi vào 10 → (0,95·1 + 0,9·0,6 + 0,95·1) / 2,8 = 0,871.

N = tag TOPIC/GOAL nhu cầu của học sinh (từ bộ lọc, câu tự nhiên, tin học nếu chọn dùng, hồ sơ —
role `NEED`/`SELF`), kèm độ tin cậy. S = tag TOPIC/GOAL `STRENGTH` của tin dạy và hồ sơ gia sư.

```
match(n) = 1.0  nếu n ∈ S;   0.6 nếu cha của n ∈ S hoặc n là cha của một phần tử S;   0 nếu không
f_need   = Σ conf(n)·match(n) / Σ conf(n)
```

Thiếu khi N rỗng. S rỗng → 0,4.

### `f_style` — phong cách dạy

> 💡 Tỉ lệ phong cách học sinh mong muốn mà gia sư có. Ví dụ muốn "kiên nhẫn" và "giải thích kỹ", gia sư chỉ ghi "kiên nhẫn" → 1/2 = 0,5. Gia sư không ghi phong cách nào → 0,5 (trung tính).

P = STYLE học sinh mong muốn (bộ lọc + hồ sơ); O = STYLE `OFFERED` của gia sư.
`f_style = |P ∩ O| / |P|` (O rỗng → 0,5). Thiếu khi P rỗng.

### `f_segment` — trình độ có thuộc đối tượng gia sư nhắm tới

> 💡 Trình độ xếp theo thứ tự mất gốc < trung bình < khá < giỏi. Học sinh đúng đối tượng gia sư nhắm tới → 1; lệch một bậc → 0,5; lệch xa hơn → 0,1.

Thứ tự `mat_goc < trung_binh < kha < gioi` (`can_kem_cap` ≈ `mat_goc`).
`1.0` nếu thuộc; `0.5` nếu kề; `0.1` nếu không; `0.5` nếu gia sư không ghi đối tượng. Thiếu khi
không biết trình độ học sinh.

### `f_price` — giá (góc nhìn học sinh)

> 💡 p là học phí của tin, hi là ngân sách tối đa. Trong ngân sách → 1; vượt thì giảm đều, vượt 50% thì về 0. Ví dụ ngân sách 200.000 đ, tin 220.000 đ → 1 − 20.000 / 100.000 = 0,8.

```
f_price = 1                                   nếu p ≤ hi
        = max(0, 1 − (p − hi) / (0.5·hi))     nếu p > hi
```

p = `tr.expected_price`, hi = ngân sách tối đa. Thiếu khi một trong hai rỗng.

### `f_sched` — lịch giao nhau

> 💡 Đếm số phút hai lịch trùng nhau (overlap), chia cho số phút "cần" — tối đa 180 phút, tức chỉ cần trùng khoảng 3 giờ mỗi tuần là đủ điểm. Ví dụ học sinh rảnh tối thứ Ba và thứ Năm 19–21h (240 phút, nên need = 180), gia sư rảnh thứ Ba 18–20h và thứ Năm 19–21h → trùng 60 + 120 = 180 phút → f_sched = 1.

A = lịch rảnh học sinh chọn trên lưới (hoặc từ tin học/câu tự nhiên); B = lịch của tin dạy.

```
overlap = Σ_ngày |A_ngày ∩ B_ngày| (phút);   need = min(tổng phút của A, 180)
f_sched = min(1, overlap / need)
```

Thiếu khi A hoặc B rỗng.

### `f_area` — mức thuận tiện về khu vực (chỉ áp dụng tin `OFFLINE`)

> 💡 Lọc cứng đã giữ tin trong phạm vi người dùng chọn; đặc trưng này phân biệt gần hay xa **bên trong** phạm vi đó: dạy ở quận mình chọn (hoặc nơi ở) → 1; chỉ cùng tỉnh → 0,6; khác tỉnh → 0,2. Tin online luôn được 1.

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

> 💡 Tin này có giống những tin người dùng **từng quan tâm** không. `v_pref` là vector sở thích (tổng hợp từ các tin họ đã xem / tym / gửi đơn), `aff(tag)` là mức ưa thích từng tag (0–1). 60% điểm đến từ độ giống với `v_pref`, 40% từ các tag của tin mà người dùng ưa. Người chưa có lịch sử thì đặc trưng này thiếu.

Từ **hồ sơ sở thích dài hạn** của người tìm (cách cập nhật ở 07, mục 7.4): vector sở thích `v_pref`
và mức ưa thích từng tag `aff(tag)` ∈ [0, 1].

```
f_hist = 0.6 · clip((cos(v_pref, tr.embedding) − 0.30) / 0.50, 0, 1)
       + 0.4 · Σ_{tag ∈ tag(tr)} aff(tag) / max(1, Σ_tag aff(tag))
```

Thiếu khi người dùng chưa có hồ sơ sở thích.

Ghi chú triển khai (13/10/2026): tin chưa có vector thì phần cosine lấy trung tính 0,5; `aff` tính theo **mã
tag** của tin và của người đăng; phần tag bị chặn trên ở 1. `a = (1 − w_hist)·a_cơ_sở + w_hist·f_hist` áp dụng
cho cả hai chiều; `w_hist` và `f_hist` được ghi theo từng ứng viên trong log (`wHist`, `features.hist`).
Cờ tắt: `history.enabled=false`.

### Gộp thành a

> 💡 Cộng 7 đặc trưng theo trọng số (nội dung 30%, nhu cầu 20%, giá 20%, phong cách 10%, lịch 10%, khu vực 5%, trình độ 5%). Sau đó trộn thêm lịch sử hành vi với trọng số `w_hist` tăng dần: 0 với người mới, tối đa 0,15 khi có từ 10 tín hiệu. Ví dụ có 4 tín hiệu → w_hist = 0,15 × 4/10 = 0,06, tức lịch sử chiếm 6%. Các trọng số này do nhóm đặt (**prior** — giá trị xuất phát), sau này có thể được tự học (12).

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
và đối tượng đã được lọc/ước lượng thô nên nhỏ. Đây là **trọng số cho giai đoạn khởi đầu lạnh**.

**Từ L1 (13/10/2026):** bảng trên là **prior**. Trọng số thực sự dùng do `WeightProvider` cung cấp: bản học
được (mô hình `A_S2T`) khi L1 ở chế độ `ACTIVE`, nếu không thì đúng các giá trị trên. Bản học được bị kẹp
trong `[0,5×; 2×]` giá trị prior và chuẩn hoá tổng bằng 1. `w_hist` vẫn theo quy tắc tăng dần ở dưới. Chi tiết:
[12](./12-hoc-trong-so.md).

## 6.5. Quy tắc khi thiếu dữ liệu

> 💡 Đặc trưng nào thiếu dữ liệu thì được tính là **0,5 — trung tính**, không bỏ qua. Lý do: nếu bỏ qua và chỉ tính trên phần có dữ liệu, một tin hệ thống biết rất ít lại được điểm ảo rất cao. `c_a` = tổng trọng số của các đặc trưng có dữ liệu (mức "đã nhìn thấy" bao nhiêu phần). Ví dụ chỉ biết `f_area = 1` (trọng số 0,05): a = 0,05·1 + 0,95·0,5 = 0,525, chứ không phải 1,0. `c_a` dưới 0,4 thì gắn nhãn "độ tin cậy thấp".

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

> 📌 **Đã làm 26/10/2026:** `b_cap` chỉ phạt khi số đơn chờ n ≥ số chỗ còn lại r (công thức `ON_SHORTAGE`, λ = 0,5),
> vì công thức cũ phạt cả khi chưa dồn và làm số lần bị từ chối tăng 0,22 → 0,27 trên kịch bản base. Trên kịch bản thị
> trường đông, hai công thức chặn dồn đơn tốt như nhau; công thức mới ghép được người hợp hơn, gia sư mới được thấy nhiều
> hơn, đổi lại Gini cao hơn ~0,04. **Còn phải đo lại trên base ở Bước 40**; nếu không cải thiện thì quay về công thức cũ.
> Bằng chứng: [13, mục 13.4](./13-tinh-chinh-sau-danh-gia.md).

> 💡 b ước lượng gia sư có nhận học sinh này không: một nửa từ **lịch sử nhận / từ chối** của gia sư (`b_hist`), một nửa từ mức học sinh **hợp tiêu chí công khai** của gia sư (`b_fit`), rồi nhân với hệ số **chống dồn đơn** (`b_cap`). Gia sư đã chủ động mời học sinh này thì b = 1. Ba mục con giải thích từng phần.

```
b = (0.5·b_hist + 0.5·b_fit) × b_cap
```

`b_hist` là **ước lượng tỉ lệ nhận có làm trơn Bayes**: giá trị tự cập nhật khi gia sư nhận / từ chối thêm
đơn, nhưng các trọng số (0,5 / 0,5 ở trên, kernel 0,5 / 0,3 / 0,2, α, p0) do nhóm đặt. Từ L1, tỉ lệ
`0,5·b_hist + 0,5·b_fit` được viết dạng dàn phẳng `0,5·b_hist + 1/6·fit_segment + 1/6·fit_sched +
1/6·fit_price` và 4 trọng số này được học từ quyết định nhận / từ chối (mô hình `B_S2T`, 12, mục 12.6);
kernel và `b_cap` giữ cố định (12, mục 12.2).

**Ngoại lệ:** học sinh có tin học `OPEN` và gia sư **đã chủ động gửi đề nghị** (`tutor_student_requests`
`PENDING`) vào tin đó → `b = 1`.

### `b_hist` — ước lượng từ lịch sử quyết định của gia sư (theo RECON)

> 💡 Xem các đơn cũ gia sư đã nhận / từ chối trong 180 ngày; đơn nào **càng giống** học sinh hiện tại (cùng lớp, giá gần, cùng hình thức — đo bằng `k`, từ 0 đến 1) thì càng được tính nặng. Phần `α·p0` là **làm trơn Bayes**: cộng thêm 2 "đơn ảo" theo tỉ lệ nhận chung của nền tảng `p0` (mặc định 0,6), để gia sư ít dữ liệu không bị kết luận vội. Ví dụ tổng độ giống của các đơn đã nhận là 2,6, của tất cả các đơn là 3,4 → b_hist = (2,6 + 2·0,6) / (3,4 + 2) = 0,704.

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

> 💡 Trung bình ba câu hỏi: học sinh có đúng đối tượng gia sư ghi không, lịch có trùng không, ngân sách có đủ mức giá của gia sư không. Ví dụ đúng đối tượng (1), lịch trùng đủ (1), ngân sách đủ (1) → b_fit = 1.

Trung bình các thành phần có dữ liệu: đối tượng (như `f_segment`), lịch (`f_sched`), giá theo góc
nhìn gia sư (`1` nếu `hi ≥ p`, nếu không `max(0, 1 − (p − hi)/(0.3·p))`). Không có dữ liệu → `p0`.

### `b_cap` — chống dồn đơn (B4)

> 💡 r = số chỗ còn trống của tin, n = số đơn đang chờ. Khi số người chờ còn ít hơn số chỗ, ai nộp cũng còn chỗ nên
> không phạt. Chỉ khi người chờ đã đủ hoặc vượt số chỗ thì b mới giảm, để không đẩy thêm học sinh vào chỗ gần như chắc
> chắn bị từ chối. Ví dụ còn 1 chỗ, 3 người chờ → 1 / (1 + 0,5·3) = 0,4; còn 3 chỗ, 2 người chờ → 1 (không phạt).

```
r     = quantity − số đơn ACCEPTED vào tr        (≥ 1 vì tr đang OPEN; quantity rỗng → 1)
n     = số đơn PENDING vào tr
b_cap = 1                              khi n < r     (willingness.congestion-formula = ON_SHORTAGE, mặc định)
b_cap = r / (r + λ·(n − r + 1))        khi n ≥ r     (λ = willingness.congestion-lambda = 0,5)
```

| Còn chỗ r | Đang chờ n | b_cap (mới) | b_cap cũ `r / (r + 0,5·n)` |
|---|---|---|---|
| 1 | 0 | 1,00 | 1,00 |
| 1 | 2 | 0,50 | 0,50 |
| 1 | 6 | 0,25 | 0,25 |
| 3 | 1 | **1,00** | 0,86 |
| 3 | 2 | **1,00** | 0,75 |
| 3 | 3 | 0,86 | 0,67 |

Công thức cũ giữ để so sánh: `willingness.congestion-formula = EVERY_APPLICATION`.

Hai cờ phục vụ so sánh các cấu hình (09, mục 9.4): `reciprocal.enabled = false` → chỉ dùng a (V2-a);
`willingness.congestion-enabled = false` → `b_cap = 1` (V2-R); cả hai bật → V2-RC (mặc định).

## 6.7. Điểm reciprocal (R1)

> 📌 **Đã làm 26/10/2026:** trung bình nhân có trọng số `R = a^α · b^(1−α)`, **α = 0,65** chốt trên kịch bản thị trường
> đông (quét 0,5 / 0,65 / 0,8): danh sách hợp hơn trung bình điều hoà cũ (NDCG +0,026, p < 0,001) mà số lần bị từ chối
> không tăng có ý nghĩa; α 0,8 làm từ chối tăng, α 0,5 không cải thiện thứ tự. Bằng chứng: [13, mục 13.5](./13-tinh-chinh-sau-danh-gia.md).

> 💡 **Reciprocal** = hai chiều: a = "người tìm có thích ứng viên này không", b = "ứng viên có khả năng đồng ý không".
> R gộp hai phía bằng **trung bình nhân có trọng số**: b = 0 thì R = 0 (không đề xuất người chắc chắn từ chối), và α
> cho biết tin a bao nhiêu so với b. b là ước lượng thô (nhiều gia sư ít lịch sử), nên a nặng hơn: α = 0,65. Ví dụ
> a = 0,9, b = 0,2 → R = 0,9^0,65 · 0,2^0,35 ≈ 0,52 (trung bình điều hoà cũ cho 0,33). Ý tưởng hai chiều theo mô hình
> RECON (11 mục 11.2).

```
R = a^α · b^(1−α)            (R = 0 nếu a = 0 hoặc b = 0; α = reciprocal.alpha = 0,65; reciprocal.mode = GEOMETRIC)
```

Trung bình điều hoà cũ `R = 2·a·b / (a + b)` giữ để so sánh: `reciprocal.mode = HARMONIC`. Reciprocal không tự biết
gia sư đã hết chỗ (b học từ lịch sử nhận đơn, nên còn đẩy học sinh vào gia sư nổi tiếng); phần đó là việc của `b_cap`,
vì vậy hai phần luôn bật cùng nhau (13 mục 13.5).

## 6.8. Điểm cuối (R2)

> 📌 **Đã làm 27/10/2026:** đã chạy ba cấu hình tách (tắt hệ số Q, tắt cổng lịch, tắt cổng từ chối) trên `base` và thị
> trường đông; **giữ nguyên công thức dưới**. Lợi ích đo được của Q đến từ cách dựng nhóm gia sư nổi tiếng trong mô
> phỏng (bỏ nhóm đó thì tắt Q nhỉnh hơn); giữ Q 0,3 vì nó mang tín hiệu tin cậy (khiếu nại có lỗi, khoá tài khoản, xác
> minh) mà mô phỏng không đo được — kiểm lại khi có đánh giá và khiếu nại thật. Cổng lịch không có tác dụng đo được;
> cổng từ chối giữ vì trải nghiệm người dùng. Bằng chứng: [13, mục 13.8](./13-tinh-chinh-sau-danh-gia.md).

> 💡 Điểm cuối = R × hệ số uy tín × hệ số phạt. Hệ số uy tín `(0,7 + 0,3·Q)` nằm trong khoảng 0,7–1,0, tức Q (điểm uy tín gia sư, 0–1) chỉ dịch điểm khoảng ±15%. G gồm hai "phanh": lịch hai bên có mà không trùng chút nào → × 0,4; gia sư đã từ chối học sinh này trong 90 ngày → × 0,5. Dùng **phép nhân** để một yếu tố rất tệ kéo cả điểm xuống, không bị yếu tố khác "bù" lại. Ví dụ R = 0,490, Q = 0,82, G = 1 → 0,490 × 0,946 = 0,463.

```
score    = R × (0.7 + 0.3·Q) × G
Q        = tutors.recommendation_score / 100     (rỗng → 0,5)
G        = g_sched × g_reject
g_sched  = 0.4 nếu cả hai có lịch và overlap = 0;  1 nếu ngược lại
g_reject = 0.5 nếu gia sư đã từ chối học sinh này (bất kỳ tin nào) trong 90 ngày;  1 nếu không
```

Q chỉ dịch điểm ±15%; `recommendation_score` đã gồm chứng chỉ nên không cộng thêm thưởng chứng chỉ. Trên mô phỏng
(27/10/2026): `g_sched` hầu như không đổi top-10 (cặp lệch lịch hoàn toàn vốn đã xếp thấp); `g_reject` không đo được lợi
ích vì mô phỏng không có hiện tượng "đã từ chối thì dễ từ chối nữa" — giữ vì trải nghiệm người dùng (13 mục 13.8).

### Điểm chất lượng `recommendation_score` và khiếu nại có lỗi (bổ sung 15/10/2026)

> 💡 Cách tính điểm uy tín gia sư (thang 100): đánh giá sao, hợp đồng hoàn thành, tỉ lệ nhận đơn, tốc độ phản hồi, kinh nghiệm, xác minh hồ sơ, trừ điểm nếu từng bị khóa. Phần mới (phiên bản `v2`, đã lập trình ngày 20/10/2026) trừ thêm tối đa 20 điểm khi **nhân viên đã kết luận gia sư có lỗi** trong khiếu nại; mức trừ nhẹ dần khi gia sư có nhiều hợp đồng ("làm trơn" với 5 hợp đồng sạch giả định). Ví dụ 1 lần vắng mặt: gia sư mới bị trừ 8 điểm, gia sư đã có 20 hợp đồng chỉ bị trừ 1,6. Khiếu nại chưa kết luận hay vô căn cứ không bị tính.

Công thức (`TutorRecommendationScoreService`, `v2`, thang 100):

| Thành phần | Điểm tối đa | Cách tính |
|---|---|---|
| Đánh giá | 35 | Điểm trung bình làm trơn về 3,5 với độ tin cậy n/(n + 5), nhân 7 |
| Hợp đồng hoàn thành | 20 | min(số hợp đồng `COMPLETED`, 20) |
| Tỉ lệ nhận đơn | 15 | Theo `acceptance_rate`; chưa đủ 3 quyết định → 7,5 |
| Tốc độ phản hồi | 10 | ≤ 1 giờ → 10; ≥ 48 giờ → 0; chưa có → 5 |
| Kinh nghiệm | 10 | min(số học sinh đã dạy, 20) / 2 |
| Xác minh | 10 | Hồ sơ `VERIFIED` 5 + có chứng chỉ được duyệt 5 |
| Phạt bị khoá tài khoản | −15 | −5 mỗi lần bị khoá, tối đa −15 |
| **Phạt khiếu nại có lỗi (`v2`)** | **−20** | Dưới đây |

**Phạt khiếu nại có lỗi.** Dựa trên kết luận có cấu trúc của tính năng xử lý khiếu nại
(`complaints.fault_party`, `category`; migration `V28` của `dev`, đã merge vào nhánh đề xuất ngày 20/10/2026). Bên bị khiếu nại là bên còn lại của hợp đồng (`ComplaintParties.respondentOf`).

```
Khiếu nại c được tính cho gia sư T khi:
  c.status = RESOLVED, c.resolved_at trong 365 ngày gần nhất, c.contract.tutor = T, và
    (T là bên bị khiếu nại  — c.user ≠ T.user —  và c.fault_party ∈ {RESPONDENT, BOTH})
  hoặc (T là người gửi      — c.user = T.user —  và c.fault_party ∈ {COMPLAINANT, BOTH})

mức(c) = trọng số loại × (0,5 nếu BOTH, 1 nếu không)
  trọng số loại (khi T là bên bị khiếu nại): ABSENCE 1,0 · LATE_OR_SHORT 0,5 · TEACHING_QUALITY 0,5
                                             MISCONDUCT 1,5 · SERIOUS_MISCONDUCT 3,0 · OTHER / rỗng 1,0
  khi T là người gửi mà có lỗi: 1,0 (loại do T chọn mô tả học sinh, không mô tả lỗi của T)

W       = Σ mức(c)
n       = số hợp đồng đã ký của T (`signed_at` có giá trị, chưa xoá — theo chữ ký, không theo trạng thái:
          hợp đồng bị từ chối / hết hạn khi chưa ký cũng thành CANCELLED; sửa 21/10/2026)
phạt_K  = min(20, 40 × W / (n + 5))
score   = clamp(… − phạt_khoá − phạt_K, 0, 100)
```

| Ví dụ | W | n | phạt_K |
|---|---|---|---|
| 1 lần vắng mặt, gia sư mới | 1 | 0 | 8,0 |
| 1 lần vắng mặt, gia sư 20 hợp đồng | 1 | 20 | 1,6 |
| 3 lần vắng mặt trên 5 hợp đồng | 3 | 5 | 12,0 |
| 1 lần vi phạm nghiêm trọng, gia sư mới | 3 | 0 | 20,0 (chạm trần) |

Nguyên tắc:
- **Chỉ khiếu nại đã được nhân viên kết luận gia sư có lỗi** mới bị tính. `PENDING`, `IN_REVIEW`,
  `AWAITING_EVIDENCE`, `REJECTED`, `CANCELLED`, `fault_party = NONE` và khiếu nại kết luận trước khi có trường
  `fault_party` (giá trị rỗng) **không** ảnh hưởng — tránh việc gửi khiếu nại để hạ thứ hạng đối thủ.
- **Tỉ lệ có làm trơn Bayes** trên số hợp đồng (như cách làm trơn điểm đánh giá): một khiếu nại với gia sư
  dạy nhiều nhẹ hơn với gia sư mới; prior "5 hợp đồng sạch".
- Cửa sổ **365 ngày**: lỗi cũ hết tác dụng, gia sư có thể cải thiện. Job đêm tính lại toàn bộ nên điểm tự hồi
  khi khiếu nại ra khỏi cửa sổ.
- `category` là nhận định của người gửi; nhân viên kết luận lỗi sau khi xem minh chứng của hai bên, nên trọng
  số loại được dùng nhưng có trần chung 20 điểm. Nếu sau này nhân viên được xác nhận / sửa loại khi kết luận,
  dùng loại đã xác nhận.
- Ghi vào `score_breakdown`: `complaintPenalty`, `faultComplaints` (số khiếu nại), `complaintWeight` (W);
  `score_formula_version = v2`.
- **Không hiển thị cho học sinh** dưới dạng lý do khớp hay lưu ý (thông tin xử lý khiếu nại là nội bộ); admin
  xem qua `score_breakdown`.
- Không thêm gate cứng: phạt qua Q (chỉ dịch điểm cuối ±15%) đủ nhẹ để công bằng; trường hợp nghiêm trọng
  dùng cơ chế khoá tài khoản sẵn có — gia sư bị khoá không xuất hiện trong đề xuất.
- **Tính lại ngay khi có kết luận:** `TutorRecommendationScoreEventListener` thêm `COMPLAINT_RESOLVED` và
  `COMPLAINT_AGAINST_YOU_RESOLVED` vào nhóm sự kiện "tính lại nếu người nhận là gia sư" — `AdminComplaintService`
  gửi hai thông báo này cho người gửi và bên bị khiếu nại, nên gia sư ở vai nào cũng được tính lại.
- Truy vấn đếm đặt ở phía đề xuất (SQL riêng trong service / repository của tính năng đề xuất), không sửa
  `ComplaintRepository` của tính năng khiếu nại.
- Học sinh có lỗi: chưa có điểm chất lượng cho học sinh (chiều T→S không có Q) — để hướng phát triển (10).

**Ghi chú triển khai (20/10/2026).**

> 💡 Những chỗ lúc lập trình phải chọn cụ thể mà thiết kế chưa nói: khiếu nại đã bị xoá không tính; "gia sư là bên bị khiếu nại" xác định đúng như tính năng khiếu nại (người gửi là học sinh của hợp đồng); điểm phạt làm tròn 2 chữ số. Một gia sư không có khiếu nại nào thì điểm `v2` bằng đúng điểm `v1`.

- Truy vấn ở `TutorComplaintPenaltyRepository` (SQL riêng của tính năng đề xuất). Gia sư là bên bị khiếu nại khi
  người gửi là học sinh của hợp đồng (`complaints.user_id = students.user_id`), là người gửi khi
  `complaints.user_id = tutors.user_id` — cùng quy tắc với `ComplaintParties.respondentOf`. Khiếu nại có
  `deleted_at` không tính; hợp đồng đã xoá không vào n.
- Cửa sổ tính theo `resolved_at ≥ hiện tại − window-days`.
- Chỉ đếm n khi có ít nhất một khiếu nại có lỗi. `phạt_K` làm tròn 2 chữ số rồi trừ vào tổng như phạt khoá.
- Mức của khiếu nại do gia sư gửi mà có lỗi là tham số `complaint.complainant-weight` (1,0).
- `score_breakdown` có thêm `complaintPenalty`, `faultComplaints`, `complaintWeight`; không có API nào cho học sinh
  trả về `score_breakdown` (chỉ API quản trị `TutorRecommendationScoreResponse`), lý do khớp không đọc nó.
- Tính lại ngay: `COMPLAINT_RESOLVED` và `COMPLAINT_AGAINST_YOU_RESOLVED` nằm trong nhóm "người nhận là gia sư";
  `…_REJECTED` và `COMPLAINT_RECEIVED_AGAINST_YOU` không kích hoạt. Job đêm (`TutorRecommendationScoreScheduler`)
  tính lại mọi gia sư nên lỗi ra khỏi cửa sổ 365 ngày tự hết tác dụng.

## 6.9. Pha 4 — Rerank bằng cross-encoder (R3)

> 📌 **Đã làm 26/10/2026:** có chế độ rerank chạy nền (`rerank.mode = ASYNC`): lần tìm chỉ dùng điểm đã có trong
> cache, không bao giờ chờ model; cặp thiếu điểm được một worker nền chấm sau. **Mặc định hiện là `OFF`**: đo lại khi
> đã chấm đủ mọi cặp, rerank không cải thiện thứ tự (NDCG −0,001, KTC chứa 0); mức +0,013 đo ngày 22/10 là do lần tìm
> dùng nhầm vector hồ sơ khi AI service quá tải. Bật lại `ASYNC` khi chứng minh được lợi ích trên click thật. Bằng
> chứng, số đo và điều kiện bật lại: [13, mục 13.2](./13-tinh-chinh-sau-danh-gia.md).

> 💡 **Rerank** = chấm lại kỹ nhóm đầu. Mô hình tạo vector (bi-encoder) đọc từng văn bản riêng nên nhanh nhưng thô;
> **cross-encoder** (`bge-reranker-v2-m3`) đọc cả cặp (nhu cầu, tin đăng) cùng lúc nên lẽ ra chính xác hơn nhưng chậm
> (~1 s một cặp trên máy phát triển). Khi bật (`ASYNC`), lần tìm **không chờ** nó: dùng điểm đã chấm từ trước (lưu
> trong **cache**), tin nào chưa có điểm thì xếp theo vector như bình thường và được đưa vào hàng đợi để chấm "lúc
> rảnh". Hiện thành phần này **tắt mặc định** vì trên dữ liệu mô phỏng nó không làm kết quả tốt hơn. "Logit →
> sigmoid" là bước đổi điểm thô của mô hình về khoảng 0–1.

1. Sau pha 3, lấy **top-10** (`rerank.top-k`; 20 cho tới 21/10/2026).
2. Tra `recommendation_rerank_cache` theo (mã băm văn bản nhu cầu, ứng viên, mã băm nội dung ứng
   viên, phiên bản model). Văn bản và mã băm nội dung của ứng viên là bản đã lưu khi embed (`embedding_source_text`,
   `embedding_content_hash`), đọc trong một truy vấn; tin chưa embed thì dựng văn bản như trước (từ 26/10/2026).
3. Theo `rerank.mode`:
   - **`OFF`** (mặc định từ 26/10/2026, hoặc `rerank.enabled = false`): không rerank, đúng thứ tự pha 3 (V2-RC);
     không xếp hàng, job chấm sẵn ban đêm bỏ qua. Log `DISABLED`.
   - **`ASYNC`**: không gọi model. Cặp có điểm thì dùng; cặp chưa có giữ `f_sem = s` và được
     xếp vào hàng đợi chấm nền (`RerankQueue`). Log `rerankStatus`: `CACHE` (đủ cả nhóm) · `PARTIAL` (một phần) ·
     `NONE` (chưa có cặp nào).
   - **`ONLINE`** (cách cũ, giữ để so sánh): gọi `/rerank` theo lô 4 cặp, hạng cao trước, trong ngân sách 1 500 ms;
     cặp không kịp chấm cũng được xếp hàng chấm nền thay vì bỏ. Log `ONLINE` / `TIMEOUT` / `CACHE`.

   Đầu vào: (văn bản nhu cầu, văn bản cung cấp), mỗi bên ≤ 256 token. Model `bge-reranker-v2-m3` ONNX int8;
   logit → sigmoid.
4. Tính lại `f_sem`, a, R, `score` cho những tin **có điểm rerank**, sắp lại **trong nhóm top-k**;
   phía sau giữ nguyên.

**Worker chấm nền (`RerankQueue`).**

> 💡 Một "người làm việc" chạy riêng, nhận các cặp mà lần tìm thiếu điểm và chấm dần từng ít một. Trên laptop, model
> rerank và model tạo vector làm chậm nhau khi chạy cùng lúc (kể cả trên các lõi khác nhau, vì cùng tranh băng thông
> bộ nhớ), nên worker **nhường** lần tìm: chỉ chấm khi không có ai đang tìm.

| Vấn đề | Cách cài |
|---|---|
| Số yêu cầu đồng thời | Một luồng nền, mỗi lần một yêu cầu `/rerank` gồm tối đa `rerank.worker-chunk-size` (2) cặp cùng văn bản nhu cầu |
| Chống trùng | Một cặp (mã băm nhu cầu, loại, ứng viên, mã băm nội dung) chỉ nằm trong hàng đợi một lần, kể cả khi đang chấm |
| Nhường lần tìm | Chỉ chấm khi **không có lần tìm nào đang chạy** (bộ đếm tăng khi vào, giảm khi ra) **và đã yên ít nhất** `rerank.worker-quiet-ms` (2 000 ms) |
| Chống đói việc | Cặp đã chờ quá `rerank.worker-max-wait-ms` (10 phút) thì vẫn được chấm, **từng cặp một**, kể cả khi đang có lần tìm. Job đêm vẫn là lưới an toàn |
| Sức chứa | `rerank.queue-capacity` (2 000 cặp); đầy thì bỏ phần thừa, lần tìm sau xin lại |
| Lưu điểm | Cùng bảng cache, làm tròn 4 chữ số, ghi trong transaction riêng |
| Khởi động lại | Hàng đợi nằm trong bộ nhớ, mất khi tắt backend; lần tìm sau hoặc job đêm xin lại |

**AI service (từ 26/10/2026).** Mặc định không nạp model rerank (`AI_RERANK_ENABLED=false` trong
`docker-compose.yml`, tiết kiệm ~1,1 GB RAM; bật cùng lúc với `rerank.mode = ASYNC`). Khi bật: `/rerank` chạy trên một
executor riêng **một luồng** (các yêu cầu khác xếp hàng ở đó, không chiếm luồng của `/embed`); yêu cầu còn đang chờ
lượt mà người gọi đã bỏ chờ thì bị huỷ; model rerank dùng `AI_RERANK_THREADS` = 2 luồng ONNX và chấm **từng cặp một**
(`AI_RERANK_BATCH_SIZE` = 1); trước mỗi cặp, nếu đang có `/embed` chạy thì chờ embed xong (tối đa 10 s).

**Ghi nhận khi phải dùng vector hồ sơ.** Nếu câu truy vấn không embed kịp (`query-embedding.timeout-ms`), lần tìm
dùng vector hồ sơ của người tìm. Từ 26/10/2026 log ghi `query_context.queryVector`: `QUERY` (vector của câu
truy vấn) · `PROFILE_FALLBACK` (embed lỗi / quá hạn, dùng vector hồ sơ) · `PROFILE` (không có văn bản để embed).
Bộ đánh giá đếm riêng và loại các lần `PROFILE_FALLBACK` khỏi phép so sánh (09, 13.2).

Các tin học `OPEN` và các tìm kiếm đã lưu được rerank sẵn ở tiến trình nền (07, mục 7.3), nên khi
học sinh tìm lại với tiêu chí tương tự thường đã có cache.

### Ghi chú triển khai (12/10/2026)

> 💡 Lúc thiết kế đặt giới hạn 800 ms, nhưng đo thật mỗi cặp mất ~175 ms, 20 cặp mất ~3,5 giây; với 800 ms thì gần như lần nào cũng quá giờ và không bao giờ có cache. Nên đã nâng lên 4 giây và chấm theo lô nhỏ. Bảng dưới ghi cách xử lý các trường hợp cụ thể, kể cả job chấm sẵn ban đêm.

**Vì sao không còn 800 ms.** Đo trên máy phát triển (CPU 12 luồng, int8): khoảng 110–120 ms mỗi cặp
với tin rất ngắn, ~175 ms với tin điển hình (~70 token), ~590 ms với tin dài chạm trần 256 token.
20 cặp mất 2,2 s / 3,5 s / 11,8 s; gộp lô không nhanh hơn. Với timeout 800 ms và quy tắc "quá giờ thì
bỏ", lần tìm trực tiếp gần như luôn quá giờ và không bao giờ có cache. Quyết định (người dùng chọn):
**chờ lâu hơn** — ngân sách 4 000 ms, chia lô nhỏ để giữ lại phần đã chấm khi hết giờ. (Sau đó: hạ còn 1 500 ms
ngày 21/10/2026; từ 26/10/2026 mặc định `OFF`, khi bật dùng `ASYNC` — ghi chú đầu mục.)

| Vấn đề | Cách cài |
|---|---|
| Trạng thái ghi log (`query_context.rerankStatus`) | `CACHE` (đủ từ cache) · `ONLINE` (có gọi model, đủ cả nhóm) · `TIMEOUT` (thiếu một phần hoặc toàn bộ) · `PARTIAL` / `NONE` (ASYNC, từ 26/10/2026) · `DISABLED` |
| Tin có điểm rerank nhưng chưa có vector | `f_sem = rerank` |
| Văn bản ứng viên | Đúng văn bản dùng để embed tin đó (`EmbeddingTextBuilder`); mã băm nội dung = sha256 của văn bản này. Tin bị sửa → mã băm đổi → chấm lại |
| Điểm | Làm tròn 4 chữ số như cột lưu, để điểm lấy từ cache và điểm vừa chấm cho cùng thứ hạng |
| Chiều gia sư tìm học viên | Cùng cơ chế: văn bản cung cấp × tin học, `candidate_type = STUDYING_REQUEST` |
| Chấm sẵn ban đêm (`RerankPrefetchJob`, 19:00 UTC) | Với mỗi tin học `OPEN`: chạy pha 1–3 như lần tìm chỉ có `useStudyingRequestId`, chấm top-30, ngân sách 120 s mỗi tin. Không ghi log. Tìm kiếm đã lưu nối vào ở bước C6 |
| Dọn cache | Job đêm xoá dòng cũ hơn 30 ngày |
| Cờ tắt | `app.recommendation.rerank.enabled=false` (hoặc `rerank.mode=OFF`) → đúng thứ tự pha 3 (bản V2-RC) |

Hạn chế đã biết (cập nhật 26/10/2026): ở `ASYNC`, lần tìm đầu với tiêu chí mới chưa có điểm rerank (xếp theo
vector), chỉ các lần sau mới có; cache chỉ trúng khi văn bản nhu cầu giống hệt (đổi một bộ lọc là văn bản khác);
ở `ONLINE`, `/rerank` vẫn được gọi bên trong transaction của lần tìm. Số đo "~175 ms một cặp" ở trên là của tin
ngắn: với văn bản phía học sinh (600–800 ký tự) đo lại ngày 26/10 là ~0,9 s một cặp (13.2).

## 6.10. Gộp theo người

> 💡 Một gia sư có nhiều tin cùng khớp thì chỉ giữ tin điểm cao nhất, để danh sách là các **người** khác nhau. Bước này làm **trước** rerank để không tốn thời gian chấm kỹ những tin sẽ bị bỏ.

Một gia sư có nhiều tin dạy cùng khớp → chỉ giữ tin có `score` cao nhất.

**Thứ tự thực hiện (quyết định 12/10/2026): gộp ngay sau pha 3, trước rerank.** Lý do: rerank chậm, không
nên chấm những tin sẽ bị bỏ; và sau khi gộp, cả top-k (10 *người* khác nhau từ 21/10/2026, trước đó 20) đều được chấm. Đổi lại, tin
đại diện của mỗi người được chọn theo điểm trước rerank. Chiều gia sư tìm học viên gộp theo học sinh.
Gợi ý nới tiêu chí (07 mục 7.1) cũng đếm theo người.

## 6.11. Pha 5 — Slot khám phá (R6)

> 💡 Vị trí 5 và 12 trên trang đầu được dành cho tin "đủ tốt" (điểm ≥ 80% điểm của hạng 20) nhưng **ít được hiển thị** — để gia sư mới có cơ hội. Chọn bằng **Thompson Sampling**: mỗi tin có một phân phối Beta mô tả "tỉ lệ được bấm có thể là bao nhiêu"; rút ngẫu nhiên một số từ đó rồi nhân với điểm. Tin mới (`Beta(1,1)`, chưa biết gì) có khoảng rất rộng nên đôi khi rút ra số cao; tin hiển thị nhiều mà ít ai bấm thì khoảng hẹp, thấp, dần hết được chọn. Bốn vị trí đầu không bị đụng tới.

Trang đầu N = 20. **Vị trí 5 và 12** dành cho slot khám phá nếu tập ứng viên > N + 5.

1. E = ứng viên ngoài top-N, `score ≥ 0,8 × score hạng N`, `impressions_7d` dưới trung vị.
2. Rút mẫu Thompson `θ_e ~ Beta(1 + clicks_total, 1 + impressions_total − clicks_total)`; chọn
   `θ_e × score_e` lớn nhất cho slot 5, lặp lại (không trùng) cho slot 12.
3. Chèn vào, đẩy phần còn lại xuống; ghi `isExploration = true`.

Ghi chú triển khai (13/10/2026):
- Chạy **sau rerank**, trên danh sách đã gộp theo người; chỉ khi có hơn N + 5 kết quả.
- Điều kiện hiển thị là **không vượt trung vị** `impressions_7d` (thay cho "dưới trung vị"): khi phần lớn tin
  chưa từng hiển thị, trung vị bằng 0 và "dưới trung vị" sẽ không còn tin nào. Trung vị tính trên toàn bộ kết
  quả của lần tìm; tin chưa có số liệu coi như 0 lượt.
- Số liệu do `ExposureStatsJob` đếm lại hằng đêm (20:00 UTC) từ log: **lượt hiển thị** = tin nằm trong 20 hạng
  đầu của một lần tìm (trang đầu luôn được trả về); **lượt bấm** = một lần tìm trong đó người dùng mở / tym /
  liên hệ tin đó (`DISMISSED` không tính).
- Rút mẫu Beta bằng hai biến Gamma (Marsaglia–Tsang). Kết quả ngẫu nhiên theo từng lần tìm, nhưng thứ tự đã
  chọn được lưu trong log nên lật trang và tải lại vẫn ổn định.
- Tin ở slot khám phá có `isExploration = true` và lý do đầu tiên là "Gia sư mới trên nền tảng" (chiều gia sư:
  "Tin học ít được xem, có thể hợp với bạn"). Cờ tắt: `exploration.enabled=false`.

Tin mới (`Beta(1,1)`, phân bố rộng) có cơ hội xuất hiện; tin hiển thị nhiều mà ít click dần hết
được chọn. Không đụng 4 vị trí đầu. Slot khám phá cũng là nguồn dữ liệu ít thiên lệch cho việc
học trọng số sau này (10).

## 6.12. Chiều ngược lại — gia sư tìm học viên (T→S)

> 💡 Cùng khung như chiều học sinh tìm gia sư, đổi vai: ứng viên là **tin học** đang mở; a′ (dấu phẩy ′ chỉ chiều ngược lại) đo gia sư có thích tin học này không, giá có trọng số cao hơn (25%); b′ đo học sinh có khả năng nhận lời đề nghị của gia sư không, dựa vào uy tín, phong cách, giá của gia sư đang tìm và lịch sử học sinh nhận / từ chối đề nghị. Không có hệ số uy tín cho học sinh (nhóm không làm tính năng đánh giá học sinh). Chỉ gia sư đã được duyệt hồ sơ mới tìm được.

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
b′_cap  = 1 / (1 + 0.5·n);  n = đề nghị PENDING vào sr   (mỗi tin học nhận đúng một gia sư, xem ghi chú 13/10/2026;
          cùng công thức b_cap của 6.6 với r = 1, nên công thức mới và cũ cho cùng giá trị)
```

Ngoại lệ: học sinh đã gửi đơn `PENDING` vào một tin dạy `OPEN` của chính gia sư này → `b′ = 1`.

Từ L1: trọng số a′ (mô hình `A_T2S`) và dạng dàn phẳng của b′ — `0,5·b′_hist + 0,20·Q + 0,15·f_style +
0,15·f_price` (mô hình `B_T2S`) — là prior và được học như chiều S→T ([12](./12-hoc-trong-so.md)).

**Điểm cuối:** `score′ = R′ × G′`, `R′ = a′^α · b′^(1−α)` như 6.7 (không có hệ số chất lượng cho học sinh). Rerank, gộp (theo học
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
- **Sau khi merge `dev` #49 (13/10/2026):** cột `studying_requests.quantity` bị xoá — mỗi tin học nhận đúng một gia
  sư và chuyển `MATCHED` ngay khi có đề nghị được nhận. Vì ứng viên luôn là tin `OPEN`, số chỗ còn lại luôn là 1
  và `b′_cap = 1 / (1 + 0,5·n)`. Chiều học sinh tìm gia sư không đổi (tin dạy vẫn có `quantity`).
- **Tin học ghi môn ngoài danh mục** (`subject_id` rỗng, tên môn nằm trong `note`) ở trạng thái `PENDING_REVIEW`
  cho tới khi được duyệt: **không được đề xuất** (lọc cứng cần `OPEN` và đúng môn), không được embed, không gửi
  LLM, không sinh thông báo ghép đôi. Hiện `dev` chưa có luồng duyệt tin học, nên các tin này chưa thể thành
  `OPEN`.
- **Chỉ gia sư có hồ sơ `VERIFIED` mới tìm được học viên** (`403 TUTOR_PROFILE_NOT_VERIFIED`, kể cả khi lật
  trang bằng `logId`), cho nhất quán với chiều S→T chỉ hiện gia sư đã duyệt (quyết định 12/10/2026).
- Giá mong muốn để trống → trung vị `expected_price` của mọi tin dạy của gia sư; không có tin → không lọc
  và không chấm theo giá.

## 6.13. Lý do khớp

> 💡 Mỗi kết quả có tối đa 3 câu lý do tích cực và 1 câu lưu ý, chọn theo **bảng mẫu**: đặc trưng nào đủ cao thì sinh câu tương ứng, điền tên tag, giá, khung giờ thật vào. Ví dụ `f_price = 1` → "Học phí 180.000đ — nằm trong ngân sách". Lý do nào đóng góp nhiều điểm hơn thì đứng trước. Câu do code sinh, không do AI viết, và chỉ sinh khi có dữ kiện thật.

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

**Chiều gia sư tìm học viên (T→S)** — bổ sung 12/10/2026, đối xứng với bảng trên:

| Điều kiện | Câu mẫu |
|---|---|
| `f_need ≥ 0,7` | "Học sinh cần đúng phần bạn mạnh: {tag khớp}" |
| `f_sem ≥ 0,7` | "Nhu cầu của học sinh rất sát với những gì bạn dạy" |
| `f_segment = 1` | "Trình độ {mức} — đúng đối tượng bạn muốn dạy" |
| `f_price = 1` | "Ngân sách tới {max_price} — đủ mức bạn mong muốn" |
| `f_sched ≥ 0,8` | "Trùng lịch rảnh: {khung giờ}" |
| `f_area = 1` và `OFFLINE` | "Học tại {quận}, trong khu vực bạn dạy" |
| Học sinh đã gửi đơn vào tin dạy của gia sư (`b′ = 1`) | "Học sinh này đã gửi đơn vào tin dạy của bạn" |
| `b′_hist ≥ 0,75` và ≥ 3 quyết định | "Học sinh này thường nhận lời đề nghị của gia sư" |
| `b′_cap = 1` | "Chưa có gia sư nào khác đang chờ học sinh này trả lời" |
| **Lưu ý:** `g_sched < 1` | "Lịch chưa trùng — có thể trao đổi thêm" |
| **Lưu ý:** `f_price < 1` | "Ngân sách thấp hơn mức bạn mong muốn {chênh lệch}" |
| **Lưu ý:** `f_area ≤ 0,6` | "Ngoài khu vực bạn chọn ưu tiên ({quận})" |

Ghi chú triển khai (12/10/2026):
- Chỉ sinh câu khi có **dữ kiện thật**: `f_style = 0,5` do gia sư chưa có tag phong cách (giá trị trung tính)
  không tạo câu "Phong cách…"; câu "Dạy tại {quận}" chỉ có khi tin thật sự có quận nằm trong vùng đã chọn;
  câu đánh giá chỉ có khi gia sư đã có lượt đánh giá.
- Tối đa 2 tag và 2 khung giờ mỗi câu. Tiền viết kiểu `180.000đ`; `{lớp}` là "lớp {level}".
- Chỉ một lưu ý, theo thứ tự: lịch → giá → khu vực.
- Lý do được lưu trong `returned_candidate_ids` của log (`reasons`, `caution`), nên trang sau lấy bằng
  `logId` hiện đúng câu của lần tìm.
- Đã có từ 13/10/2026: lý do theo `f_hist` ("Giống các gia sư bạn đã quan tâm: {tối đa 2 tag người dùng ưa
  nhất mà tin này có}"; chiều gia sư: "Giống các học viên bạn đã quan tâm…") và lý do của slot khám phá.

## 6.14. Ví dụ tính tay

> 💡 Đi trọn một ví dụ với số cụ thể: gia sư X rất hợp với Minh (a = 0,871) nhưng tin đang có 3 người chờ cho 1 chỗ nên b chỉ 0,341; gia sư Y hợp vừa phải (a = 0,75) nhưng tin còn trống và hay nhận học sinh như Minh (b = 0,80). Kết quả Y (0,675) đứng trên X (0,593) — đúng tinh thần "phải hợp cả hai phía". Đoạn cuối cho thấy lịch sử hành vi dần thay đổi thứ hạng.

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
| **R** | 0,871^0,65 · 0,341^0,35 | **0,627** |
| **score** | 0,627 × (0,7 + 0,3·0,82) | **0,593** |

**Gia sư Y**: a = 0,75, b = 0,80 (tin không bị dồn, hay nhận học sinh như Minh),
`recommendation_score` 60 → R = 0,75^0,65 · 0,80^0,35 = 0,767; score = 0,767 × 0,88 = **0,675** → Y đứng trên X.

**Sau vài tuần**, Minh đã có 12 tín hiệu (`w_hist = 0,15`) và chủ yếu quan tâm gia sư kiểu luyện
đề; với X, `f_hist = 0,4` → a = 0,85·0,871 + 0,15·0,4 = 0,801; R = 0,594; score = 0,562. Một gia sư
luyện đề có `f_hist` cao sẽ được đẩy lên tương ứng — hệ thống "học" dần sở thích thật của Minh.
