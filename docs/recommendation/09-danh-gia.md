# 9. Đánh giá (D9)

Mục tiêu: trả lời được câu hỏi "làm sao biết thuật toán mới tốt hơn?" bằng số liệu, khi chưa có
người dùng thật.

## 9.1. Ý tưởng chính: dữ liệu mô phỏng có "đáp án ẩn"

Nếu chỉ cho LLM viết hồ sơ rồi lại cho LLM chấm, kết quả dễ bị vòng lặp (LLM chấm cái LLM tạo).
Vì vậy dữ liệu được sinh theo **hai lớp**:

1. **Thuộc tính ẩn** (sinh bằng code, có kiểm soát): nhu cầu thật, phong cách, trình độ, ngân
   sách, lịch, quận; với gia sư thêm **tiêu chí nhận học sinh** (khoảng lớp ưa thích, giá tối
   thiểu chấp nhận, đối tượng ưa thích, số chỗ).
2. **Văn bản hiển thị** (LLM viết từ thuộc tính ẩn): hồ sơ, tin đăng, lịch dạng chữ — với độ chi
   tiết khác nhau, có cả hồ sơ sơ sài, viết tắt, lỗi chính tả, giống người dùng thật.

Hệ thống đề xuất **chỉ nhìn thấy văn bản và dữ liệu nhập** như một người dùng thật. Đáp án đúng
được tính từ **thuộc tính ẩn** — khách quan và không phụ thuộc LLM.

## 9.2. Bộ dữ liệu

| Thành phần | Quy mô |
|---|---|
| Môn | Toán, Tiếng Anh, Vật lý |
| Lớp | 6–12 |
| Khu vực | Hà Nội, TP.HCM; mỗi nơi 6 quận |
| Gia sư | 120, mỗi người 1–2 tin dạy (≈ 180 tin) |
| Học sinh | 60, mỗi người 1–2 tin học (≈ 90 tin) |
| Lịch sử nộp đơn mô phỏng | 4 tuần, để `b_hist` và chống dồn có dữ liệu |

Một phần gia sư có lịch sử dày (nhiều quyết định), một phần là gia sư mới — để đo được cả trường
hợp khởi đầu lạnh và slot khám phá.

Bộ truy vấn chia đôi: **50% để hiệu chỉnh** tham số (ngưỡng cosine, τ, trọng số) và **50% để
kiểm tra**. Số liệu báo cáo chỉ lấy trên phần kiểm tra.

## 9.3. Đáp án

**Độ phù hợp thật** `rel(s, t) ∈ {0, 1, 2, 3}`, tính từ thuộc tính ẩn:
- 0: lệch lịch hoàn toàn, hoặc vượt ngân sách > 30%, hoặc không phủ nhu cầu chính;
- 1–3: theo mức phủ nhu cầu, phong cách, đối tượng (công thức cố định, công bố trong báo cáo).

**Mô phỏng quyết định của gia sư:**

```
P(nhận) = sigmoid(β0 + β1·[lớp trong khoảng ưa thích] + β2·[giá ≥ giá tối thiểu]
                  + β3·[đối tượng ưa thích] + β4·tỉ lệ trùng lịch)
```

và gia sư không nhận quá số chỗ còn lại (ai nộp trước được xét trước).

**Nhãn từ LLM đóng vai học sinh** (bổ sung): với 30 truy vấn, gộp top-10 của mọi cấu hình, cho LLM
đóng vai học sinh đọc văn bản và chấm 0–3 theo rubric. Nhóm tự chấm lại 20% số cặp; báo cáo hệ số
đồng thuận **Cohen's kappa (có trọng số)** giữa người và LLM, và tương quan giữa nhãn LLM với
`rel` từ thuộc tính ẩn.

## 9.4. Các cấu hình so sánh (ablation)

| Cấu hình | Thành phần |
|---|---|
| **V1** (baseline) | Tổng tuyến tính 8 thành phần như tài liệu `04-thuat-toan-scoring.md` trong bộ cũ, embedding hồ sơ |
| **V2-a** | Đặc trưng mới + embedding theo tin đăng + cấu trúc nhân/gate, chỉ dùng a |
| **V2-R** | + reciprocal (b không có chống dồn) |
| **V2-RC** | + chống dồn (`b_cap`) |
| **V2-RCX** | + cross-encoder rerank |
| **V2-full** | + slot khám phá |
| **V2-full − A1** | Như V2-full nhưng không có dữ liệu chuẩn hoá từ A1 |

## 9.5. Chỉ số

### Chất lượng xếp hạng (mỗi truy vấn, lấy trung bình)

