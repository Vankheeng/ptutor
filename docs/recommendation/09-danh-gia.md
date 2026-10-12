# 9. Đánh giá (D9)

Mục tiêu: trả lời bằng số liệu câu hỏi "làm sao biết thuật toán mới tốt hơn?" khi chưa có người
dùng thật.

## 9.1. Ý tưởng chính: dữ liệu mô phỏng có "đáp án ẩn"

Dữ liệu được sinh theo **hai lớp**:

1. **Thuộc tính ẩn** (sinh bằng code, có kiểm soát): nhu cầu thật, phong cách, trình độ, ngân sách,
   lịch, khu vực; với gia sư thêm **tiêu chí nhận học sinh** (khoảng lớp ưa thích, giá tối thiểu,
   đối tượng ưa thích, số chỗ).
2. **Văn bản hiển thị** (LLM viết từ thuộc tính ẩn): hồ sơ, tin đăng, lịch dạng chữ — độ chi tiết
   khác nhau, có cả hồ sơ sơ sài, viết tắt, lỗi chính tả.

Hệ thống đề xuất **chỉ thấy văn bản và tiêu chí nhập**, như với người dùng thật. Đáp án tính từ
**thuộc tính ẩn** — khách quan, tránh vòng lặp "LLM tự chấm dữ liệu LLM tạo".

## 9.2. Bộ dữ liệu

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

**Độ phù hợp thật** `rel ∈ {0, 1, 2, 3}` từ thuộc tính ẩn (0: lệch lịch hoàn toàn / vượt ngân sách
> 30% / không phủ nhu cầu chính; 1–3 theo mức phủ nhu cầu, phong cách, đối tượng — công thức cố
định, công bố trong báo cáo).

**Quyết định của gia sư:**

```
P(nhận) = sigmoid(β0 + β1·[lớp trong khoảng ưa thích] + β2·[giá ≥ giá tối thiểu]
                  + β3·[đối tượng ưa thích] + β4·tỉ lệ trùng lịch)
```

không vượt số chỗ còn lại (ai nộp trước được xét trước).

**Hành vi duyệt danh sách** (để tạo tín hiệu cho H1, B5): mô hình click theo vị trí —
`P(click) = P(nhìn tới vị trí k) × P(hấp dẫn | rel, sở thích ẩn)`, với `P(nhìn tới vị trí k)` giảm dần
theo k. Học sinh ảo có **sở thích ẩn** (ví dụ thích kiểu luyện đề) không ghi trong hồ sơ — để kiểm tra
H1 có "học" ra được không.

**Nhãn LLM đóng vai học sinh** (bổ sung): 30 truy vấn, gộp top-10 của mọi cấu hình, LLM chấm 0–3 theo
rubric; nhóm chấm lại 20%; báo cáo Cohen's kappa có trọng số và tương quan với `rel` ẩn.

## 9.4. Các cấu hình so sánh

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

## 9.5. Chỉ số

### Chất lượng xếp hạng (trung bình theo truy vấn)

| Chỉ số | Ý nghĩa |
|---|---|
| **NDCG@10** | Có đưa ứng viên phù hợp lên đầu không (`rel` 0–3) |
| **Precision@5** | Tỉ lệ `rel ≥ 2` trong top-5 |
| **MRR** | Vị trí ứng viên phù hợp đầu tiên |
| **EA@10** | Trung bình `P(nhận)` của top-10 (phần reciprocal) |
| **MS@10** | Tỉ lệ top-10 vừa `rel ≥ 2` vừa được nhận (mô phỏng) |

### Mô phỏng cả thị trường (tác dụng của R1 + B4)

Mỗi học sinh nộp đơn lần lượt vào top-3; gia sư quyết định theo mô phỏng, có giới hạn chỗ. Đo
**tỉ lệ ghép thành công**, **số lần bị từ chối trung bình**, **độ phù hợp trung bình** của ghép đôi.

### Thích ứng theo hành vi (H1)

Chạy nhiều phiên liên tiếp cho cùng học sinh ảo: sau mỗi phiên cập nhật hồ sơ sở thích từ các click
mô phỏng. Đo **NDCG@10 theo số phiên** (V2-full so với V2-full − H1) và mức trùng giữa tag ưa thích
học được với **sở thích ẩn**.

### Công bằng hiển thị (R6)

**Gini** lượt hiển thị giữa các tin dạy; **Coverage@20**; lượt hiển thị trung bình của gia sư mới.

### Thành phần LLM

| Thành phần | Bộ kiểm tra | Chỉ số |
|---|---|---|
| A1 — tag | 60 văn bản nhóm tự gán nhãn | Micro-F1 |
| A1 — lịch | 60 câu lịch dạng chữ | Độ chính xác theo ngày; IoU khung giờ |
| A2 — tách câu | 80 câu nhóm tự viết | P/R/F1 theo từng ô bộ lọc; % câu điền đúng toàn bộ |

### Hiệu năng

p50/p95 từng pha trên laptop và VPS; tỉ lệ rerank timeout.

## 9.6. Quy trình

1. Sinh thuộc tính ẩn (Python, seed cố định).
2. LLM viết văn bản → nạp qua chính API của hệ thống (A1, embedding chạy đúng luồng thật).
3. Mô phỏng 4 tuần nộp đơn và duyệt danh sách để tạo lịch sử.
4. Hiệu chỉnh trọng số/ngưỡng trên nửa hiệu chỉnh (tìm lưới thô quanh giá trị khởi đầu).
5. Chạy mọi cấu hình trên nửa kiểm tra, xuất CSV.
6. So từng cặp cấu hình theo truy vấn bằng **Wilcoxon signed-rank**, khoảng tin cậy 95% bằng **bootstrap**.

## 9.7. Mẫu bảng kết quả

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

- Dữ liệu mô phỏng; hành vi người thật có thể khác. Thuộc tính ẩn, mô hình quyết định và mô hình click
  do nhóm thiết kế.
- Trọng số được hiệu chỉnh trên mô phỏng, chưa học từ dữ liệu thật (hướng phát triển — 10, mục 10.5).
- Nhãn LLM chỉ là bổ sung; kết luận chính dựa trên đáp án từ thuộc tính ẩn.
