# 9. Đánh giá (D9)

> 💡 **File này nói về gì:** Làm sao chứng minh bằng số liệu rằng thuật toán tốt, và phiên bản mới tốt hơn phiên bản cũ, khi **chưa có người dùng thật**. Cách làm: tự tạo dữ liệu mô phỏng có đáp án ẩn. File này dùng trực tiếp cho chương đánh giá trong báo cáo. Giải thích trực giác: [00](./00-doc-truoc.md) mục 4.10.

Mục tiêu: trả lời bằng số liệu câu hỏi "làm sao biết thuật toán mới tốt hơn?" khi chưa có người
dùng thật.

## 9.1. Ý tưởng chính: dữ liệu mô phỏng có "đáp án ẩn"

> 💡 Code sinh học sinh và gia sư ảo với **thuộc tính ẩn** (nhu cầu thật, ngân sách, gia sư thật sự thích nhận ai…); LLM viết hồ sơ, tin đăng dạng chữ từ các thuộc tính đó. Hệ thống đề xuất chỉ được đọc chữ; người chấm thì biết thuộc tính ẩn nên biết đáp án đúng. Giống thầy giáo tự soạn đề kèm đáp án, tránh cảnh "AI tự chấm bài AI làm".

Dữ liệu được sinh theo **hai lớp**:

1. **Thuộc tính ẩn** (sinh bằng code, có kiểm soát): nhu cầu thật, phong cách, trình độ, ngân sách,
   lịch, khu vực; với gia sư thêm **tiêu chí nhận học sinh** (khoảng lớp ưa thích, giá tối thiểu,
   đối tượng ưa thích, số chỗ).
2. **Văn bản hiển thị** (LLM viết từ thuộc tính ẩn): hồ sơ, tin đăng, lịch dạng chữ — độ chi tiết
   khác nhau, có cả hồ sơ sơ sài, viết tắt, lỗi chính tả.

Hệ thống đề xuất **chỉ thấy văn bản và tiêu chí nhập**, như với người dùng thật. Đáp án tính từ
**thuộc tính ẩn** — khách quan, tránh vòng lặp "LLM tự chấm dữ liệu LLM tạo".

## 9.2. Bộ dữ liệu

> 💡 Quy mô bộ dữ liệu ảo: 3 môn, 120 gia sư (~180 tin dạy), 60 học sinh, mỗi người vài lần tìm, cộng 4 tuần lịch sử gửi đơn và duyệt danh sách. Một nửa số lần tìm dùng để **hiệu chỉnh** trọng số, nửa kia để **kiểm tra** — chỉ báo cáo số liệu trên nửa kiểm tra, để không "học tủ".

| Thành phần | Quy mô |
|---|---|
| Môn / lớp / khu vực | Toán, Tiếng Anh, Vật lý; lớp 6–12; Hà Nội và TP.HCM, mỗi nơi 6 quận |
| Gia sư | 120, mỗi người 1–2 tin dạy `OPEN` (≈ 180 tin) |
| Học sinh | 60; khoảng một nửa có tin học `OPEN` (≈ 45 tin), một nửa **không có tin đăng** |
| Truy vấn tìm kiếm | Mỗi học sinh 2–3 truy vấn (bộ lọc sinh từ thuộc tính ẩn, có truy vấn đủ tiêu chí, có truy vấn chỉ môn + lớp; một phần dạng câu tự nhiên); mỗi gia sư 1–2 truy vấn |
| Lịch sử mô phỏng | 4 tuần nộp đơn/quyết định (cho b, chống dồn) và hành vi duyệt danh sách (cho H1) |

Truy vấn chia đôi: **50% hiệu chỉnh** (trọng số, ngưỡng) và **50% kiểm tra**. Số liệu báo cáo chỉ lấy
trên phần kiểm tra.

## 9.3. Đáp án và mô hình hành vi

> 💡 Đáp án `rel` từ 0 đến 3 = mức phù hợp thật của một cặp (0 là hoàn toàn không hợp). Quyết định nhận / từ chối của gia sư ảo theo một công thức xác suất: `sigmoid` biến một tổng điểm thành xác suất 0–1, ví dụ đúng lớp ưa thích và đủ giá thì xác suất nhận cao. Hành vi bấm của người dùng ảo giảm dần theo vị trí (ít ai đọc tới cuối danh sách). Cohen's kappa là chỉ số đo hai người chấm đồng ý với nhau tới đâu.

