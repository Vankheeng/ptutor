# Kiểm chứng L1 toàn hệ thống — "tìm lại trọng số ẩn" (Bước 43a, 27/10/2026)

> 📌 **Dùng cho báo cáo như bằng chứng vòng học chạy đúng trong sản phẩm** (28/10/2026), chạy trên thế giới hạt giống cũ; thí nghiệm học trên thế giới báo cáo ở [`../tong-ket/`](../tong-ket/README.md) mục 4.

> 💡 Ghi chú tinh chỉnh, chưa phải văn báo cáo. Thí nghiệm học ở `9.9.4` (`learning.py`) tự dựng quan sát trong
> Python; ở đây người dùng ảo đi qua **đúng đường dữ liệu của sản phẩm**: tìm kiếm qua API → sự kiện `/events` → đơn và
> ảnh chụp quyết định → `LearningDataRepository` (SQL) → `WeightLearningJob` → `WeightProvider` → log lần tìm sau.
> Mục đích: chứng minh cả vòng tự học chạy đúng và học ra đúng hướng. Lý do và quyết định:
> [13 mục 13.7](../../13-tinh-chinh-sau-danh-gia.md).

## Chạy gì

| | |
|---|---|
| Database | `ptutor_eval_l1`, chép từ `ptutor_eval_seed` (`crowded`: 400 HS, 600 GS, 973 tin dạy / 1 836 chỗ); 1 600 log duyệt danh sách cũ của seed đánh dấu xoá (không sinh theo w\*) |
| Cấu hình | Mặc định đã chốt (13.9) + `A_S2T`, `B_S2T` `ACTIVE`; `A_T2S`, `B_T2S` `OFF`; **ngưỡng kích hoạt, cửa sổ thưởng 7 ngày, giữ riêng 7 ngày, κ 50, ρ 0,5, γ 0,99, `max-step` 0,05 đúng như sản phẩm**; job đêm tắt lịch, chạy bằng `POST /api/v1/admin/recommendations/weights/learn` |
| Một ngày | 100 lần tìm S2T qua API (1 180 truy vấn, không lặp trước khi dùng hết) → người dùng ảo đọc trang đầu và phản ứng qua API → gia sư trả lời đơn trong ngày qua API → **lùi mọi cột thời gian 1 ngày** → chạy job học |
| Số ngày | 45 (4 500 lần tìm); 5 ngày cuối dùng đo "có ích" |
| Thời gian | ~190–210 s mỗi ngày, cả lượt ~2 giờ 40; RAM trống 2,3–5,0 GB |

**Người dùng ảo** (mô hình vị trí và độ hấp dẫn của `learning.py`, trên đúng đặc trưng backend ghi trong log):

- Trọng số ẩn phía a: **w\* = (0,18; 0,24; 0,32; 0,08; 0,08; 0,05; 0,05)** (sem, need, price, style, sched, area,
  segment); prior (0,30; 0,20; 0,20; 0,10; 0,10; 0,05; 0,05); ‖w\* − prior‖₁ = 0,32; mọi tỉ lệ w\* / prior trong [0,6;
  1,6] — **nằm trong biên an toàn [0,5; 2] × prior**, nên L1 tới được.
- Phía b: P(nhận) = σ(−4,8 + 2,5·hist + 1,0·fitSegment + 1,5·fitSched + 1,5·fitPrice) trên đặc trưng của **ảnh chụp
  quyết định** backend ghi khi tạo đơn. Hướng b\* = (0,385; 0,154; 0,231; 0,231), trong biên (tỉ lệ 0,77–1,38). Hệ số
  chặn −4,8 để P(nhận) trung bình ~0,53 (−2 cho 0,93 vì người nộp đơn tự chọn tin hợp).
- Phản ứng: xem → `VIEWED`, thích → `FAVORITED`, rất thích → một cơ hội 0,35 **gửi đơn** (rồi `CONTACTED` như
  frontend) mỗi lần tìm, các tin rất thích khác → `CONTACTED`; tin đọc mà không hấp dẫn → `DISMISSED` 15 %; 5 % đơn
  không được trả lời (từ chối ngầm sau 14 ngày).

**Hai lượt thử trước** (`out/crowded/l1-thu-1`, `l1-thu-2`, không dùng số): lỗi bí danh trong lệnh lùi giờ; người dùng
ảo gửi 302 rồi 74 đơn / 100 lần tìm (hết chỗ sau vài ngày) → giới hạn một cơ hội mỗi lần tìm, hạ hệ số chặn. Lượt
chính dừng một lần sau ngày 14 (cạn bộ nhớ hệ thống, `psql` 0xC0000142); chạy tiếp từ ngày 15, ngày 15 không làm lại.

