# Kết quả đánh giá mô phỏng (21/10/2026)

> 💡 Số liệu của chương đánh giá (thiết kế: [09](../09-danh-gia.md); cách làm thực tế: 09 mục 9.10). Sinh bằng `scripts/recommendation_eval/` với seed cố định; chạy lại cho ra cùng dữ liệu, chỉ thời gian chạy và phần ngẫu nhiên của backend (slot khám phá, rerank hết giờ) có thể khác chút ít. Mọi số dưới đây là trên **dữ liệu mô phỏng**, văn bản viết theo mẫu (9.8).

Bộ dữ liệu: 120 gia sư (202 tin dạy), 60 học sinh (32 tin học), 4 tuần lịch sử (292 đơn, 177 hợp đồng, 19 khiếu nại),
352 sự kiện duyệt danh sách, 59 hồ sơ sở thích. 332 truy vấn, nửa kiểm tra có 79 truy vấn học sinh → gia sư có ít nhất
một ứng viên phù hợp. Chiều gia sư → học viên quá mỏng (32 tin học, trung bình 1,6 kết quả) nên chỉ ghi ở cột riêng
trong CSV, không kết luận.

## Bảng 9.7 — học sinh tìm gia sư, nửa kiểm tra

Rerank theo tham số từ 21/10/2026 (`top-k` 10, `timeout-ms` 1 500). Số của lượt chạy đầu (`top-k` 20, 4 000 ms) ở
cuối mục.

| Cấu hình | NDCG@10 | P@5 | MRR | EA@10 | MS@10 | Bị từ chối / HS | Gini | Coverage@20 | p95 (ms) |
|---|---|---|---|---|---|---|---|---|---|
| V1 (dựng lại) | 0,404 | 0,192 | 0,438 | 0,841 | 0,125 | 0,26 | 0,496 | 0,866 | — |
| V2-a | 0,403 | 0,195 | 0,424 | 0,841 | 0,126 | 0,26 | 0,496 | 0,871 | 635 |
| V2-R | 0,397 | 0,190 | 0,413 | 0,842 | 0,109 | 0,20 | 0,500 | 0,871 | 437 |
| V2-RC | 0,392 | 0,185 | 0,381 | 0,840 | 0,108 | 0,23 | 0,504 | 0,866 | 433 |
| V2-RCX | 0,387 | 0,182 | 0,380 | 0,840 | 0,108 | 0,22 | 0,504 | 0,866 | 926 |
| V2-full | 0,389 | 0,177 | 0,370 | 0,839 | 0,107 | 0,21 | 0,505 | 0,861 | 856 |
| V2-full − H1 | 0,388 | 0,180 | 0,380 | 0,840 | 0,109 | 0,23 | 0,504 | 0,866 | 805 |
| V2-full − A1 | 0,366 | 0,149 | 0,293 | 0,833 | 0,098 | 0,29 | 0,504 | 0,871 | 2 723 |

p95 là thời gian trong backend (cả hai chiều). Các lượt có rerank được lợi từ cache của lượt trước nên lạc quan; riêng
"− A1" đổi văn bản nhu cầu nên phải chấm mới (p95 2,7 s). Tỉ lệ ghép thành công 96–97% ở mọi cấu hình (thị trường dư
chỗ: 202 tin × 1–3 chỗ cho 60 học sinh), nên số lần bị từ chối là chỉ số phân biệt được. Chi tiết:
`9.7-bang-ket-qua.csv`, biểu đồ `9.7-so-sanh-cau-hinh.png`.

**Lượt đầu (rerank `top-k` 20, 4 000 ms):** V2-RCX NDCG 0,387 / p95 4 848 ms; V2-full 0,385 / 4 710 ms; − H1 0,389 /
4 636 ms; − A1 0,363 / 5 355 ms. Pha rerank chiều học sinh p50 3,8 s, chạm trần 4 s ở p95, 86 lần hết giờ / 332 lần
tìm — lý do đổi tham số (10, mục 10.2).

## Kiểm định 9.6 (Wilcoxon theo truy vấn, khoảng tin cậy 95% bootstrap của chênh lệch trung bình)

| So sánh | Chỉ số | Chênh lệch | KTC 95% | p |
|---|---|---|---|---|
| V2-a → V2-R | MS@10 | −0,017 | [−0,027; −0,008] | 0,001 |
| V2-full − A1 → V2-full | NDCG@10 | +0,023 | [+0,007; +0,040] | 0,002 |
| V2-full − A1 → V2-full | EA@10 | +0,006 | [+0,000; +0,012] | 0,011 |
| V2-full − A1 → V2-full | MS@10 | +0,009 | [+0,001; +0,017] | 0,032 |
| V2-RC → V2-RCX | NDCG@10 | −0,005 | [−0,015; +0,003] | 0,022 (KTC chứa 0) |
| Các cặp khác | NDCG@10 | trong khoảng ±0,006 | đều chứa 0 | > 0,1 |

Đầy đủ: `9.6-kiem-dinh.csv`.