**Độ phù hợp thật** `rel ∈ {0, 1, 2, 3}` từ thuộc tính ẩn (0: lệch lịch hoàn toàn / vượt ngân sách
> 30% / không phủ nhu cầu chính; 1–3 theo mức phủ nhu cầu, phong cách, đối tượng — công thức cố
định, công bố trong báo cáo).

**Độ tin cậy ẩn của gia sư** (bổ sung 15/10/2026): một phần nhỏ gia sư ảo có độ tin cậy thấp; khiếu nại được
sinh theo độ tin cậy, kèm `category` và `fault_party` (có cả khiếu nại vô căn cứ → `REJECTED` / `NONE`). Kiểm
tra: phạt khiếu nại trong Q làm giảm lượt hiển thị top-10 của gia sư kém tin cậy, trong khi gia sư chỉ bị khiếu
nại vô căn cứ không bị ảnh hưởng.

**Quyết định của gia sư:**

```
P(nhận) = sigmoid(β0 + β1·[lớp trong khoảng ưa thích] + β2·[giá ≥ giá tối thiểu]
                  + β3·[đối tượng ưa thích] + β4·tỉ lệ trùng lịch)
```

không vượt số chỗ còn lại (ai nộp trước được xét trước). β = (β0, β1…β4) = (−1; 1,6; 1,2; 1,0; 1,2) ở `base` và
`text-only`.

**Kịch bản thị trường đông (`crowded`) — hiệu chỉnh 26/10/2026** (lý do: 13 mục 13.1, 13.3; tham số chọn bằng
`calibrate_crowded.py`, đo lại trên database dựng mới):

> 💡 Ba chỉnh sửa để bài kiểm tra có cạnh tranh thật: gia sư khó tính hơn (chỉ khoảng một nửa số đơn được nhận),
> gia sư giỏi vừa được đánh giá cao vừa hợp hơn với học sinh, và nhiều học sinh hơn để gia sư cũng có đủ người để
> chọn. Chỉ áp dụng cho kịch bản này; `base` và `text-only` sinh ra đúng thế giới cũ (có test).

| Thành phần | Công thức |
|---|---|
| Quyết định của gia sư | như trên, **β0 = −3,1** → P(nhận) trung bình của ứng viên ≈ 0,5 (với β0 = −1 là 0,86) |
| Độ phù hợp thật | `rel` như trên, rồi: `quality ≥ 0,75` → +1 mức (tối đa 3); `quality < 0,6` → −1 mức (tối thiểu 1); `rel = 0` giữ nguyên |
| Đánh giá sau hợp đồng | gia sư nổi tiếng: 5 sao; còn lại `round(1 + 4·quality + N(0; 0,6))`, trừ 1 sao nếu gia sư kém tin cậy, kẹp trong 1–5 (trước: tin cậy 4–5, kém tin cậy 2–4, không theo `quality`) |
| Quy mô | 600 gia sư (15 % nổi tiếng, 1–2 chỗ; 20 % mới), **400 học sinh** (trước 300), 4 quận Hà Nội, 2 môn |

`quality` ẩn của gia sư = `min(1; 0,45 + 0,04·số năm kinh nghiệm + U(−0,1; 0,15))`; gia sư nổi tiếng `U(0,9; 1)`.
Vì Q của nền tảng tính từ đánh giá và khiếu nại, đánh giá theo `quality` làm Q phản ánh chất lượng thật; `rel` theo
`quality` làm chất lượng đó liên quan tới độ phù hợp — tương quan mục tiêu 0,2–0,4.

**Hành vi duyệt danh sách** (để tạo tín hiệu cho H1, B5): mô hình click theo vị trí —
`P(click) = P(nhìn tới vị trí k) × P(hấp dẫn | rel, sở thích ẩn)`, với `P(nhìn tới vị trí k)` giảm dần
theo k. Học sinh ảo có **sở thích ẩn** (ví dụ thích kiểu luyện đề) không ghi trong hồ sơ — để kiểm tra
H1 có "học" ra được không.

