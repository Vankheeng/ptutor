# Kết quả đánh giá mô phỏng (21/10/2026)

> 💡 Số liệu của chương đánh giá (thiết kế: [09](../09-danh-gia.md); cách làm thực tế: 09 mục 9.10). Sinh bằng `scripts/recommendation_eval/` với seed cố định; chạy lại cho ra cùng dữ liệu, chỉ thời gian chạy và phần ngẫu nhiên của backend (slot khám phá, rerank hết giờ) có thể khác chút ít. Mọi số dưới đây là trên **dữ liệu mô phỏng**, văn bản viết theo mẫu (9.8).

Bộ dữ liệu: 120 gia sư (202 tin dạy), 60 học sinh (32 tin học), 4 tuần lịch sử (292 đơn, 177 hợp đồng, 19 khiếu nại),
352 sự kiện duyệt danh sách, 59 hồ sơ sở thích. 332 truy vấn, nửa kiểm tra có 79 truy vấn học sinh → gia sư có ít nhất
một ứng viên phù hợp. Chiều gia sư → học viên quá mỏng (32 tin học, trung bình 1,6 kết quả) nên chỉ ghi ở cột riêng
trong CSV, không kết luận.

## Bảng 9.7 — học sinh tìm gia sư, nửa kiểm tra

| Cấu hình | NDCG@10 | P@5 | MRR | EA@10 | MS@10 | Bị từ chối / HS | Gini | Coverage@20 | p95 (ms) |
|---|---|---|---|---|---|---|---|---|---|
| V1 (dựng lại) | 0,404 | 0,192 | 0,438 | 0,841 | 0,125 | 0,26 | 0,496 | 0,866 | — |
| V2-a | 0,403 | 0,195 | 0,424 | 0,841 | 0,126 | 0,26 | 0,496 | 0,871 | 635 |
| V2-R | 0,397 | 0,190 | 0,413 | 0,842 | 0,109 | 0,20 | 0,500 | 0,871 | 437 |
| V2-RC | 0,392 | 0,185 | 0,381 | 0,840 | 0,108 | 0,23 | 0,504 | 0,866 | 433 |
| V2-RCX | 0,387 | 0,190 | 0,381 | 0,840 | 0,108 | 0,23 | 0,504 | 0,866 | 4 848 |
| V2-full | 0,385 | 0,175 | 0,370 | 0,839 | 0,106 | 0,22 | 0,504 | 0,866 | 4 710 |
| V2-full − H1 | 0,389 | 0,180 | 0,380 | 0,840 | 0,109 | 0,23 | 0,505 | 0,856 | 4 636 |
| V2-full − A1 | 0,363 | 0,147 | 0,286 | 0,833 | 0,097 | 0,32 | 0,504 | 0,866 | 5 355 |

Tỉ lệ ghép thành công 96–97% ở mọi cấu hình (thị trường dư chỗ: 202 tin × 1–3 chỗ cho 60 học sinh), nên số lần bị từ
chối là chỉ số phân biệt được. Chi tiết: `9.7-bang-ket-qua.csv`, biểu đồ `9.7-so-sanh-cau-hinh.png`.

## Kiểm định 9.6 (Wilcoxon theo truy vấn, khoảng tin cậy 95% bootstrap của chênh lệch trung bình)

| So sánh | Chỉ số | Chênh lệch | KTC 95% | p |
|---|---|---|---|---|
| V2-a → V2-R | MS@10 | −0,017 | [−0,027; −0,008] | 0,001 |
| V2-full − A1 → V2-full | NDCG@10 | +0,023 | [+0,005; +0,040] | 0,006 |
| V2-full − A1 → V2-full | EA@10 | +0,006 | [−0,001; +0,013] | 0,017 |
| Các cặp khác | NDCG@10 | trong khoảng ±0,006 | đều chứa 0 | > 0,1 (trừ V2-RCX → V2-full: p = 0,015 nhưng KTC chứa 0) |

Đầy đủ: `9.6-kiem-dinh.csv`.

**Đọc kết quả (so với giả thuyết 9.7):**
- **A1 có tác dụng rõ nhất:** bỏ tag do LLM gắn làm NDCG@10 giảm 0,023, P@5 từ 0,175 xuống 0,147, số lần bị từ chối
  tăng 0,22 → 0,32.
