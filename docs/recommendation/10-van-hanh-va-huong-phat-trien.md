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
| `tutor-weights.*` (a′): sem / price / need / segment / sched / area | 0,30 / 0,25 / 0,20 / 0,10 / 0,10 / 0,05 | 06, 6.12 |
| `area.same-province` / `area.other-province` | 0,6 / 0,2 | 06, 6.4 |
| `history.max-weight` / `history.full-signals` | 0,15 / 10 | 06, 6.4 |
| `history.half-life-days` / `window-days` | 30 / 180 | 07, 7.4 |
| `history.signal-weights` view / favorite / contact / accepted / dismissed | 1 / 2 / 3 / 4 / −2 | 07, 7.4 |
| `low-confidence-threshold` / `confidence-prior` | 0,4 / 0,5 | 06, 6.5 |
| `price.hard-factor` | 1,5 | 06, 6.2 |
| `willingness.alpha` / `p0-default` / `p0-min-decisions` / `history-days` / `congestion-lambda` | 2 / 0,6 / 20 / 180 / 0,5 | 06, 6.6 |
| `reciprocal.enabled` / `willingness.congestion-enabled` | true / true | 06, 6.6–6.7 |
| `quality.base` / `weight` | 0,7 / 0,3 | 06, 6.8 |
| `complaint.window-days` / `prior-contracts` / `penalty-scale` / `max-penalty` | 365 / 5 / 40 / 20 | 06, 6.8 |
| `complaint.category-weights` ABSENCE / LATE_OR_SHORT / TEACHING_QUALITY / MISCONDUCT / SERIOUS_MISCONDUCT / OTHER; `both-factor` | 1,0 / 0,5 / 0,5 / 1,5 / 3,0 / 1,0; 0,5 | 06, 6.8 |
| `gate.no-schedule-overlap` / `recent-reject` | 0,4 / 0,5 | 06, 6.8 |
| `rerank.enabled` / `top-k` / `timeout-ms` / `chunk-size` | true / 20 / 4 000 / 4 | 06, 6.9 |
| `rerank.prefetch-top-k` / `prefetch-timeout-ms` / `prefetch-cron` / `cache-retention-days` | 30 / 120 000 / `0 0 19 * * *` (UTC) / 30 | 06, 6.9 |
| `rerank.model-version` (backend) · `AI_RERANK_MAX_TOKENS` (AI service, mỗi bên) | `bge-reranker-v2-m3-int8` · 256 | 06, 6.9 |
| `exploration.enabled` / `page-size` / `slots` / `min-score-ratio` / `stats-cron` | true / 20 / 5,12 / 0,8 / `0 0 20 * * *` (UTC) | 06, 6.11 |
| `history.enabled` / `max-weight` / `full-weight-signals` / `window-days` / `half-life-days` / `nightly-cron` | true / 0,15 / 10 / 180 / 30 / `0 30 19 * * *` (UTC) | 06, 6.4 · 07, 7.4 |
| `refine.enabled` / `positive-weight` / `negative-weight` / `skipped-weight` / `original-share` | true / 0,6 / 0,3 / 0,5 / 0,5 | 07, 7.2 |
| `relaxation.enabled` / `good-threshold` / `budget-ms` / `max-suggestions` | true / 0,50 / 400 / 3 | 07, 7.1 |
| `relaxation.min-good` / `min-results` / `min-gain` / `best-score-margin` | 5 / 10 / 2 / 0,10 | 07, 7.1 |
| `refine.alpha` / `beta` / `gamma` | 1,0 / 0,6 / 0,3 | 07, 7.2 |
| `proactive.enabled` / `notify-threshold` / `top-k` / `cooldown-hours` | true / 0,65 / 3 / 24 | 07, 7.3 |
| `saved-search.max-per-user` | 5 | 05, 5.6 |
| `parse-query.enabled` / `timeout-ms` / `cache-hours` / `cache-size` | true / 3 000 / 24 / 500 | 05, 5.4 |
| `enrichment.min-confidence` / `max-attempts` / `timeout-ms` / `batch-size` / `worker-interval-ms` | 0,6 / 5 / 25 000 / 5 / 30 000 | 04 |
| `learning.enabled` / `models.<key>.mode` / `prior-strength` / `exploration-scale` / `min-ratio` / `max-ratio` / `max-step` | true / `SHADOW` / 200 / 1,0 / 0,5 / 2,0 / 0,05 | 12, 12.10 (bảng đầy đủ, gồm thang thưởng và mẫu phía b) |
| `algorithm-version` | `v2.1-rc` (trước Bước 8: `v2.1-a`) | Ghi vào log |

Cờ bật/tắt từng thành phần: `reciprocal`, `rerank`, `exploration`, `history`, `relaxation`, `refine`,
`proactive`, `learning`. Dùng để chạy ablation (09) và tắt nhanh khi demo gặp sự cố.

