# 10. Vận hành, tham số và hướng phát triển

## 10.1. Bảng tham số

Đặt trong cấu hình `app.recommendation.*`. Lưu ý: dự án đang để `application.yml` trong
`.gitignore` — cần tách phần cấu hình không bí mật ra file được Git theo dõi (ví dụ
`application-recommendation.yml`) để cả nhóm dùng chung.

| Tham số | Giá trị khởi đầu | Tài liệu |
|---|---|---|
| `candidate-limit` | 200 (180 theo vector + 20 chưa embed) | 06, 6.3 |
| `semantic.cos-low` / `cos-high` / `rerank-weight` | 0,30 / 0,80 / 0,7 | 06, 6.4 |
| Trọng số a (S→T): sem / need / price / style / sched / area / segment | 0,30 / 0,20 / 0,20 / 0,10 / 0,10 / 0,05 / 0,05 | 06, 6.4 |
| Trọng số a′ (T→S): sem / price / need / segment / sched / area | 0,30 / 0,25 / 0,20 / 0,10 / 0,10 / 0,05 | 06, 6.12 |
| `area.same-province` / `area.other-province` | 0,6 / 0,2 | 06, 6.4 |
| `history.max-weight` / `history.full-signals` | 0,15 / 10 | 06, 6.4 |
| `history.half-life-days` / `window-days` | 30 / 180 | 07, 7.4 |
| `history.signal-weights` view / favorite / contact / accepted / dismissed | 1 / 2 / 3 / 4 / −2 | 07, 7.4 |
| `low-confidence-threshold` / `confidence-prior` | 0,4 / 0,5 | 06, 6.5 |
| `price.hard-factor` | 1,5 | 06, 6.2 |
| `willingness.alpha` / `p0-default` / `p0-min-decisions` / `history-days` / `congestion-lambda` | 2 / 0,6 / 20 / 180 / 0,5 | 06, 6.6 |
| `reciprocal.enabled` / `willingness.congestion-enabled` | true / true | 06, 6.6–6.7 |
| `quality.base` / `weight` | 0,7 / 0,3 | 06, 6.8 |
| `gate.no-schedule-overlap` / `recent-reject` | 0,4 / 0,5 | 06, 6.8 |
| `rerank.top-k` / `timeout-ms` / `max-tokens` | 20 / 800 / 256 | 06, 6.9 |
| `exploration.page-size` / `slots` / `min-score-ratio` | 20 / [5, 12] / 0,8 | 06, 6.11 |
| `relaxation.good-threshold` / `budget-ms` / `max-suggestions` | 0,50 / 400 / 3 | 07, 7.1 |
| `refine.alpha` / `beta` / `gamma` | 1,0 / 0,6 / 0,3 | 07, 7.2 |
| `proactive.notify-threshold` / `top-k` / `cooldown-hours` | 0,65 / 3 / 24 | 07, 7.3 |
| `saved-search.max-per-user` | 5 | 05, 5.6 |
| `enrichment.min-confidence` / `max-attempts` | 0,6 / 5 | 04 |
| `algorithm-version` | `v2.1-rc` (trước Bước 8: `v2.1-a`) | Ghi vào log |

Cờ bật/tắt từng thành phần: `reciprocal`, `rerank`, `exploration`, `history`, `relaxation`, `refine`,
`proactive`. Dùng để chạy ablation (09) và tắt nhanh khi demo gặp sự cố.

## 10.2. AI service