- **Hai chiều (V2-R):** giảm số lần bị từ chối 0,26 → 0,20 như giả thuyết, nhưng MS@10 giảm có ý nghĩa — xếp gia sư dễ
  nhận lên trên đổi lấy độ phù hợp. EA@10 gần như bão hoà (0,84) vì P(nhận) ẩn của dữ liệu cao; giả thuyết "V2-R tăng
  EA@10" **không được xác nhận** trên bộ dữ liệu này.
- **Chống dồn, rerank, khám phá, H1:** không thay đổi chất lượng có ý nghĩa. Bộ lọc cứng chỉ để lại 10–20 ứng viên mỗi
  truy vấn (≈ 4 người thực sự phù hợp), còn ít chỗ cho xếp hạng tạo khác biệt. Rerank không làm tăng NDCG mà làm p95
  lên ~4,8 s.
- **V1** là bản dựng lại từ mô tả trên cùng ứng viên của V2-a, nên gần bằng V2-a; không dùng để kết luận V2 hơn V1.

## Độ nhạy 9.9.1 — mỗi tham số ×0,5 / ×0,75 / ×1,25 / ×1,5

Mọi tham số: |ΔNDCG@10| ≤ 0,009, Kendall τ của top-10 ≥ 0,89. Nhạy nhất: `a.price` (−0,0085…+0,0065), `b.hist`
(τ ≥ 0,897), `a.need` (τ ≥ 0,892). Kết luận: trọng số tự đặt **không phải "điểm may mắn"**; kết quả ổn định. Tham số
phạt khiếu nại và kernel không có trong phép thử này (9.10). Chi tiết: `9.9.1-do-nhay.csv`, `9.9.1-tornado.png`.

## Thí nghiệm học L1 — 9.9.3, 9.9.4 (3 000 lần tìm × 10 seed)

| Cấu hình | Regret cuối (± sd) | ‖w − w*‖₁ cuối | Trọng số cuối (sem, need, price, style, sched, area, segment) |
|---|---|---|---|
| Fixed | 1 280 (± 9) | 0,300 | 0,30 0,20 0,20 0,10 0,10 0,05 0,05 |
| LinTS-prior | 934 (± 16) | 0,289 | 0,13 0,27 0,27 0,09 0,14 0,02 0,08 |
| LinTS-bad | 1 137 (± 18) | 0,409 | 0,06 0,24 0,24 0,10 0,16 0,06 0,14 |
| Greedy | 933 (± 15) | 0,279 | 0,13 0,27 0,27 0,09 0,14 0,02 0,08 |
| Oracle | 0 | 0 | 0,15 0,20 0,35 0,10 0,10 0,05 0,05 (w*) |

- **L1 giảm regret 27% so với trọng số cố định** và đẩy `sem` xuống, `price` lên đúng hướng của w*.
- **Trọng số không hội tụ về w*** (sai số ≈ 0,28, không seed nào xuống dưới 0,05): mô hình hồi quy tuyến tính trên
  phần thưởng phi tuyến (hấp dẫn dạng sigmoid × mức hành động) và mẫu chỉ lấy ở top-10 nên hệ số bị lệch (`need` bị đẩy
  lên thay vì giữ nguyên); thêm biên kẹp (xem phát hiện dưới).
- **Greedy ≈ LinTS-prior**: với 106 truy vấn lặp lại, khám phá Thompson không mang thêm lợi ích; LinTS-bad không bắt kịp
  trong 3 000 lần tìm.
- **Phía b:** AUC dự đoán nhận 0,68–0,71 (Oracle 0,77).
- **Độ nhạy L1** (κ ∈ {50, 200, 800}, ρ ∈ {0; 0,5; 1; 2}, có / không IPW): regret 928–984, sai số 0,25–0,32 — không
  cấu hình nào khác biệt rõ. Chi tiết: `9.9.4-*.csv`, `9.9.4-regret-va-sai-so.png`.
- **Đối chiếu Python ↔ Java:** `LinearBayesParityTest` (6 trường hợp, sai lệch ≤ 1e-9) qua.

**Phát hiện về cơ chế an toàn (12 mục 12.3):**
1. `project` kẹp **θ thô** vào `[0,5; 2] × prior` trước khi chuẩn hoá. θ của hồi quy không cùng thang với trọng số (tổng
   ≠ 1), nên biên kẹp bị chạm ở **mọi** lần cập nhật — biên thực tế khác với ý định "mỗi trọng số trong 0,5–2 lần prior".
2. Giới hạn bước `max-step = 0,05` áp **trước** khi chuẩn hoá, nên bước thật sau chuẩn hoá lên tới 0,085.
Hai điểm này có trong bản Java (bản Python giống hệt, đã đối chiếu).