**Nhãn LLM đóng vai học sinh** (bổ sung): 30 truy vấn, gộp top-10 của mọi cấu hình, LLM chấm 0–3 theo
rubric; nhóm chấm lại 20%; báo cáo Cohen's kappa có trọng số và tương quan với `rel` ẩn.

## 9.4. Các cấu hình so sánh

> 💡 Bật dần từng thành phần để thấy mỗi thành phần đóng góp bao nhiêu: V1 (cách cũ, cộng tuyến tính) → V2-a (chỉ một chiều) → V2-R (thêm hai chiều) → V2-RC (thêm chống dồn) → V2-RCX (thêm rerank) → V2-full (thêm khám phá và lịch sử). Hai dòng "− H1", "− A1" là bỏ riêng một thành phần ra (ablation) để đo tác dụng của nó.

Thiết kế so sánh theo cách của Su, Bayoumi, Joachims (2022): từ xếp hạng một chiều → hai chiều →
hai chiều có xét dồn đơn (11, mục 11.3).

| Cấu hình | Thành phần |
|---|---|
| **V1** (baseline) | Tổng tuyến tính 8 thành phần như bộ tài liệu cũ, embedding hồ sơ |
| **V2-a** (một chiều) | Đặc trưng mới + embedding theo tin đăng + cấu trúc nhân/gate, chỉ dùng a |
| **V2-R** (hai chiều) | + reciprocal (b không có chống dồn) |
| **V2-RC** | + chống dồn (`b_cap`) |
| **V2-RCX** | + cross-encoder rerank |
| **V2-full** | + slot khám phá + H1 |
| **V2-full − H1** | Bỏ lịch sử hành vi |
| **V2-full − A1** | Bỏ dữ liệu chuẩn hoá từ A1 |

Các cấu hình trên dùng **trọng số prior**. Ảnh hưởng của L1 (học trọng số) được đánh giá riêng bằng thí
nghiệm học tuần tự ở mục 9.9 (Fixed / LinTS-prior / LinTS-bad / Greedy / Oracle), vì L1 chỉ có tác dụng theo
thời gian, không so được trên một lượt truy vấn tĩnh.

## 9.5. Chỉ số

> 💡 Các con số dùng để chấm, chia theo nhóm: chất lượng xếp hạng, hiệu quả cho cả thị trường, khả năng học sở thích, công bằng hiển thị, độ chính xác của phần LLM, và tốc độ.

### Chất lượng xếp hạng (trung bình theo truy vấn)

> 💡 **NDCG@10**: các tin phù hợp có nằm ở 10 vị trí đầu, càng phù hợp càng ở cao không (1 là hoàn hảo). **Precision@5**: trong 5 tin đầu có bao nhiêu tin phù hợp. **MRR**: tin phù hợp đầu tiên nằm ở vị trí mấy (ở vị trí 1 → 1; ở vị trí 2 → 0,5). **EA@10** và **MS@10** đo thêm phía kia: các tin trong top-10 có khả năng nhận người tìm không.

| Chỉ số | Ý nghĩa |
|---|---|
| **NDCG@10** | Có đưa ứng viên phù hợp lên đầu không (`rel` 0–3) |
| **Precision@5** | Tỉ lệ `rel ≥ 2` trong top-5 |
| **MRR** | Vị trí ứng viên phù hợp đầu tiên |
| **EA@10** | Trung bình `P(nhận)` của top-10 (phần reciprocal) |
| **MS@10** | Tỉ lệ top-10 vừa `rel ≥ 2` vừa được nhận (mô phỏng) |

### Mô phỏng cả thị trường (tác dụng của R1 + B4)

> 💡 Cho mọi học sinh ảo cùng gửi đơn vào top-3 của mình, gia sư ảo quyết định theo mô hình. Đo tỉ lệ ghép thành công và số lần bị từ chối — để thấy gợi ý hai chiều và chống dồn đơn giúp được bao nhiêu.