## Kết quả

| Kiểm tra | Kết quả | |
|---|---|---|
| **Đối chiếu dữ liệu** — quan sát job dùng (file xuất) so với quan sát dựng lại độc lập từ những gì người dùng ảo đã làm (12.4–12.6) | a: 31 version, 3 100 lần tìm, **32 679 dòng ứng viên**; b: 38 version, **1 285 mẫu**; thiếu / thừa / lệch: **0** (ứng viên, hạng, đặc trưng, phần thưởng, trọng số mẫu; nhãn, trọng số nhận nhanh / từ chối ngầm, thời điểm) | đạt |
| **Nhất quán** — phát lại từng version bằng `linear_bayes.py` từ đúng quan sát xuất ra | lệch lớn nhất (A, c, μ, trọng số, β) **5,7 × 10⁻¹⁴** | đạt |
| **Vòng khép kín** — version mới mỗi đêm → kích hoạt → `WeightProvider` phục vụ → `query_context.weights` | **45 / 45 ngày** mọi lần tìm dùng đúng version đang kích hoạt (hoặc prior khi chưa có) | đạt |
| Version | a: 31 (ngày 15–45), kích hoạt 29; b: 38 (ngày 8–45), kích hoạt 12 | |
| **Có ích** — 5 ngày cuối (494 lần tìm), xếp lại offline bằng trọng số đang kích hoạt (a v31, b v32) so với prior, chấm theo w\* | phần thưởng kỳ vọng top-10 **+0,108 [+0,096; +0,120]** (3,977 → 4,085, +2,7 %); NDCG@10 **+0,029 [+0,025; +0,033]** | đạt |
| **Học đúng hướng** — ‖w − w\*‖₁ theo version (`sai-so-theo-ngay.png`) | b: 0,256 (prior) → **0,148** (giảm rõ từ ngày 15); a: 0,32 → 0,16 (v2) rồi dao động 0,19–0,235 (v31: 0,235) | b đạt; a **một phần** |

**Đọc kết quả a.** Đường dữ liệu đúng (đối chiếu và phát lại khớp tuyệt đối), nhưng trọng số a không tiến dần tới w\*
theo từng số: `sem` (0,30 → 0,17; w\* 0,18) và `style`, `sched` đi đúng; `price` chỉ lên 0,23 (w\* 0,32); `need`,
`area`, `segment` vượt w\* (`segment` chạm trần 0,10). Cùng hiện tượng với `learning.py` (9.9.4: `price` thiếu,
`segment` thừa). Lý do: phần thưởng không tuyến tính theo w\*·x (độ hấp dẫn hình sigmoid, ba mức thưởng), người dùng chỉ
thấy những tin hệ thống đã xếp trên, và các đặc trưng tương quan với nhau — hồi quy tuyến tính tìm bộ trọng số **dự
đoán phần thưởng tốt nhất trên dữ liệu đó**, không nhất thiết là w\*. Điều quan trọng là **thứ tự**: trên 5 ngày giữ
riêng, xếp bằng a học được đạt **91 %** mức cải thiện mà w\* thật đạt được so với prior (4,066 so với 4,074; prior
3,977); cả a và b học được đạt 83 % của cặp w\*, b\* (4,085 so với 4,107).

**b ít được kích hoạt** (12 / 38; 8 version đầu chưa đủ 300 mẫu): điều kiện lúc chạy là log-loss trên 7 ngày giữ
riêng không kém prior; hai bên chênh nhau ~0,001 (ví dụ v19 0,6362 so với 0,6355), nên lần đạt lần không. Trọng số b
vẫn tiến đều về b\*. Sau lượt này điều kiện kích hoạt đổi (12 mục 12.7): biên 0,002, và phía b so **AUC** thay cho
log-loss (b là tổng chuẩn hoá, không phải xác suất; chỉ thứ tự của b ảnh hưởng xếp hạng). Tính lại từ file xuất
(`kich-hoat-b-auc.csv`): b được bật **30 / 30** version đủ dữ liệu (log-loss có biên: 23; lúc chạy: 12), AUC bản học
cao hơn prior ở 28 / 30 và chênh lệch tăng dần (v38: +0,007) trong khi log-loss của chính các bản đó kém hơn (+0,011).
a không đổi (29 / 29).

