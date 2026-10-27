# Kịch bản `text-only` — nhu cầu chỉ nằm trong văn bản

> 💡 Ghi chú tinh chỉnh, chưa phải văn báo cáo. Số trên **dữ liệu mô phỏng**, sinh bằng `scripts/recommendation_eval/` (seed cố định). Lý do và quyết định: [13](../../13-tinh-chinh-sau-danh-gia.md); cách đọc chỉ số: 13 mục 13.0.

**Chạy gì.** 175 truy vấn S2T có nhu cầu **chỉ nằm trong văn bản** (ví dụ "con hay mất tập trung, cần chia nhỏ bài"),
không tag hay bộ lọc nào thể hiện — nơi rerank lẽ ra có lợi nhất. Database `ptutor_eval_text` / `_seed` (A1 bằng
prompt cũ; kịch bản chỉ dùng để đo rerank). Mỗi cấu hình 175 truy vấn × 2 lượt.

| Cấu hình | Ý nghĩa |
|---|---|
| `cosine-only` | Không rerank |
| `rerank-w0.5`, `rerank-w0.7`, `rerank-w0.7-t4000` | Lượt 22/10: `ONLINE`, trọng số rerank 0,5 / 0,7, ngân sách 1 500 / 4 000 ms — **bị lỗi dùng nhầm vector hồ sơ** (cột `fallbacks`) |
| `rerank-online`, `rerank-async` | 26/10 sau khi sửa, cache nguội |
| `rerank-async-2`, `rerank-online-2` | Lượt 2: worker đã chấm đủ 3 500 cặp |

| Cấu hình | Lần dùng vector hồ sơ | NDCG@10 | MS@10 | p50 (ms) | p95 (ms) |
|---|---|---|---|---|---|
| cosine-only | 0 | 0,525 | 0,496 | 209 | 329 |
| rerank-w0.5 | 93 | 0,538 | 0,508 | 2 587 | 2 655 |
| rerank-w0.7 | 87 | 0,535 | 0,505 | 2 582 | 2 648 |
| rerank-w0.7-t4000 | 27 | 0,530 | 0,499 | 4 737 | 5 098 |
| rerank-online | 0 | 0,525 | 0,496 | 2 013 | 2 270 |
| rerank-async | 0 | 0,525 | 0,496 | 324 | 417 |
| rerank-async-2 | 0 | 0,524 | 0,496 | 328 | 420 |
| rerank-online-2 | 0 | 0,524 | 0,496 | 320 | 450 |

`latency.csv`: lượt `V2-full` khi hàng đợi rerank còn việc (worker chấm chen 60 lần) — pha vector p50 / p95 248 / 514
ms, tổng p95 705 ms; RAM trống 3,6–3,7 GB.

## Đọc kết quả

- **"+0,013" của 22/10 là ảo:** đến từ các lần dùng nhầm vector hồ sơ (189 / 350 ở `rerank-w0.5`); loại ra thì
  cosine → rerank +0,0000.
- **Rerank chạy đúng cách không giúp:** chấm đủ top-10 mọi truy vấn, cosine-only → `rerank-async-2` NDCG −0,0014
  [−0,0039; +0,0011], MS@10 0. → `rerank.mode = OFF` mặc định; điều kiện bật lại ở 13.2.
- `ASYNC` không bắt lần tìm chờ: tổng p95 417–705 ms so với 2 270 ms của `ONLINE` cache nguội; 0 / 1 400 lần dùng nhầm
  vector hồ sơ.