Mỗi học sinh nộp đơn lần lượt vào top-3; gia sư quyết định theo mô phỏng, có giới hạn chỗ. Đo
**tỉ lệ ghép thành công**, **số lần bị từ chối trung bình**, **độ phù hợp trung bình** của ghép đôi.

### Thích ứng theo hành vi (H1)

> 💡 Cho cùng một học sinh ảo tìm nhiều lần liên tiếp; kiểm tra NDCG có tăng dần theo số lần không, và sở thích hệ thống học ra có khớp với sở thích ẩn không.

Chạy nhiều phiên liên tiếp cho cùng học sinh ảo: sau mỗi phiên cập nhật hồ sơ sở thích từ các click
mô phỏng. Đo **NDCG@10 theo số phiên** (V2-full so với V2-full − H1) và mức trùng giữa tag ưa thích
học được với **sở thích ẩn**.

### Công bằng hiển thị (R6)

> 💡 **Gini** đo mức chênh lệch lượt hiển thị giữa các tin (0 = chia đều tuyệt đối, 1 = một tin chiếm hết). **Coverage@20**: bao nhiêu phần trăm tin từng xuất hiện trong top-20.

**Gini** lượt hiển thị giữa các tin dạy; **Coverage@20**; lượt hiển thị trung bình của gia sư mới.

### Thành phần LLM

> 💡 Đo độ chính xác của phần LLM trên các bộ câu nhóm tự gán nhãn. **F1** là chỉ số gộp hai câu hỏi "trích ra có đúng không" (precision) và "có bỏ sót không" (recall). **IoU** khung giờ = phần giờ trùng chia phần giờ hợp của hai khung.

| Thành phần | Bộ kiểm tra | Chỉ số |
|---|---|---|
| A1 — tag | 60 văn bản nhóm tự gán nhãn | Micro-F1 |
| A1 — lịch | 60 câu lịch dạng chữ | Độ chính xác theo ngày; IoU khung giờ |
| A2 — tách câu | 80 câu nhóm tự viết | P/R/F1 theo từng ô bộ lọc; % câu điền đúng toàn bộ |
| A2 — phần ngữ nghĩa (thêm 13/10/2026) | Cùng 80 câu, chạy xếp hạng với hai biến thể `semanticText`: nguyên câu (đang dùng) và "phần còn lại" do LLM tách | Chênh lệch NDCG@10; số câu LLM thêm ý không có trong câu gốc; số ô bộ lọc được **suy ra** thay vì nêu rõ |

### Hiệu năng

> 💡 Đo thời gian chạy: p50 là mức mà một nửa số lần tìm nhanh hơn, p95 là mức mà 95% số lần nhanh hơn.

p50/p95 từng pha trên laptop và VPS; tỉ lệ rerank timeout.

## 9.6. Quy trình

> 💡 Bảy bước từ sinh dữ liệu đến so sánh. **Wilcoxon signed-rank** là phép kiểm định thống kê xem chênh lệch giữa hai cấu hình có thật hay do may rủi; **bootstrap** là cách ước lượng khoảng tin cậy bằng cách lấy mẫu lại nhiều lần.

1. Sinh thuộc tính ẩn (Python, seed cố định).
2. LLM viết văn bản → nạp qua chính API của hệ thống (A1, embedding chạy đúng luồng thật).
3. Mô phỏng 4 tuần nộp đơn và duyệt danh sách để tạo lịch sử.
4. Hiệu chỉnh trọng số/ngưỡng trên nửa hiệu chỉnh (tìm lưới thô quanh giá trị khởi đầu).
5. Chạy mọi cấu hình trên nửa kiểm tra, xuất CSV.
6. So từng cặp cấu hình theo truy vấn bằng **Wilcoxon signed-rank**, khoảng tin cậy 95% bằng **bootstrap**.
7. Phân tích độ nhạy và thí nghiệm học trọng số (9.9).

## 9.7. Mẫu bảng kết quả

> 💡 Khung bảng sẽ điền khi chạy xong, kèm các giả thuyết cần kiểm chứng (ví dụ "thêm hai chiều thì tỉ lệ ghép thành công tăng").

