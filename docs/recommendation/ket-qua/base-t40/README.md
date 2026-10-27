# Kịch bản `base` dựng lại (Bước 40, 27/10/2026)

> 💡 Ghi chú tinh chỉnh, chưa phải văn báo cáo. Số trên **dữ liệu mô phỏng**, sinh bằng `scripts/recommendation_eval/` (seed cố định). Lý do và quyết định: [13](../../13-tinh-chinh-sau-danh-gia.md); cách đọc chỉ số: 13 mục 13.0.

**Chạy gì.** Thế giới `base` (thị trường thừa chỗ: 120 gia sư / 202 tin dạy, 60 học sinh) dựng lại trong database
riêng `ptutor_eval_base` / `ptutor_eval_base_seed`, A1 bằng prompt mới. Mọi cấu hình dùng α 0,65; rerank tắt. 79 truy
vấn S2T nửa kiểm tra, 58 truy vấn T2S; thị trường 60 học sinh (mẫu nhỏ). Hai câu hỏi: công thức chống dồn mới có sửa
được lỗi "phạt cả khi còn thừa chỗ" (13.4) không, và tách Q / hai cổng chặn (13.8).

| File | Nội dung |
|---|---|
| `9.7-bang-ket-qua.csv`, `9.6-kiem-dinh.csv`, `9.7-so-sanh-cau-hinh.png` | Tìm kiếm |
| `market-t40.csv`, `t40-base-kiem-dinh-thi-truong.csv` | Thị trường 8 vòng, so theo học sinh |
| `t40-base-quet.csv` | Bảng gộp tìm kiếm + thị trường (`sweep_report.py --preset t40-base`) |

## Tìm kiếm (S2T, 79 truy vấn)

`V2-RC` = mặc định hiện tại; `V2-RC-oldCongestion` = chống dồn cũ.

| Cấu hình | NDCG@10 | MS@10 | EA@10 | Bị từ chối / HS (một lượt) | Gini | p95 (ms) |
|---|---|---|---|---|---|---|
| Random | 0,346 | 0,090 | 0,830 | 0,299 | 0,499 | — |
| Popularity | 0,331 | 0,103 | 0,825 | 0,446 | 0,507 | — |
| Semantic | 0,385 | 0,103 | 0,839 | 0,250 | 0,491 | — |
| V1 (thiết kế cũ) | 0,359 | 0,108 | 0,835 | 0,219 | 0,499 | — |
| V2-linear | 0,423 | 0,129 | 0,840 | 0,262 | 0,494 | — |
| V2-a | 0,409 | 0,130 | 0,841 | 0,189 | 0,495 | 458 |
| V2-R | 0,400 | 0,113 | 0,838 | 0,206 | 0,497 | 453 |
| V2-RC | 0,398 | 0,109 | 0,836 | 0,239 | 0,500 | 899 |
| V2-RC-oldCongestion | 0,392 | 0,109 | 0,836 | 0,252 | 0,500 | 424 |
| V2-RC-noQ | 0,395 | 0,110 | 0,841 | 0,233 | 0,500 | 423 |
| V2-RC-noSchedGate | 0,398 | 0,109 | 0,836 | 0,236 | 0,500 | 471 |
| V2-RC-noRejectGate | 0,409 | 0,113 | 0,836 | 0,213 | 0,501 | 445 |

## Thị trường 8 vòng

| Cấu hình | Bị từ chối / HS | Vòng chờ | Gini hiển thị | Gia sư mới | rel khi ghép |
|---|---|---|---|---|---|
| V2-a | 0,33 | 1,03 | 0,550 | 0,000 | 1,37 |
| V2-R | 0,30 | 1,02 | 0,567 | 0,000 | 1,36 |
| V2-RC | 0,18 | 1,02 | 0,511 | 0,000 | 1,37 |
| V2-RC-oldCongestion | 0,10 | 1,00 | 0,511 | 0,000 | 1,44 |
| V2-RC-noQ | 0,23 | 1,02 | 0,512 | 0,000 | 1,32 |
| V2-RC-noSchedGate | 0,18 | 1,02 | 0,512 | 0,000 | 1,37 |
| V2-RC-noRejectGate | 0,15 | 1,00 | 0,517 | 0,000 | 1,44 |

## Đọc kết quả

- **Chống dồn:** công thức cũ lặp lại lỗi 22/10 (V2-R → cũ NDCG −0,0074, p = 0,012); công thức mới bỏ phần lớn mức
  giảm (cũ → mới +0,005 [+0,001; +0,011]) và bị từ chối một lượt ít hơn (0,239 so với 0,252). Thị trường 8 vòng: cũ
  0,10 / mới 0,18 lần từ chối mỗi HS, không có ý nghĩa (~6 so với 11 lần trên 60 HS). → giữ công thức mới.
- **Q:** tắt Q NDCG −0,003, không ý nghĩa. **Cổng lịch:** không đổi gì. **Cổng từ chối:** tắt +0,011
  [−0,000; +0,026], không ý nghĩa.
- Gia sư mới = 0 vì `base` không có gia sư mới; mẫu 60 học sinh nhỏ, chỉ dùng để kiểm tra hướng.