| Chỉ số | Ý nghĩa |
|---|---|
| **NDCG@10** | Thứ hạng có đưa ứng viên phù hợp lên đầu không (dùng `rel` 0–3) |
| **Precision@5** | Tỉ lệ ứng viên có `rel ≥ 2` trong top-5 |
| **MRR** | Vị trí ứng viên phù hợp đầu tiên |
| **EA@10** (Expected Acceptance) | Trung bình `P(nhận)` của top-10 — đo phần reciprocal |
| **MS@10** (Mutual Success) | Tỉ lệ top-10 vừa `rel ≥ 2` vừa được nhận (mô phỏng) |

### Mô phỏng cả thị trường (đo tác dụng chống dồn)

Mỗi học sinh nộp đơn lần lượt vào top-3 của mình; gia sư quyết định theo mô phỏng, có giới hạn chỗ.
Đo:
- **Tỉ lệ ghép thành công**: % học sinh được ít nhất một gia sư nhận;
- **Số lần bị từ chối trung bình** trên mỗi học sinh;
- **Độ phù hợp trung bình** của các ghép đôi thành công.

Đây là chỉ số thể hiện rõ nhất giá trị của R1 + B4: một hệ thống chỉ tối ưu a sẽ dồn mọi học sinh
vào vài gia sư hàng đầu và tỉ lệ ghép thấp.

### Công bằng hiển thị (R6)

- **Gini** của số lượt hiển thị giữa các tin dạy, trên toàn bộ truy vấn (càng thấp càng đều);
- **Coverage@20**: % tin dạy xuất hiện ở ít nhất một top-20;
- Lượt hiển thị trung bình của nhóm **gia sư mới**.

### Thành phần LLM

| Thành phần | Bộ kiểm tra | Chỉ số |
|---|---|---|
| A1 — tag | 60 văn bản nhóm tự gán nhãn | Micro-F1 theo tag |
| A1 — lịch | 60 câu lịch dạng chữ | Độ chính xác theo ngày; IoU khung giờ |
| A2 — tách câu | 80 câu nhóm tự viết (nhiều cách diễn đạt, viết tắt, lỗi chính tả) | Precision/Recall/F1 theo từng trường; % câu tách đúng toàn bộ |

### Hiệu năng

p50 / p95 độ trễ từng pha (lọc, chấm điểm, rerank, tách câu, nới tiêu chí) đo trên **laptop phát
triển** và trên **VPS triển khai**; tỉ lệ rerank bị timeout.

## 9.6. Quy trình

1. Sinh thuộc tính ẩn (script Python, seed cố định để tái lập được).
2. LLM viết văn bản → nạp vào database qua chính API của hệ thống (để A1, embedding chạy đúng luồng
   thật).
3. Chạy mô phỏng 4 tuần nộp đơn để tạo lịch sử.
4. Hiệu chỉnh tham số trên nửa truy vấn hiệu chỉnh.
5. Chạy mọi cấu hình trên nửa kiểm tra, xuất kết quả ra CSV.
6. So sánh từng cặp cấu hình theo từng truy vấn bằng **Wilcoxon signed-rank**, kèm khoảng tin cậy
   95% bằng **bootstrap**.

## 9.7. Mẫu bảng kết quả trong báo cáo

| Cấu hình | NDCG@10 | P@5 | EA@10 | MS@10 | Tỉ lệ ghép | Từ chối/HS | Gini | p95 (ms) |
|---|---|---|---|---|---|---|---|---|
| V1 | | | | | | | | |
| V2-a | | | | | | | | |
| V2-R | | | | | | | | |
| V2-RC | | | | | | | | |
| V2-RCX | | | | | | | | |
| V2-full | | | | | | | | |
| V2-full − A1 | | | | | | | | |

Kỳ vọng (giả thuyết, cần kiểm chứng): V2-a tăng NDCG so với V1; V2-R và V2-RC tăng EA@10, MS@10
và tỉ lệ ghép, giảm số lần bị từ chối; V2-RCX tăng NDCG/P@5; V2-full giảm Gini mà NDCG giảm không
đáng kể.

## 9.8. Hạn chế cần nêu trong báo cáo

- Dữ liệu là mô phỏng; hành vi người thật có thể khác. Thuộc tính ẩn và mô hình quyết định do nhóm
  thiết kế nên có thể thiên về giả định của nhóm.
- Nhãn LLM chỉ là bổ sung; kết luận chính dựa trên đáp án từ thuộc tính ẩn.
- Khi có người dùng thật, đánh giá tiếp bằng log: CTR theo hạng (lưu ý thiên lệch vị trí), tỉ lệ
  đơn được chấp nhận, tỉ lệ thành hợp đồng.
