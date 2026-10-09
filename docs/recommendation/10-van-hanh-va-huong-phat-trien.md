# 10. Vận hành, tham số và hướng phát triển

## 10.1. Bảng tham số

Mọi tham số đặt trong `application.yml` (`app.recommendation.*`) để hiệu chỉnh không cần sửa code.
Giá trị dưới đây là **điểm khởi đầu**; bước đánh giá (09) hiệu chỉnh trên tập hiệu chỉnh.

| Tham số | Giá trị | Tài liệu |
|---|---|---|
| `candidate-limit` | 200 (180 theo vector + 20 chưa embed) | 06, 6.3 |
| `semantic.cos-low` / `cos-high` | 0,30 / 0,80 | 06, 6.4 |
| `semantic.rerank-weight` | 0,7 | 06, 6.4 |
| Trọng số a (S→T): sem / need / price / style / sched / segment | 0,35 / 0,20 / 0,20 / 0,10 / 0,10 / 0,05 | 06, 6.4 |
| Trọng số a′ (T→S): sem / price / need / segment / sched | 0,30 / 0,25 / 0,20 / 0,15 / 0,10 | 06, 6.12 |
| `low-confidence-threshold` | 0,4 | 06, 6.5 |
| `price.hard-factor` | 1,5 | 06, 6.2 |
| `willingness.alpha` / `p0-default` / `history-days` | 2 / 0,6 / 180 | 06, 6.6 |
| `willingness.congestion-lambda` | 0,5 | 06, 6.6 |
| `quality.base` / `quality.weight` | 0,7 / 0,3 | 06, 6.8 |
| `gate.no-schedule-overlap` / `gate.recent-reject` | 0,4 / 0,5 | 06, 6.8 |
| `rerank.top-k` / `rerank.timeout-ms` / `rerank.max-tokens` | 20 / 800 / 256 | 06, 6.9 |
| `exploration.page-size` / `slots` / `min-score-ratio` | 20 / [5, 12] / 0,8 | 06, 6.11 |
| `relaxation.good-threshold` / `budget-ms` / `max-suggestions` | 0,50 / 400 / 3 | 07, 7.1 |
| `refine.alpha` / `beta` / `gamma` | 1,0 / 0,6 / 0,3 | 07, 7.2 |
| `proactive.notify-threshold` / `top-k` / `cooldown-hours` | 0,65 / 3 / 24 | 07, 7.3 |
| `enrichment.min-confidence` / `max-attempts` | 0,6 / 5 | 04, 4.6–4.8 |
| `algorithm-version` | `v2.0` | Ghi vào log |

Có thể bật/tắt từng thành phần (`reciprocal.enabled`, `rerank.enabled`, `exploration.enabled`,
`relaxation.enabled`, `refine.enabled`, `proactive.enabled`). Ngoài việc giúp chạy ablation (09),
cờ này cho phép tắt nhanh một thành phần nếu gặp sự cố khi demo.

## 10.2. AI service

| Hạng mục | Thiết kế |
|---|---|
| Ngôn ngữ / framework | Python, FastAPI, chạy 1 tiến trình |
| Embedding | `BAAI/bge-m3` (dense, 1024 chiều), ONNX Runtime, lượng tử hoá int8 |
| Rerank | `BAAI/bge-reranker-v2-m3`, ONNX Runtime, int8 |
| LLM | API bên ngoài, chế độ JSON, `temperature = 0` |
| File model | Tải về volume ở lần chạy đầu, không đóng gói vào image |
| Bảo mật | Chỉ mạng nội bộ docker; header `X-Internal-Token` dùng chung với backend |
| Sẵn sàng | `GET /health` trả `ready` khi đã nạp model; backend chỉ gọi khi `ready` |
| Bộ nhớ | Khoảng 1,5–2 GB RAM với hai model int8 |

Ngày đầu triển khai cần **đo thử độ trễ rerank** trên máy phát triển và trên VPS. Nếu rerank 20
cặp vượt khoảng 1 giây kể cả khi đã int8, chuyển sang model rerank nhỏ hơn hoặc giảm `top-k`.

## 10.3. Quyền riêng tư (tổng hợp)

| Nguyên tắc | Áp dụng ở |
|---|---|
| Không gửi định danh cá nhân (họ tên, SĐT, email, địa chỉ chi tiết, ngày sinh, CCCD) ra ngoài | A1, A2, C8 |
| Lọc SĐT/email lọt trong văn bản tự do trước khi gửi LLM và trước khi ghi log | A1, A2, log |
| Embedding và rerank chạy cục bộ, không gửi văn bản ra ngoài | R3, embedding |
| Gia sư chỉ thấy thông tin giới hạn của học sinh trong danh sách đề xuất | API T→S |
| Không dùng toạ độ địa lý | Toàn hệ thống |
| Chọn nhà cung cấp LLM cam kết không dùng dữ liệu API để huấn luyện | A1, A2 |

## 10.4. Giám sát tối thiểu

- Log mỗi lần tìm: độ trễ từng pha, `rerankStatus` (`CACHE` / `ONLINE` / `TIMEOUT` / `DISABLED`),
  số ứng viên sau mỗi pha.
- Đếm job `ai_enrichment_jobs` theo trạng thái; cảnh báo khi số `FAILED` tăng.
- Màn hình admin (tuỳ chọn): xem một lần tìm cụ thể với đầy đủ a, b, Q, G của từng ứng viên —
  rất hữu ích khi demo và khi hội đồng hỏi "vì sao người này đứng đầu?".

## 10.5. Hướng phát triển (ngoài phạm vi)

| Hướng | Điều kiện để làm | Đã chuẩn bị sẵn |
|---|---|---|
| **Learning-to-rank** (logistic regression → LambdaMART) | Đủ log thật có nhãn "đơn được chấp nhận" | `returned_candidate_ids` lưu đủ đặc trưng từng ứng viên |
| **Xếp hạng theo ghép đôi thành công lâu dài** | Đủ hợp đồng đã kết thúc | Nhãn định nghĩa được từ `contracts.status`, `renewed_from_contract_id`, `lessons.status`, `cancel_contract_requests`, `complaints` |
| **Gale–Shapley / Hospital–Residents** ghép tối ưu toàn cục | Nhiều tin đăng mở cùng lúc | `recommendation_pair_scores` chính là ma trận ưu tiên hai phía |
| **Đánh giá học sinh** (để có Q cho chiều T→S) | Quyết định sản phẩm | — |
| **Index ANN** (`hnsw`) cho vector | > khoảng 10 000 tin đăng | — |

## 10.6. Thứ tự triển khai gợi ý

1. Điều kiện tiên quyết P1–P7 (01, mục 1.5).
2. Migration V25, V26; AI service (`/embed`); `EmbeddingWorker`.
3. Pha 1–3 với a, Q, G (chưa có b) → **V2-a** chạy được, ghi log.
4. A1 (`/enrich`, `EnrichmentWorker`, seed bộ từ vựng tag).
5. b + chống dồn → **V2-RC**.
6. Rerank + `PairScoringWorker` → **V2-RCX**; C7 dùng lại worker này.
7. Slot khám phá, nới tiêu chí, Rocchio.
8. A2.
9. Bộ dữ liệu mô phỏng và đánh giá (có thể bắt đầu song song từ bước 3).