| Hạng mục | Thiết kế |
|---|---|
| Ngôn ngữ / framework | Python, FastAPI |
| Embedding | `BAAI/bge-m3` (dense, 1024 chiều), ONNX Runtime int8. File lấy từ bản chuyển đổi `Xenova/bge-m3` (`onnx/model_int8.onnx`, ~586 MB kể cả tokenizer); lấy vector token đầu (CLS) rồi chuẩn hoá độ dài 1. Ghi `embedding_model_version = bge-m3-int8` |
| Rerank | `BAAI/bge-reranker-v2-m3`, ONNX Runtime int8 |
| LLM | API bên ngoài, chế độ JSON, `temperature = 0` |
| File model | Tải về volume lần chạy đầu, không đóng gói vào image (`ai-service/models`, git bỏ qua) |
| Bảo mật | Chỉ mạng nội bộ (cổng chỉ mở trên `127.0.0.1`); header `X-Internal-Token`; service không khởi động nếu thiếu token |
| Địa chỉ | Backend gọi `http://127.0.0.1:8000`, **không dùng `localhost`** (trên Windows `localhost` thử IPv6 trước, chậm ~2 s mỗi kết nối) |
| Hợp đồng `/embed` | `{"texts": [...]}` → `{"model", "dimensions": 1024, "embeddings": [[...]]}`; ≤ 32 văn bản, mỗi văn bản ≤ 512 token |
| Sẵn sàng | `GET /health` trả `ready` khi đã nạp model |
| Bộ nhớ | ~1,5–2 GB RAM với hai model int8 |

Số đo embedding trên máy phát triển (12/10/2026, CPU 12 luồng, `scripts/benchmark_embed.py`): câu truy vấn
ngắn p50 ≈ 62–76 ms, p95 ≈ 65–91 ms (ngân sách 150 ms); một tin đăng ~80 token ≈ 105–140 ms; gộp lô không
nhanh hơn (≈ 100–110 ms mỗi văn bản, lô 32 ≈ 3,5 s) → `EmbeddingWorker` dùng lô nhỏ và timeout riêng.

Ngày đầu triển khai: đo độ trễ rerank trên máy phát triển và VPS; nếu 20 cặp > ~1 s thì giảm `top-k`
hoặc dùng model nhỏ hơn.

## 10.3. Quyền riêng tư

| Nguyên tắc | Áp dụng ở |
|---|---|
| Không gửi định danh cá nhân ra ngoài | A1, A2, C8 |
| Lọc SĐT/email trong văn bản tự do trước khi gửi LLM và ghi log | A1, A2, log |
| Embedding, rerank chạy cục bộ | R3, embedding |
| Gia sư chỉ thấy thông tin giới hạn của học sinh trong danh sách đề xuất | API T→S |
| Ứng viên phía học sinh chỉ là **tin học đã đăng** — học sinh chưa đăng tin không bị hiển thị cho gia sư | Lọc cứng T→S |
| Hồ sơ sở thích (H1) xoá được bất cứ lúc nào | H1 |
| Không dùng toạ độ địa lý | Toàn hệ thống |

## 10.4. Giám sát tối thiểu

- Log mỗi lần tìm: độ trễ từng pha, `rerankStatus` (`CACHE`/`ONLINE`/`TIMEOUT`/`DISABLED`), số ứng
  viên sau mỗi pha.
- Số job `ai_enrichment_jobs` theo trạng thái; cảnh báo khi `FAILED` tăng.
- Màn hình admin (tuỳ chọn): xem một lần tìm với đủ a, b, Q, G, `f_hist` của từng ứng viên.

## 10.5. Hướng phát triển

### Mô hình học từ dữ liệu (learning-to-rank)

Giai đoạn này dùng **trọng số khởi đầu lạnh**, hiệu chỉnh trên dữ liệu mô phỏng. Khi có dữ liệu thật,
thay trọng số bằng mô hình học — **giữ nguyên cấu trúc** `R = 2ab/(a+b)`, `× Q × G`, chỉ thay cách tính
a và b:

| Mô hình | Dự đoán | Dữ liệu huấn luyện | Khi nào có |
|---|---|---|---|
| **Mô hình b** | P(gia sư chấp nhận đơn) / P(học sinh chấp nhận đề nghị) | **Mọi đơn trong hệ thống**: `student_tutor_requests`, `tutor_student_requests` với `ACCEPTED`/`REJECTED`; đặc trưng: độ chênh lớp, tỉ lệ giá, hình thức, trùng lịch, đối tượng, tỉ lệ nhận lịch sử, mức dồn đơn | Sớm nhất — không phụ thuộc tính năng đề xuất |
| **Mô hình a** | P(người tìm xem / tym / gửi đơn) | **Log đề xuất**: mỗi ứng viên đã hiển thị là một dòng, đặc trưng có sẵn trong `returned_candidate_ids`; nhãn: 0 không bấm, 1 xem, 2 tym/gửi đơn, 3 được nhận/hợp đồng (nối log với bảng đơn trong 7 ngày) | Sau khi triển khai, có người dùng |

**Thiên lệch vị trí:** (1) chỉ dùng ứng viên từ vị trí click thấp nhất trở lên; (2) dùng dữ liệu từ
slot khám phá (`isExploration`), vốn ít thiên lệch; (3) nâng cao: trọng số nghịch đảo xác suất (IPW).

**Mô hình:** bắt đầu bằng **logistic regression** (dễ giải thích, hệ số so được với trọng số tự đặt,
cần ít dữ liệu); khi đủ dữ liệu (cỡ vài nghìn lượt hiển thị trên vài trăm lần tìm có kết quả tích cực)
chuyển sang **LightGBM LambdaMART**.

**Quy trình:** xuất dữ liệu (SQL) → bảng huấn luyện → chia theo lần tìm/thời gian → huấn luyện
(`ai-service/training/`) → so NDCG/AUC với bản trọng số tự đặt → xuất mô hình (logistic regression:
JSON hệ số, Java tự tính; LightGBM: endpoint `/score`) → chạy "ngầm" song song, ghi cả hai vào log →
chuyển hẳn khi tốt hơn → huấn luyện lại hằng tuần; phiên bản ghi vào `algorithm_version`. Cờ bật/tắt
và tự quay về trọng số tự đặt khi chưa đủ dữ liệu hoặc mô hình kém hơn.

Tham khảo: Goda, Hayashi, Saito (Wantedly) — kết hợp hai mô hình một chiều cho gợi ý hai chiều trong
tuyển dụng (11, mục 11.4).

### Các hướng khác

| Hướng | Điều kiện | Đã chuẩn bị |
|---|---|---|
| Xếp hạng theo ghép đôi thành công lâu dài | Đủ hợp đồng đã kết thúc | Nhãn định nghĩa được từ `contracts`, `renewed_from_contract_id`, `lessons`, `cancel_contract_requests`, `complaints` |
| Tối ưu toàn cục cho thị trường ghép đôi (Su và cộng sự, 2022) / Gale–Shapley | Nhiều tin đăng mở cùng lúc | Log, mô hình b |
| Đánh giá học sinh (để có Q cho chiều T→S) | Quyết định sản phẩm | — |
| Index ANN (`hnsw`) | > ~10 000 tin đăng | — |

## 10.6. Thứ tự triển khai gợi ý

1. Điều kiện tiên quyết P1–P7 (01, mục 1.5). Migration **`V25`** (P2, P5; 03, mục 3.3) làm trước; P7 làm cùng bước tìm gia sư (3).
2. Migration **`V26`** (03, mục 3.4; áp dụng sau `V25`, Flyway không bật `out-of-order`); AI service (`/embed`); `EmbeddingWorker`.
3. Bộ lọc + `QueryContextBuilder` + pha 1–3 với a, Q, G → **V2-a** chạy được, ghi log.
4. A1 (`/enrich`, seed bộ từ vựng tag).
5. b + chống dồn → **V2-RC**.
6. Rerank + cache + `RerankPrefetchJob` → **V2-RCX**.
7. H1 (`ProfileUpdater`, `f_hist`), B5, slot khám phá, nới tiêu chí.
8. Lưu tìm kiếm + C7 (`MatchAlertWorker`).
9. A2 (`/parse-query`, tự điền bộ lọc).
10. Dữ liệu mô phỏng và đánh giá (có thể bắt đầu song song từ bước 3).