| Cấu hình | NDCG@10 | P@5 | EA@10 | MS@10 | Tỉ lệ ghép | Từ chối/HS | Gini | p95 (ms) |
|---|---|---|---|---|---|---|---|---|
| V1 | | | | | | | | |
| V2-a | | | | | | | | |
| V2-R | | | | | | | | |
| V2-RC | | | | | | | | |
| V2-RCX | | | | | | | | |
| V2-full | | | | | | | | |
| V2-full − H1 | | | | | | | | |
| V2-full − A1 | | | | | | | | |

Giả thuyết (cần kiểm chứng): V2-a tăng NDCG so với V1; V2-R, V2-RC tăng EA@10, MS@10, tỉ lệ ghép và
giảm số lần bị từ chối; V2-RCX tăng NDCG/P@5; H1 tăng NDCG dần theo số phiên; slot khám phá giảm Gini
mà NDCG giảm không đáng kể.

## 9.8. Hạn chế cần nêu

> 💡 Những điểm phải nói thẳng trong báo cáo: dữ liệu là mô phỏng, mô hình hành vi do nhóm tự đặt, cơ chế học trọng số mới chỉ chứng minh trên mô phỏng.

- Dữ liệu mô phỏng; hành vi người thật có thể khác. Thuộc tính ẩn, mô hình quyết định và mô hình click
  do nhóm thiết kế.
- Cơ chế học trọng số (L1) chỉ được chứng minh trên mô phỏng; chưa có kết quả học từ người dùng thật.
- Nhãn LLM chỉ là bổ sung; kết luận chính dựa trên đáp án từ thuộc tính ẩn.
- Văn bản hồ sơ và tin đăng viết theo mẫu (9.10), đều đặn hơn văn người thật: phần đọc chữ (embedding,
  rerank, A1) có thể được đánh giá cao hơn thực tế. Bộ nhãn LLM đóng vai học sinh (9.3) và bộ kiểm tra A1/A2 tự gán
  nhãn (9.5) chưa làm.

## 9.9. Trọng số: phân tích độ nhạy và thí nghiệm học (L1)

> 💡 Hai câu hỏi về các trọng số nhóm tự đặt: kết quả có thay đổi mạnh nếu đổi trọng số một chút không (**độ nhạy**), và nếu trọng số sai thì cơ chế tự học (L1, đang thiết kế) có tự sửa được không.

Mục này trả lời hai câu hỏi về trọng số tự đặt: **kết quả có phụ thuộc mạnh vào con số cụ thể không**, và
**nếu sai thì hệ thống có tự sửa được không** (cơ chế L1 — [12](./12-hoc-trong-so.md)).

### 9.9.1. Phân tích độ nhạy (trên nửa kiểm tra)

> 💡 Tăng / giảm từng trọng số 25% và 50%, chạy lại, xem kết quả đổi bao nhiêu. **Kendall τ** đo hai danh sách xếp hạng giống nhau tới đâu (1 = y hệt). Biểu đồ "lốc xoáy" (tornado) xếp các tham số theo mức ảnh hưởng. Kỳ vọng: đa số tham số đổi chút ít thì kết quả vẫn ổn định.

- Mỗi trọng số trong a, a′, b (dạng dàn phẳng) và các tham số giữ cố định (kernel, điểm thành phần, λ, tham số
  phạt khiếu nại `penalty-scale`, `max-penalty`, `prior-contracts`):
  tăng / giảm **±25% và ±50%**, chuẩn hoá lại tổng, chạy lại V2-full.
- Đo: NDCG@10, EA@10, và **Kendall τ** giữa top-10 mới và top-10 gốc.
- Kết quả trình bày dạng biểu đồ "lốc xoáy" (tornado): tham số nào làm chỉ số đổi nhiều nhất.
- Kỳ vọng: kết quả **ổn định** (τ cao, NDCG đổi ít) với đa số tham số — trọng số tự đặt không phải "điểm
  may mắn". Tham số nhạy được nêu trong báo cáo và là ứng viên ưu tiên cho L1.

### 9.9.2. Mô hình hành vi có "trọng số thật"