**Đọc kết quả (so với giả thuyết 9.7):**
- **A1 có tác dụng rõ nhất:** bỏ tag do LLM gắn làm NDCG@10 giảm 0,023, P@5 từ 0,177 xuống 0,149, MRR từ 0,370 xuống
  0,293, số lần bị từ chối tăng 0,21 → 0,29.
- **Hai chiều (V2-R):** giảm số lần bị từ chối 0,26 → 0,20 như giả thuyết, nhưng MS@10 giảm có ý nghĩa — xếp gia sư dễ
  nhận lên trên đổi lấy độ phù hợp. EA@10 gần như bão hoà (0,84) vì P(nhận) ẩn của dữ liệu cao; giả thuyết "V2-R tăng
  EA@10" **không được xác nhận** trên bộ dữ liệu này.
- **Chống dồn, rerank, khám phá, H1:** không thay đổi chất lượng có ý nghĩa. Bộ lọc cứng chỉ để lại 10–20 ứng viên mỗi
  truy vấn (≈ 4 người thực sự phù hợp), còn ít chỗ cho xếp hạng tạo khác biệt. Rerank có xu hướng hạ NDCG nhẹ (Wilcoxon
  p = 0,02 nhưng KTC của trung bình chứa 0) — văn mẫu đều đặn, cross-encoder không thêm thông tin.
- **V1** là bản dựng lại từ mô tả trên cùng ứng viên của V2-a, nên gần bằng V2-a; không dùng để kết luận V2 hơn V1.

## Độ nhạy 9.9.1 — mỗi tham số ×0,5 / ×0,75 / ×1,25 / ×1,5 (trên V2-full)

Mọi tham số: |ΔNDCG@10| ≤ 0,009, Kendall τ của top-10 ≥ 0,88. Nhạy nhất: `a.price` (−0,008…+0,006), `b.hist`
(−0,008…+0,005, τ ≥ 0,889), `a.need` (τ ≥ 0,885). Kết luận: trọng số tự đặt **không phải "điểm may mắn"**; kết quả
ổn định. Tham số phạt khiếu nại và kernel không có trong phép thử này (9.10). Chi tiết: `9.9.1-do-nhay.csv`,
`9.9.1-tornado.png`.

## Thí nghiệm học L1 — 9.9.3, 9.9.4 (3 000 lần tìm × 10 seed)

Chạy với `project` đã sửa (21/10/2026, 12 mục 12.3). Kết quả của bản `project` cũ ghi ở cuối mục.

| Cấu hình | Regret cuối (± sd) | ‖w − w*‖₁ cuối | Trọng số cuối (sem, need, price, style, sched, area, segment) |
|---|---|---|---|
| Fixed | 1 280 (± 9) | 0,300 | 0,30 0,20 0,20 0,10 0,10 0,05 0,05 |
| LinTS-prior | 954 (± 14) | 0,218 | 0,15 0,28 0,32 0,06 0,09 0,03 0,08 |
| LinTS-bad | 1 032 (± 26) | 0,342 | 0,07 0,29 0,29 0,07 0,12 0,07 0,10 |
| Greedy | 955 (± 20) | 0,217 | 0,15 0,28 0,32 0,05 0,09 0,03 0,08 |
| Oracle | 0 | 0 | 0,15 0,20 0,35 0,10 0,10 0,05 0,05 (w*) |

- **L1 giảm regret 25% so với trọng số cố định**; `sem` về đúng biên dưới 0,15 (= w*), `price` lên 0,32 (w* = 0,35).
- **Chưa hội tụ hẳn về w*** (sai số 0,22; không seed nào dưới 0,05): `need` bị đẩy lên 0,28 thay vì giữ 0,20 — mô hình
  tuyến tính trên phần thưởng phi tuyến (hấp dẫn dạng sigmoid × mức hành động) và chỉ có mẫu ở top-10 nên hệ số lệch.
- **Greedy ≈ LinTS-prior**: với 106 truy vấn lặp lại, khám phá Thompson không thêm lợi ích; LinTS-bad thu hẹp khoảng
  cách nhưng chưa bắt kịp trong 3 000 lần tìm.
- **An toàn:** bước lớn nhất 0,050 (= `max-step`), mọi trọng số trong biên `[0,5; 2] × prior` — đúng trên trọng số cuối.
- **Phía b:** AUC dự đoán nhận 0,68–0,70 (Oracle 0,77).
- **Độ nhạy L1** (κ ∈ {50, 200, 800}, ρ ∈ {0; 0,5; 1; 2}, có / không IPW): `9.9.4-do-nhay-L1.csv`; biểu đồ
  `9.9.4-regret-va-sai-so.png`.
- **Đối chiếu Python ↔ Java:** `LinearBayesParityTest` (6 trường hợp, sai lệch ≤ 1e-9) qua.

**Bản `project` cũ (trước 21/10/2026)** kẹp θ thô rồi chuẩn hoá: regret LinTS-prior 934, LinTS-bad 1 137, sai số
0,289 / 0,409; biên kẹp chạm ở mọi lần cập nhật và bước thật tới 0,085 > `max-step`. Đây là phát hiện dẫn tới việc sửa.