## 10.2. AI service

| Hạng mục | Thiết kế |
|---|---|
| Ngôn ngữ / framework | Python, FastAPI |
| Embedding | `BAAI/bge-m3` (dense, 1024 chiều), ONNX Runtime int8. File lấy từ bản chuyển đổi `Xenova/bge-m3` (`onnx/model_int8.onnx`, ~586 MB kể cả tokenizer); lấy vector token đầu (CLS) rồi chuẩn hoá độ dài 1. Ghi `embedding_model_version = bge-m3-int8` |
| Rerank | `BAAI/bge-reranker-v2-m3`, ONNX Runtime int8 |
| LLM | OpenAI `gpt-6-luna` (biến `AI_LLM_MODEL`), đầu ra có schema, `store=False`; không gửi `temperature` |
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

**Đã đo trên máy phát triển (12/10/2026, `scripts/benchmark_rerank.py`):** 20 cặp mất 2,2 s (tin rất
ngắn) – 3,5 s (tin điển hình) – 11,8 s (tin dài 256 token), tức vượt ngưỡng 1 s. Đã chọn giữ model và
`top-k`, nâng ngân sách lên 4 000 ms và chấm theo lô nhỏ (06 mục 6.9). Trên VPS yếu hơn cần đo lại;
các núm chỉnh theo thứ tự nên thử: `AI_RERANK_MAX_TOKENS` 256 → 128, `rerank.top-k` 20 → 10.
AI service dùng khoảng 2,3 GB RAM khi nạp cả hai model.

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
- L1: sau mỗi lần chạy `WeightLearningJob` ghi log số mẫu mới, trọng số trước / sau, khoảng cách tới prior,
  kết quả đánh giá so với prior và việc có kích hoạt hay không; mức `WARN` khi một trọng số chạm biên kẹp
  nhiều đêm liền (dấu hiệu prior lệch xa hoặc dữ liệu bất thường) hoặc khi job lỗi.

## 10.5. Hướng phát triển

### Mô hình học từ dữ liệu (learning-to-rank)

> Từ 13/10/2026, **học trọng số tuyến tính trực tuyến đã thuộc phạm vi đồ án** (L1, [12](./12-hoc-trong-so.md)):
> trọng số tự đặt là prior, contextual bandit / hồi quy Bayes tự tinh chỉnh. Mục này giữ lại cho bước tiếp
> theo — mô hình phi tuyến (LightGBM LambdaMART) khi đủ dữ liệu, huấn luyện trên chính log của L1.

Giai đoạn này dùng **trọng số khởi đầu lạnh** làm prior cho L1. Khi có nhiều dữ liệu thật,
thay phần gộp tuyến tính bằng mô hình học — **giữ nguyên cấu trúc** `R = 2ab/(a+b)`, `× Q × G`, chỉ thay cách tính
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
| Đánh giá học sinh (để có Q cho chiều T→S) | Quyết định sản phẩm | Khiếu nại có `fault_party` chỉ học sinh có lỗi đã dùng được làm một thành phần độ tin cậy của học sinh (cùng cách tính như 06, mục 6.8) |
| Index ANN (`hnsw`) | > ~10 000 tin đăng | — |

## 10.6. Thứ tự triển khai gợi ý

1. Điều kiện tiên quyết P1–P7 (01, mục 1.5). Migration **`V27`** (P2, P5; 03, mục 3.3) làm trước; P7 làm cùng bước tìm gia sư (3).
2. Migration **`V28`** (03, mục 3.4; áp dụng sau `V27`, Flyway không bật `out-of-order`); AI service (`/embed`); `EmbeddingWorker`.
3. Bộ lọc + `QueryContextBuilder` + pha 1–3 với a, Q, G → **V2-a** chạy được, ghi log.
4. A1 (`/enrich`, seed bộ từ vựng tag, API quản trị tag và gợi ý tag bằng LLM — 04 mục 4.11).
5. b + chống dồn → **V2-RC**.
6. Rerank + cache + `RerankPrefetchJob` → **V2-RCX**.
7. H1 (`ProfileUpdater`, `f_hist`), B5, slot khám phá, nới tiêu chí.
8. Lưu tìm kiếm + C7 (`MatchAlertWorker`).
9. A2 (`/parse-query`, tự điền bộ lọc).
10. Dữ liệu mô phỏng và đánh giá (có thể bắt đầu song song từ bước 3).
11. L1 (12): (a) chuyển hằng số viết cứng vào `RecommendationSettings` + `WeightProvider` trả prior — kết quả
    không đổi; (b) migration bảng trọng số và snapshot, ghi snapshot khi tạo đơn, bổ sung log;
    (c) `LinearBayesModel` + `WeightLearningJob` + chế độ `SHADOW`/`ACTIVE`; (d) thí nghiệm học trên mô
    phỏng (09, mục 9.9); (e) API quản trị (tuỳ chọn).
