# Kịch bản `crowded`, thế giới báo cáo (hạt giống 20261027)

> 💡 Đây là thế giới dùng cho **số báo cáo** (Bước 43b). Công thức sinh giống hệt thế giới tinh chỉnh
> [`crowded`](../crowded/README.md), chỉ khác hạt giống ngẫu nhiên. Mọi tham số (của thuật toán và của bộ mô phỏng) đã
> chọn trên thế giới cũ và **không chỉnh lại** ở đây, để số đo không bị "luyện" trên chính dữ liệu dùng để báo cáo.
> Số trên **dữ liệu mô phỏng**, sinh bằng `scripts/recommendation_eval/`.

**Chạy gì.** `EVAL_SCENARIO=crowded EVAL_SEED=20261027 EVAL_OUT=crowded-final EVAL_DB=ptutor_eval_final`:
600 gia sư, 945 tin dạy, 400 học sinh, 360 tin học, 1 369 truy vấn (thế giới cũ: 973 tin dạy, 1 380 truy vấn). A1 bằng
prompt 13.6 (2 305 lời gọi LLM), embedding đủ cho mọi tin OPEN, gia sư, học sinh và hồ sơ sở thích (0 thiếu vector).
Database `ptutor_eval_final` / `ptutor_eval_final_a1` (sau A1 + embedding) / `ptutor_eval_final_seed` (sau lịch sử
duyệt: 3 626 sự kiện, 387 hồ sơ sở thích).

| File | Nội dung |
|---|---|
| `kiem-tra-mo-phong.csv` | Kiểm tra bộ mô phỏng trước khi đọc kết quả thuật toán (13.3) |

## Kiểm tra bộ mô phỏng (13.3)

| Tiêu chí | Thế giới báo cáo | Thế giới cũ | Mục tiêu |
|---|---|---|---|
| Trung vị ứng viên S2T / T2S | 167 / 59 | 171 / 61 | ≥ 50 |
| P(nhận) trung bình top-50 | 0,523 | 0,519 | 0,4–0,6 |
| EA@10 Random | 0,479 | 0,475 | 0,4–0,6 |
| Tương quan Q–rel [KTC 95 %] | **0,195** [0,184; 0,206] | 0,203 [0,193; 0,214] | 0,2–0,4 |
| NDCG: Popularity − Random | +0,134 | +0,182 | > 0 |
| Popularity bị từ chối nhiều nhất | có (2,69 / HS) | có (2,65 / HS) | có |

**Hạn chế: Q–rel = 0,195 [0,184; 0,206], nằm trong dao động giữa các hạt giống.** Giá trị này thấp hơn ngưỡng 0,2
một chút. Nhưng KTC chứa 0,2, và hai khoảng của hai thế giới chồng lên nhau gần hết. Ngưỡng 0,2–0,4 là ngưỡng tự đặt,
dùng để kiểm tra rằng Q có mang tín hiệu thật. Mục đích đó vẫn đạt: Popularity hơn Random và vẫn bị từ chối nhiều
nhất. Bộ mô phỏng không được chỉnh lại để chạm ngưỡng.

> 💡 KTC tính bằng bootstrap **theo truy vấn**: mỗi lượt rút lại 1 180 truy vấn S2T (có hoàn lại), lấy toàn bộ ứng
> viên top-50 của các truy vấn được rút, rồi tính lại tương quan; lặp 2 000 lượt (`crowded_check.corr_ci`). Rút theo
> truy vấn thay vì theo từng cặp, vì 50 ứng viên của cùng một truy vấn không độc lập với nhau. Rút theo cặp sẽ cho
> khoảng hẹp quá mức. Số của thế giới cũ tính lại bằng cùng hàm, từ cùng file `V2-a.json` (không chạy lại tìm kiếm).

V2-a trên 587 truy vấn S2T nửa kiểm tra: NDCG@10 0,669, EA@10 0,555, MS@10 0,428, ghép 0,711 (thế giới cũ, 597 truy
vấn: 0,661 / 0,558 / 0,418 / 0,725).
