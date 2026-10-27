# Kịch bản `crowded` — thị trường đông

> 💡 Ghi chú tinh chỉnh, chưa phải văn báo cáo. Số trên **dữ liệu mô phỏng**, sinh bằng `scripts/recommendation_eval/` (seed cố định). Lý do và quyết định: [13](../../13-tinh-chinh-sau-danh-gia.md); cách đọc chỉ số: 13 mục 13.0.

**Chạy gì.** Thế giới `crowded` (09 mục 9.3, 13 mục 13.3): 400 học sinh (360 tin học), b0 −3,1, `rel` +1 khi
`quality` ≥ 0,75 và −1 khi < 0,6, đánh giá sinh theo `quality`; A1 bằng prompt mới (13.6). Database `ptutor_eval` /
`ptutor_eval_seed`. Tìm kiếm: 1 380 truy vấn mỗi cấu hình, kiểm định trên 597 truy vấn S2T nửa kiểm tra (không lần
nào dùng vector hồ sơ), 93 truy vấn T2S. Thị trường: 8 vòng qua API thật (`market.py`). Rerank không chạy ở mọi lượt
(AI service không nạp model).

| File | Nội dung |
|---|---|
| `kiem-tra-mo-phong.csv` | Kiểm tra bộ mô phỏng trước khi đọc kết quả thuật toán (13.3): đạt cả |
| `9.7-bang-ket-qua.csv`, `9.6-kiem-dinh.csv`, `9.7-so-sanh-cau-hinh.png` | Tìm kiếm, mọi cấu hình đã chạy (Bước 38–40) |
| `market.csv` | Thị trường mốc Bước 38 (chưa dùng chung số ngẫu nhiên) |
| `market-t39.csv`, `t39-quet.csv`, `t39-kiem-dinh-thi-truong.csv` | Quét λ / α (Bước 39), thị trường dùng chung số ngẫu nhiên, so theo học sinh |
| `t40-crowded-quet.csv`, `t40-crowded-kiem-dinh-thi-truong.csv` | Tách Q và hai cổng chặn (Bước 40) |
| `9.9.4-*`, `9.9.5-drift.*` | Học trọng số L1 với truy vấn đa dạng và `drift` (Bước 41) |

**Kiểm tra bộ mô phỏng:** trung vị ứng viên S2T / T2S 171 / 61; P(nhận) trung bình 0,519; EA@10 Random 0,475; tương
quan Q–rel 0,203; Popularity hơn Random NDCG +0,182 và bị từ chối nhiều nhất (2,65 / HS).

## Tìm kiếm (S2T, 597 truy vấn)

`V2-RC-s0.5-g0.65` là **mặc định hiện tại** (chống dồn `ON_SHORTAGE` λ 0,5, reciprocal trung bình nhân α 0,65, rerank
tắt). `V2-R`, `V2-RC`, `V2-full` là mốc Bước 38 (trung bình điều hoà, chống dồn cũ).

| Cấu hình | NDCG@10 | MS@10 | EA@10 | Bị từ chối / HS (một lượt) | Gini | p95 (ms) |
|---|---|---|---|---|---|---|
| Random | 0,309 | 0,197 | 0,475 | 1,07 | 0,345 | — |
| Popularity | 0,491 | 0,356 | 0,466 | 2,65 | 0,887 | — |
| Semantic | 0,414 | 0,294 | 0,559 | 1,32 | 0,558 | — |
| V1 (thiết kế cũ) | 0,567 | 0,415 | 0,533 | 2,03 | 0,805 | — |
| V2-linear | 0,643 | 0,414 | 0,563 | 1,13 | 0,516 | — |
| V2-a | 0,661 | 0,418 | 0,557 | 1,37 | 0,543 | 355 |
| V2-R | 0,599 | 0,424 | 0,585 | 1,78 | 0,652 | 349 |
| V2-RC | 0,599 | 0,424 | 0,584 | 1,79 | 0,656 | 363 |
| V2-full | 0,580 | 0,408 | 0,578 | 1,83 | 0,659 | 369 |
| V2-RC-g0.5 | 0,597 | 0,424 | 0,583 | 1,79 | 0,662 | 353 |
| V2-RC-g0.65 | 0,625 | 0,428 | 0,583 | 1,61 | 0,621 | 352 |
| V2-RC-g0.8 | 0,643 | 0,426 | 0,576 | 1,45 | 0,586 | 354 |
| V2-RC-s0.5-g0.65 | 0,624 | 0,428 | 0,583 | 1,61 | 0,620 | 373 |
| V2-RC-noQ | 0,559 | 0,399 | 0,601 | 0,92 | 0,521 | 386 |
| V2-RC-noSchedGate | 0,624 | 0,428 | 0,583 | 1,61 | 0,620 | 374 |
| V2-RC-noRejectGate | 0,632 | 0,431 | 0,583 | 1,63 | 0,623 | 345 |