> 💡 Để kiểm tra L1 học đúng không, người dùng ảo được cho một bộ **trọng số thật** `w*` (ví dụ coi giá quan trọng hơn nhóm nghĩ) mà hệ thống không nhìn thấy. `ε` là nhiễu ngẫu nhiên cho giống người thật. Nếu L1 tốt, trọng số nó học ra phải tiến dần về `w*`.

Để kiểm tra L1 có học đúng hay không, mô hình hành vi của người dùng ảo (9.3) được viết sao cho **biết
trước trọng số thật**:

```
Người tìm (phía a):  u = w* · x  +  ε                         (x = vector đặc trưng như hệ thống tính)
                     P(hấp dẫn | x) = sigmoid(κ_u · (u − c_u))
                     P(hành động) = P(nhìn tới vị trí k) × P(hấp dẫn) → xem / tym / gửi đơn theo ngưỡng
Phía kia (phía b):   P(nhận) = sigmoid(β · z)                  (z = thành phần b dàn phẳng)
```

`w*` và `β` là tham số **ẩn** của bộ mô phỏng, khác với prior (ví dụ người dùng ảo coi giá quan trọng hơn,
ngữ nghĩa ít hơn so với trọng số nhóm đặt). Hệ thống chỉ thấy phản hồi, không thấy `w*`, `β`.

### 9.9.3. Giao thức

> 💡 Cách chạy thí nghiệm: xuất dữ liệu từ backend một lần, rồi mô phỏng 3.000 lần tìm trong Python, cập nhật trọng số mỗi 100 lần. So năm cấu hình: dùng trọng số cố định; tự học từ trọng số nhóm đặt; tự học từ trọng số **cố tình đặt sai**; tự học nhưng không thử nghiệm (Greedy — tham lam); và Oracle — biết trước đáp án, dùng làm mức tốt nhất có thể.

- Vector đặc trưng **không phụ thuộc trọng số**, nên chạy backend một lần cho mọi truy vấn với
  `log-candidate-limit = 200`, xuất `x`, thành phần b, Q, G từ log. Thí nghiệm học chạy trong Python trên dữ
  liệu xuất này (nhanh, lặp được nhiều lần); điểm được tính lại đúng công thức 06.
- Bản Python của `LinearBayesModel` và hàm `project` được đối chiếu với bản Java bằng một test dùng chung
  dữ liệu (cùng đầu vào → cùng μ, cùng trọng số sau `project`) — để cơ chế được chứng minh **chính là** cơ
  chế trong sản phẩm.
- Mô phỏng tuần tự **T = 3 000 lần tìm**; cứ 100 lần tìm thì cập nhật một lần (giả lập job đêm); 10 hạt
  giống ngẫu nhiên.

| Cấu hình | Mô tả |
|---|---|
| **Fixed** | Luôn dùng prior (bản hiện tại) |
| **LinTS-prior** | L1 xuất phát từ prior |
| **LinTS-bad** | L1 xuất phát từ prior **cố tình đặt sai** (ví dụ đảo thứ tự quan trọng, hoặc chia đều) |
| **Greedy** | Như LinTS-prior nhưng `ρ = 0` (không khám phá) |
| **Oracle** | Dùng đúng `w*`, `β` — giới hạn trên |

### 9.9.4. Chỉ số và biểu đồ

> 💡 **Regret** ("tiếc nuối") = tổng phần thưởng bị mất so với Oracle; cơ chế học tốt thì regret tăng chậm dần, còn trọng số cố định thì tăng đều. Thêm sai số giữa trọng số học được và `w*`, và "cần bao nhiêu lần tìm thì sai số đủ nhỏ" — con số hữu ích khi bảo vệ. κ, ρ là hai tham số của cơ chế học (12 mục 12.3–12.4); IPW là cách giảm thiên lệch vị trí (12 mục 12.5).

| Chỉ số | Biểu đồ |
|---|---|
| **Regret tích luỹ** = Σ (phần thưởng kỳ vọng của Oracle − của cấu hình) | Đường theo số lần tìm; LinTS tăng chậm dần, Fixed tăng tuyến tính |
| **Sai số trọng số** ‖w_t − w*‖₁ (sau khi chuẩn hoá) | Đường theo số lần tìm; quỹ đạo từng trọng số so với đường ngang `w*` |
| NDCG@10 và EA@10 theo số lần tìm | Đường |
| Phía b: log-loss / AUC dự đoán nhận so với `β` | Đường |
| Số lần tìm cần để sai số trọng số < 0,05 | Bảng — con số "cần bao nhiêu dữ liệu" dùng khi bảo vệ |
| Kiểm tra an toàn: không lần cập nhật nào vượt `max-step`, mọi trọng số trong biên kẹp | Bảng |