> 💡 Nói bằng lời thường: lần này người dùng ảo dùng chính trang web (qua API) như người thật, và job học ban đêm thật
> đọc dữ liệu từ database. Mọi dòng dữ liệu job dùng đều khớp với những gì người dùng ảo đã làm, không sót dòng nào;
> bản Python tính lại ra đúng cùng con số. Mỗi đêm có bản trọng số mới, được bật, và lần tìm hôm sau dùng đúng bản đó.
> Trọng số học được không giống hệt trọng số ẩn, nhưng xếp danh sách gần tốt bằng: lấy được 91 % phần lợi mà biết
> trước trọng số ẩn sẽ mang lại.

## File

| File | Nội dung |
|---|---|
| `tom-tat.json` | Mọi số chính (đối chiếu, phát lại, vòng, có ích, mốc w\*) |
| `doi-chieu-a.csv`, `doi-chieu-b.csv` | Đối chiếu theo version: kỳ vọng / xuất ra / thiếu / thừa / lệch |
| `chenh-lech.json` | Danh sách chênh lệch (rỗng) |
| `phat-lai-python.csv` | Lệch lớn nhất Python ↔ Java theo version |
| `kich-hoat-b-auc.csv` | Phía b: AUC và log-loss của bản học / prior trên dữ liệu giữ riêng, bật lúc chạy và theo quy tắc mới |
| `sai-so.csv`, `sai-so-theo-ngay.png` | ‖w − w\*‖₁ và trọng số theo version |
| `vong-khep-kin.csv` | Theo ngày: version phải phục vụ và version log ghi |

Dữ liệu thô (`out/crowded/l1/`: `truth.jsonl`, `state.json`, `l1-observations/`) không đưa lên Git: file xuất quan
sát có ID người dùng và hành vi.

## Chạy lại

```
set EVAL_SCENARIO=crowded
set EVAL_DB=ptutor_eval_l1
cd backend && mvnw.cmd clean package -DskipTests && cd ..
python scripts/recommendation_eval/l1_system.py setup     # ~1 phút; từ chối nếu out/crowded/l1 còn dữ liệu
python scripts/recommendation_eval/l1_system.py run       # ~2 giờ 40; dừng giữa chừng thì chạy lại lệnh này
python scripts/recommendation_eval/l1_system.py analyze   # ~20 giây → thư mục này
```

Cần: Docker (`ptutor-postgres`, `ptutor-ai-service` chỉ để embed — không gọi LLM), cổng 8080 trống, một chuỗi tại một
thời điểm. `run N` dừng sau ngày N.

## Demo trước hội đồng

Không chạy lại 45 ngày khi trình bày; dùng database đã chạy xong.

1. `python scripts/recommendation_eval/l1_system.py serve` (~1 phút tới khi "backend ready").
2. Mở `sai-so-theo-ngay.png` và bảng "Kết quả" ở trên.
3. Đăng nhập admin (tài khoản của migration V3), gọi `GET /api/v1/admin/recommendations/weights`: prior, chế độ,
   version đang kích hoạt (a v31, b v32) và lịch sử version kèm đánh giá với prior.
4. Gọi `POST /api/v1/admin/recommendations/weights/learn` lần nữa: trả "nothing new" / "no new observation" vì dữ liệu
   đã học hết — cho thấy job chạy được bất kỳ lúc nào và không học lại dữ liệu cũ; gọi hai lần cùng lúc thì lần sau
   nhận `409 WEIGHT_LEARNING_RUNNING`.
5. Đăng nhập một học sinh ảo (`eval.s01@ptutor.local`) trên frontend (`npm run dev`), tìm gia sư: log của lần tìm ghi
   `query_context.weights.A.version = 31`.

Muốn cho thấy vòng chạy thật trong lúc trình bày: dựng một database khác bằng `setup` rồi `run 17` (~55 phút, chạy
trước buổi trình bày): ngày 15 có version a đầu tiên, ngày 17 version a đầu tiên được kích hoạt.

## Hạn chế

- Mẫu Thompson lấy theo (mô hình, người dùng, **ngày thật**, version): mọi ngày giả lập cùng một ngày thật, nên nhiễu
  khám phá của một người chỉ đổi khi có version mới (hằng đêm ở đây) — khám phá yếu hơn thực tế một chút.
- Thời gian giả bằng cách lùi mọi cột thời gian của database (163 cột / 47 bảng, kiểm từng cột sau mỗi lần lùi); mốc
  thời gian nằm trong JSON (ví dụ trong `query_context`) không lùi. Không đọc mốc nào như vậy ở đường dữ liệu L1.
- Người dùng ảo và w\*, b\* do nhóm đặt; trọng số chỉ dùng được khi hành vi thật có cấu trúc tương tự.
- Chỉ chiều học sinh tìm gia sư; chiều gia sư tìm học viên chung code (khác hướng) và đã có test.