## Thị trường 8 vòng (Bước 39–40, dùng chung số ngẫu nhiên)

| Cấu hình | Bị từ chối / HS | Vòng chờ | Gini hiển thị | Gia sư mới | rel khi ghép |
|---|---|---|---|---|---|
| V2-a | 2,23 | 1,53 | 0,597 | 0,193 | 2,26 |
| V2-R-harm | 2,70 | 1,66 | 0,675 | 0,155 | 2,13 |
| V2-RC-old | 0,81 | 1,11 | 0,448 | 0,280 | 1,99 |
| V2-RC-s0.25 | 0,87 | 1,12 | 0,528 | 0,286 | 2,10 |
| V2-RC-s0.5 | 0,81 | 1,10 | 0,501 | 0,302 | 2,02 |
| V2-RC-s1 | 0,81 | 1,11 | 0,493 | 0,333 | 1,95 |
| V2-RC-g0.5 | 0,74 | 1,08 | 0,445 | 0,299 | 2,06 |
| V2-RC-g0.65 | 0,92 | 1,12 | 0,472 | 0,285 | 2,10 |
| V2-RC-g0.8 | 1,08 | 1,17 | 0,495 | 0,252 | 2,17 |
| V2-RC-s0.5-g0.65 | 0,85 | 1,13 | 0,513 | 0,297 | 2,14 |
| V2-RC-noQ | 0,83 | 1,10 | 0,512 | 0,312 | 2,11 |
| V2-RC-noSchedGate | 0,88 | 1,14 | 0,516 | 0,297 | 2,13 |
| V2-RC-noRejectGate | 0,89 | 1,11 | 0,510 | 0,290 | 2,17 |

`V2-R-harm` = reciprocal điều hoà, không chống dồn; `V2-RC-old` = chống dồn cũ + điều hoà; `s…` = λ của công thức mới
(điều hoà); `g…` = α (chống dồn cũ).

## Đọc kết quả

- **Chống dồn** là phần có tác dụng lớn nhất trong thị trường: V2-R → V2-RC cũ bị từ chối −1,90 / HS, vòng chờ −0,55
  (p < 0,001). Công thức mới không kém công thức cũ (từ chối ±0,00–0,06, KTC chứa 0), ghép người hợp hơn, Gini cao hơn
  ~0,04 (13.4). Trong tìm kiếm tĩnh không có tác dụng (lúc tìm chưa có đơn chờ).
- **Reciprocal đứng một mình làm từ chối tăng** (2,23 → 2,70): b đẩy học sinh vào gia sư nổi tiếng đã hết chỗ; luôn đi
  cùng chống dồn. α 0,65 so với điều hoà: NDCG +0,026 (p < 0,001), từ chối không tăng có ý nghĩa; α 0,8 làm từ chối
  tăng (13.5).
- **Q:** tắt Q → NDCG −0,065, nhưng bỏ gia sư nổi tiếng thì tắt Q lại tốt hơn (+0,014): lợi ích đến từ cách dựng
  kịch bản. Cổng lịch không có tác dụng; cổng từ chối hơi có hại trong mô phỏng. Giữ cả ba (13.8).
- **L1:** mặc định mới (κ 50, ρ 0,5, γ 0,99) regret 1 311 so với cố định 1 658 (−21 %, 10/10 hạt giống); Greedy 1 282;
  prior chia đều sửa được phần lớn, prior đảo ngược chỉ một phần (13.7).
- **Lạc quan:** λ, α chọn trên chính thế giới này → số báo cáo lấy ở lượt tổng kết với hạt giống khác (Bước 43).
- T2S (93 truy vấn): tham chiếu Popularity phía học viên xếp theo số lời mời đã nhận từ Bước 39.