Thêm một lượt **độ nhạy của L1**: κ ∈ {50, 200, 800}, ρ ∈ {0, 0,5, 1, 2}, có / không IPW.

Giả thuyết (cần kiểm chứng): LinTS-prior có regret thấp hơn Fixed và Greedy; trọng số hội tụ về `w*`
(trong giới hạn của biên kẹp); **LinTS-bad bắt kịp LinTS-prior** sau một số lần tìm; Greedy có thể mắc kẹt
khi prior lệch nhiều.

Hạn chế riêng: `w*`, `β` và mô hình click do nhóm đặt; nếu `w*` nằm ngoài biên kẹp quanh prior thì L1 chỉ
tới được biên — điều này được trình bày như một **đánh đổi có chủ đích** giữa an toàn và tốc độ học.

> 💡 **Kết quả trên `crowded` (Bước 41, 27/10/2026; 13 mục 13.7):** truy vấn đa dạng (1 180 truy vấn) thay cho 106
> truy vấn lặp; độ nhạy chạy theo κ ∈ {20, 50, 100} × `max-step` ∈ {0,05; 0,1} (thay lưới κ × ρ × IPW); thêm mốc "cố
> định ở prior sai" để đo L1 sửa được bao nhiêu. Với mặc định mới (κ 50, ρ 0,5, γ 0,99) L1 có regret thấp hơn Fixed
> 21 % (10/10 hạt giống). Giả thuyết **đúng một phần**: khám phá không hơn Greedy (tốn ~2 % regret); prior chia đều
> được sửa phần lớn (regret −34 % so với giữ prior đó, sai số 0,54 → 0,23); prior đảo ngược chỉ sửa được một phần
> (−29 %, vẫn tệ hơn Fixed nhóm đặt 64 %) vì biên kẹp — đúng đánh đổi nêu ở trên.

## 9.10. Ghi chú triển khai (21/10/2026)

> 📌 **Đã làm 26/10/2026:** các lượt chạy 21–22/10 (base, text-only, đánh giá LLM) dùng để tinh chỉnh kiến trúc, chưa phải số cho báo cáo. Kịch bản thị trường đông đã được hiệu chỉnh và dựng lại (công thức ở mục 9.3; P(nhận) trung bình 0,52, tương quan Q–rel 0,20, trung vị ứng viên 171 / 61) và đã chạy mốc 6 cấu hình trước khi kết luận về reciprocal, chống dồn, slot khám phá. Kết quả và lý do: [13, mục 13.1, 13.3](./13-tinh-chinh-sau-danh-gia.md).

> 💡 Bộ đánh giá đã được lập trình trong `scripts/recommendation_eval/` (Python, seed cố định, chạy lại được) và kết quả nằm ở `ket-qua/`. Mục này ghi những chỗ cách làm thực tế khác hoặc cụ thể hơn thiết kế ở trên, để khi viết báo cáo không nói quá điều đã làm.

**Các file và thứ tự chạy:**

| Bước | Lệnh | Việc |
|---|---|---|
| 26a | `world.py` | Sinh thuộc tính ẩn và văn bản → `out/world.json` |
| 26a | `load.py` | Nạp vào database dev (email `eval.*@ptutor.local`), kèm 4 tuần lịch sử; `--disable` để ẩn lại |
| 26a | `run.py prepare` | Chạy backend thật: embedding, A1, điểm Q; rồi mô phỏng duyệt danh sách (H1) qua API |
| 26b | `run.py search all` | Chạy backend theo từng cấu hình 9.4 bằng thuộc tính cấu hình, tìm qua API thật → `out/runs/` |
| 26b | `evaluate.py` | Chỉ số 9.5, Wilcoxon + bootstrap 9.6, bảng 9.7, biểu đồ |
| 26c | `sensitivity.py` | Độ nhạy 9.9.1 (tornado) |
| 26c | `learning.py` | Thí nghiệm học 9.9.3–9.9.4; `linear_bayes.py --fixture` + `LinearBayesParityTest` đối chiếu bản Python với bản Java |

