# Kịch bản `base` — lượt 22/10/2026

> 📌 **Lượt tinh chỉnh, không dùng cho báo cáo** (28/10/2026). Số báo cáo lấy trên thế giới hạt giống khác: [`../tong-ket/`](../tong-ket/README.md).

> 💡 Ghi chú tinh chỉnh, chưa phải văn báo cáo. Số trên **dữ liệu mô phỏng**, sinh bằng `scripts/recommendation_eval/` (seed cố định). Lý do và quyết định: [13](../../13-tinh-chinh-sau-danh-gia.md); cách đọc chỉ số: 13 mục 13.0.

> 💡 **Lượt cũ, dùng để tìm chỗ cần chỉnh kiến trúc.** Rerank chạy `ONLINE` (`top-k` 10, 1 500 ms), lô nào phía học
> sinh cũng quá hạn và làm `/embed` quá hạn theo: 7–11 / 79 truy vấn của các cấu hình có rerank dùng nhầm vector hồ sơ
> (13 mục 13.0, 13.2). Reciprocal điều hoà, chống dồn cũ. Database của lượt này đã bị ghi đè; `base` dựng lại ở
> `../base-t40/`.

**Chạy gì.** 120 gia sư (202 tin dạy), 60 học sinh (32 tin học), 4 tuần lịch sử; 79 truy vấn S2T nửa kiểm tra; 7 cấu
hình + 5 đối chứng; độ trễ nguội / ấm (`latency.csv`); độ nhạy trọng số (`9.9.1-*`).

| Cấu hình | NDCG@10 | MS@10 | EA@10 | Bị từ chối / HS (một lượt) | Gini | p95 (ms) |
|---|---|---|---|---|---|---|
| Random | 0,341 | 0,094 | 0,830 | 0,280 | 0,498 | — |
| Popularity | 0,325 | 0,100 | 0,825 | 0,442 | 0,506 | — |
| Semantic | 0,376 | 0,095 | 0,836 | 0,225 | 0,493 | — |
| V1 (thiết kế cũ) | 0,338 | 0,102 | 0,834 | 0,262 | 0,499 | — |
| V2-linear | 0,411 | 0,127 | 0,841 | 0,248 | 0,496 | — |
| V2-a | 0,408 | 0,132 | 0,842 | 0,250 | 0,496 | 421 |
| V2-R | 0,401 | 0,108 | 0,843 | 0,217 | 0,498 | 402 |
| V2-RC | 0,393 | 0,107 | 0,840 | 0,267 | 0,502 | 427 |
| V2-RCX | 0,393 | 0,107 | 0,839 | 0,261 | 0,502 | 2 688 |
| V2-full | 0,394 | 0,107 | 0,841 | 0,250 | 0,503 | 2 602 |
| V2-full − H1 | 0,393 | 0,106 | 0,839 | 0,252 | 0,505 | 2 704 |
| V2-full − A1 | 0,372 | 0,099 | 0,832 | 0,293 | 0,503 | 2 694 |

**Độ trễ (`latency.csv`, phía học sinh):** pha rerank p50 1 508 ms (chạm ngân sách 1 500 ms ở cả lượt nguội lẫn ấm,
173 / 170 lần hết giờ), tổng p95 ~2,8 s; phía gia sư (văn bản ngắn) lượt ấm tổng p50 336 ms.

## Đọc kết quả

- Loại các lần dùng nhầm vector hồ sơ, **không kết luận nào đổi**: Random → V2-full NDCG +0,060 (p = 0,006),
  Popularity → V2-full +0,073 (p < 0,001), bỏ A1 → V2-full +0,024 (p = 0,0015).
- Thị trường thừa chỗ (ghép 96–97 % ở mọi cấu hình): chống dồn cũ làm NDCG giảm và bị từ chối tăng (V2-R 0,218 →
  V2-RC 0,267) — lý do đổi công thức chống dồn (13.4), kiểm lại ở `base-t40`.
- Popularity kém Random (Q không mang tín hiệu độ phù hợp ở `base`) → lý do dựng `crowded` (13.3).