**Khác hoặc cụ thể hơn thiết kế:**
- **Văn bản hiển thị viết theo mẫu**, không do LLM viết (người dùng chọn 20/10/2026, để không tốn phí): mỗi tag có
  nhiều cách diễn đạt, có viết tắt, lỗi chính tả, khoảng 15% gia sư hồ sơ sơ sài. Văn mẫu đều đặn hơn văn người viết
  nên phần đọc chữ (embedding, rerank, A1) có thể được điểm **cao hơn thực tế** — nêu trong 9.8. A1 vẫn chạy thật
  (LLM) trên văn bản này.
- **Nạp bằng SQL, không qua API:** một tin chỉ mở được sau phí VNPay và bước duyệt của admin, script không đi qua được.
  Tài khoản, hồ sơ, tin đăng và 4 tuần lịch sử (đơn, quyết định, hợp đồng, đánh giá, khiếu nại) chèn bằng SQL; mọi
  phần phía sau chạy đúng luồng thật (worker embedding và A1, job tổng hợp và điểm Q, tìm kiếm và sự kiện qua API).
- **Dữ liệu nằm chung database dev** (người dùng chọn): kết quả chỉ tính trên ứng viên của thị trường mô phỏng; ứng
  viên khác (demo, dữ liệu thật) bị bỏ khỏi danh sách trước khi chấm. Sao lưu trước khi nạp:
  `D:\DATN\_backup\ptutor_before_eval_20261020.dump`.
- **Quy mô:** 120 gia sư, 202 tin dạy, 60 học sinh, 32 tin học; 2–4 truy vấn mỗi học sinh, 1–2 mỗi gia sư (332 truy
  vấn), chia đôi hiệu chỉnh / kiểm tra. Không dùng câu tự nhiên qua A2 (tránh thêm phí LLM): truy vấn "câu" chỉ đưa
  `semanticText`.
- **Cấu hình bật/tắt bằng thuộc tính sẵn có** (`reciprocal.enabled`, `willingness.congestion-enabled`,
  `rerank.enabled`, `exploration.enabled`, `history.enabled`) và một cờ mới `enrichment.use-llm-tags` cho "− A1" (bỏ
  tag do LLM gắn khi xếp hạng; văn bản embedding và rerank vẫn chứa tag). Mọi cấu hình dùng trọng số prior
  (`learning.enabled = false`) và ghi 200 ứng viên mỗi log.
- **V1 dựng lại từ mô tả** (bộ tài liệu cũ không có trong repo): tổng tuyến tính 7 đặc trưng phù hợp (thiếu = 0) và
  Q (0,85 / 0,15), không cổng, không b, xếp lại trên các ứng viên mà V2-a đã ghi log. Không dùng embedding hồ sơ cũ.
- **Mô phỏng thị trường** dùng truy vấn "đủ tiêu chí" của học sinh trên nửa kiểm tra, 200 lần lặp thứ tự ngẫu nhiên.
  MS@10 tính theo kỳ vọng (`rel ≥ 2` × P(nhận)) thay vì rút thăm, cho đỡ nhiễu.
- **Độ nhạy chạy offline** trên đặc trưng trong log (`scoring.py` tính lại đúng công thức 06). Các tham số không nằm
  trong log — kernel của đặc trưng, tham số phạt khiếu nại bên trong Q — cần chạy lại backend nên không có trong
  tornado.
- **Thí nghiệm học:** chỉ học a (`A_S2T`) và b (`B_S2T`) phía học sinh tìm gia sư. Phần thưởng theo mức hành động
  (xem 0,3 / tym 0,6 / gửi đơn 1,0), mẫu lấy tới vị trí tương tác thấp nhất + 2 như `LearningDataRepository`; mỗi
  100 lần tìm cập nhật một lần, mẫu Thompson theo (người tìm, "ngày" = lô 100).
