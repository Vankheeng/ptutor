# 13. Tinh chỉnh kiến trúc sau các lượt chạy đánh giá (26/10/2026)

> 💡 **File này nói về gì:** các lượt chạy mô phỏng ngày 21–22/10/2026 được làm **để xem kiến trúc cần chỉnh chỗ
> nào**, chưa phải để lấy số cho báo cáo. File này ghi lại từng thay đổi đã quyết: **thấy gì trong số liệu → hiểu
> nó thế nào → đổi gì → vì sao không chọn cách khác → làm sao biết đã đúng**. Mục đích kép: (1) làm cơ sở để trả lời
> hội đồng khi bị hỏi "vì sao thiết kế thế này", (2) để đọc là hiểu, không cần đọc code.
>
> **Trạng thái:** đã thống nhất ngày 26/10/2026. T1 (13.2) đã lập trình và đo xong ngày 26/10/2026; các mục khác
> **chưa lập trình** (13.9). Khi một thay đổi được lập trình xong, mục tương
> ứng ở các file 04, 05, 06, 09, 10, 12 sẽ được sửa nội dung chính; file này giữ lại như biên bản quyết định.

## 13.0. Nguồn số liệu và cách đọc

| Kịch bản | Thư mục kết quả | Nội dung |
|---|---|---|
| `base` | `ket-qua/base/` | 120 gia sư, 60 học sinh, 79 truy vấn học sinh → gia sư có ứng viên phù hợp; 7 cấu hình + 5 đối chứng; độ trễ nguội / ấm (`latency.csv`) |
| `base-dev` | `ket-qua/base-dev/` | Lượt chạy cũ trên database dev; có thí nghiệm học trọng số L1 (`9.9.4-*`) |
| `text-only` | `ket-qua/text-only/` | 175 truy vấn có nhu cầu **chỉ nằm trong văn bản** (ví dụ "con hay mất tập trung, cần chia nhỏ bài"), không tag hay bộ lọc nào thể hiện |
| `llm` | `ket-qua/llm/` | Độ chính xác của A1 (gắn tag, trích lịch) và A2 (tách câu tìm kiếm); **nhãn do Claude soạn nháp, nhóm chưa duyệt** |

> 📌 **Cập nhật 26/10/2026 — lần tìm dùng nhầm vector hồ sơ:** khi AI service bận (rerank), embed câu truy vấn quá
> hạn và backend lặng lẽ dùng vector hồ sơ (13.2, "Kết quả thật"). Ở `base`, 7–11 / 79 truy vấn kiểm định của các
> cấu hình có rerank bị như vậy (V2-a, V2-R, V2-RC: 0). Loại chúng ra, **không kết luận nào của `base` đổi**:
> Random → V2-full NDCG +0,060 (p = 0,006), Popularity → V2-full +0,073 (p < 0,001), bỏ A1 → V2-full +0,024
> (p = 0,0015), V2-RC → V2-RCX 0. Ở `text-only` thì khác: 54 % lần tìm có rerank bị như vậy, và đó chính là nguồn
> của "+0,013" (13.2); đo lại sau khi sửa: rerank không giúp. Từ nay log ghi `queryVector` và `evaluate.py`
> loại các lần `PROFILE_FALLBACK` khỏi phép kiểm định (cột `excluded_fallback`).
>
> 💡 Nói gọn: kết luận về A1 và so với các baseline vẫn đứng được; con số về rerank thì không.

> 💡 **Đọc các chỉ số thế nào:** *NDCG@10* — top-10 có đưa người phù hợp lên đầu không (0 → 1, càng cao càng tốt).
> *MS@10* — tỉ lệ người trong top-10 vừa phù hợp vừa sẽ nhận học sinh. *EA@10* — trung bình khả năng được nhận của
> top-10. *Bị từ chối / HS* — trung bình một học sinh bị từ chối mấy lần khi nộp đơn theo danh sách. *p* — xác suất
> chênh lệch chỉ do may rủi; p < 0,05 coi là có ý nghĩa. *KTC 95%* — khoảng chứa chênh lệch thật với độ tin cậy 95%;
> nếu khoảng này chứa 0 thì chưa khẳng định được là có chênh lệch.

## 13.1. Nguyên tắc: tách "giới hạn của mô phỏng" khỏi "lỗi của kiến trúc"

> 💡 Mô phỏng là một thế giới do nhóm tự dựng. Nếu thế giới đó thiếu đúng hiện tượng mà một thành phần sinh ra để xử
> lý, thì thành phần đó sẽ trông vô dụng hoặc có hại — nhưng lỗi nằm ở bài kiểm tra, không ở thành phần. Giống thử
> áo mưa trong phòng khô: áo không giúp gì, nhưng không có nghĩa áo vô dụng.

Hai bằng chứng cho thấy kịch bản `base` **thiếu** hai hiện tượng quan trọng:

| Quan sát | Số liệu | Ý nghĩa |
|---|---|---|
| Gia sư gần như nhận tất cả | EA@10 ≈ 0,83–0,84 ở **mọi** cấu hình, kể cả xếp ngẫu nhiên (0,830); tỉ lệ ghép 96–97% | Không có cạnh tranh chỗ → thành phần "ước lượng ai sẽ nhận" (b, reciprocal) và "chống dồn đơn" không có gì để sửa |
| Điểm chất lượng Q không liên quan tới độ phù hợp | Xếp theo Q (Popularity) NDCG 0,325 **thấp hơn** ngẫu nhiên 0,341 | Trong mô phỏng, "chất lượng" ẩn của gia sư chỉ ảnh hưởng việc học sinh có nhận đề nghị hay không (`world.py`, `p_student_accepts`), không ảnh hưởng độ phù hợp `rel` → hệ số Q chỉ thêm nhiễu |

**Quy tắc rút ra:** không bỏ reciprocal, chống dồn hay hệ số Q dựa trên kịch bản `base`. Các quyết định về chúng
(13.4–13.7) chỉ được chốt trên kịch bản **thị trường đông** đã hiệu chỉnh (13.3).

## 13.2. T1 — Rerank chạy nền, luồng tìm chỉ dùng điểm đã có

> 💡 **Rerank** là bước "đọc kỹ": một mô hình (cross-encoder) đọc cùng lúc câu nhu cầu và văn bản từng tin đăng rồi
> chấm mức liên quan, kỹ hơn so sánh vector (cosine) nhưng chậm hơn nhiều. Hiện tại người dùng phải **chờ** bước này
> trong mỗi lần bấm tìm.

### Thấy gì

| Bằng chứng | Số liệu | Nguồn |
|---|---|---|
| Kịch bản `base`: rerank gần như không đổi kết quả | V2-RC → V2-RCX: NDCG +0,0001 | `base/9.6-kiem-dinh.csv` |
| Kịch bản `text-only`: rerank có giúp, nhưng ít | cosine → rerank (trọng số 0,5): NDCG **+0,013**, KTC [+0,008; +0,018], p < 0,001; MS@10 +0,012 — *26/10: mức này là **ảo**, do dùng nhầm vector hồ sơ, không do rerank (xem "Kết quả thật" bên dưới)* | `text-only/9.6-kiem-dinh.csv` |
| Cho chấm lâu hơn **không** tốt hơn | ngân sách 1,5 s → 4 s: NDCG −0,006 (p = 0,006) | như trên |
| Không lần nào chấm đủ 10 cặp | 0/350 lần tìm, kể cả với 4 s | nhật ký 22/10 |
| Độ trễ | phía học sinh: pha rerank p50 1 508 ms (= ngân sách); tổng p50 2,3 s, p95 2,8 s (mục tiêu ≤ 1,5 s); text-only: p95 2,6 s so với 0,33 s khi không rerank (chậm gấp 8 lần) | `base/latency.csv`, `text-only/9.7-bang-ket-qua.csv` |
| **Cache phía học sinh gần như không trúng** | lượt ấm (chạy lại cùng truy vấn, không xoá cache) phía học sinh vẫn p50 1 507 ms; phía gia sư lượt ấm p50 **9 ms** | `base/latency.csv` |

### Hiểu thế nào

- Lợi ích có thật nhưng nhỏ, và chỉ xuất hiện khi văn bản chứa thông tin ngoài các đặc trưng có cấu trúc.
- Cái giá (bắt người dùng chờ) lớn và trả ở **mọi** lần tìm. Thời gian chờ thêm không đổi lấy chất lượng.
- Cache lẽ ra phải làm lần tìm thứ hai nhanh; phía học sinh không được như vậy → có lỗi. Đọc `RerankService`: điểm
  được lưu theo (mã băm văn bản nhu cầu, tin đăng, mã băm nội dung tin, model) và **chỉ khi một lô 4 cặp trả về
  trước hạn**; lô quá hạn bị bỏ. Hai giả thuyết cần kiểm tra:
  1. *Dồn việc ở AI service:* khi backend bỏ chờ một lô quá hạn, AI service vẫn tiếp tục chấm lô đó; các lô bỏ dở
     xếp hàng và làm mọi yêu cầu sau cũng quá hạn → hầu như không lô nào kịp lưu. Văn bản phía học sinh dài hơn
     (gồm hồ sơ) nên bị nặng hơn phía gia sư.
  2. *Khoá cache thay đổi:* văn bản nhu cầu giữa hai lượt khác nhau (ví dụ hồ sơ học sinh hoặc tag A1 vừa cập
     nhật), nên mã băm khác.

  Cách kiểm tra: đếm số dòng `recommendation_rerank_cache` sau lượt nguội; so mã băm văn bản nhu cầu của cùng một truy
  vấn ở hai lượt; xem log thời gian xử lý của AI service.

### Đổi gì

1. **Sửa lỗi cache trước** (theo kết quả kiểm tra ở trên). Nếu là giả thuyết 1: lô quá hạn vẫn được **lưu khi AI
   service trả về muộn** (ghi bất đồng bộ), và giới hạn số yêu cầu rerank đồng thời gửi sang AI service.
2. **Luồng tìm không chờ rerank:** cặp nào đã có điểm trong cache thì dùng như hiện nay (`f_sem` = 0,7·rerank +
   0,3·cosine); cặp chưa có thì dùng cosine. Trạng thái ghi log: `CACHE` / `PARTIAL` / `NONE`.
3. **Chấm nền:** sau mỗi lần tìm, các cặp top-k chưa có điểm được xếp vào hàng đợi (dùng `ai_enrichment_jobs` hoặc
   một hàng đợi riêng); một worker chấm dần với số yêu cầu đồng thời giới hạn. `RerankPrefetchJob` hằng đêm (tin học
   `OPEN`, tìm kiếm đã lưu) giữ nguyên.
4. Lần tìm sau, lật trang, xếp lại trong phiên (B5), thông báo ghép đôi (C7) dùng điểm đã chấm.
5. Cờ `rerank.mode`: `ONLINE` (cách cũ, giữ để so sánh) / `ASYNC` (mặc định mới) / `OFF`.

### Vì sao không chọn cách khác

| Phương án | Lý do không chọn |
|---|---|
| Giữ nguyên, chỉ chỉnh ngân sách | Đã thử 4 s: chậm hơn mà không tốt hơn |
| Giảm `top-k` xuống 5, cắt văn bản 128 token | Vẫn bắt người dùng chờ; chỉ giảm mức chờ, và văn bản ngắn hơn làm mất đúng phần thông tin mà rerank có ích (13.0, `text-only`) |
| Bỏ hẳn rerank | Mất lợi ích +0,013 ở kịch bản văn bản tự do; với dữ liệu thật văn bản đa dạng hơn mô phỏng nên lợi ích có thể lớn hơn. Bỏ chờ đã loại được gần hết chi phí, nên không cần bỏ chức năng |
| Model rerank nhỏ hơn | Đã so ở bước 11 (nhật ký 12/10): nhanh hơn nhưng chất lượng tiếng Việt kém hơn; vẫn không giải quyết việc chờ |

### Làm sao biết đã đúng

- Lượt ấm phía học sinh: pha rerank p50 < 50 ms (trúng cache).
- `ASYNC`: tổng p95 phía học sinh < 1 s (đo khi máy không thiếu RAM — lượt 22/10 đo khi máy gần hết RAM, embedding
  câu truy vấn mất ~670 ms so với 62–76 ms khi đo riêng).
- `text-only`, lượt thứ hai trở đi: NDCG của `ASYNC` không kém `ONLINE` (cache đã đầy).

### Kết quả thật khi lập trình (26/10/2026)

**Nguyên nhân cache phía học sinh không trúng.** Kiểm tra trên `ptutor_eval_text` (DB base đã bị ghi đè khi dựng
crowded) và log các lượt 21–22/10:

| Kiểm tra | Kết quả |
|---|---|
| Một lô 4 cặp phía học sinh (văn bản 600–830 ký tự), máy rảnh | **3,4–4,4 s**; 1 cặp 0,86–0,95 s (không phải ~175 ms như đo ngày 12/10 với tin ngắn) |
| Hệ quả với ngân sách 1,5 s | lô đầu luôn quá hạn → **không cặp nào được lưu**: base phía học sinh 164/167 lần tìm 0 cặp, cả lượt nguội lẫn ấm; text-only `rerank-w0.5` 350/350 lần tìm 0 cặp. Phía gia sư (văn bản ngắn) bình thường |
| Giả thuyết 2 — khoá cache đổi | **Sai**: `demandText` giống hệt giữa lượt nguội / ấm (167/167) |
| Giả thuyết 1 — dồn việc ở AI service | **Đúng, và có hại hơn dự kiến**: AI service vẫn chấm lô đã bị bỏ chờ; 3 lô song song mất 8,8–10,3 s mỗi lô; `/embed` một câu trong lúc đó 2,0 s (rảnh ~0,3 s) > giới hạn 1 s của backend |
| Hệ quả ẩn | embed câu truy vấn quá hạn → backend **lặng lẽ dùng vector hồ sơ**: text-only cosine-only 0 / 350 lần, `rerank-w0.5` **189 / 350**, `rerank-w0.7` 179, `t4000` 57 |
| Mức "+0,013 NDCG" | ở `rerank-w0.5`, 161/161 truy vấn embed được có top-10 **giống hệt** cosine-only; 185 truy vấn khác đều là lần dùng vector hồ sơ. Loại các lần đó ra: cosine → `rerank-w0.5` NDCG **+0,0000** (n = 82). **Lợi ích thật của rerank chưa từng được đo** |

> 💡 Nói bằng lời thường: rerank chưa bao giờ thật sự chạy trong các lần tìm của học sinh — lô nào cũng quá giờ. Nhưng
> các lô bỏ dở vẫn làm AI service bận, khiến bước tạo vector cho câu tìm kiếm cũng quá giờ, và hệ thống chuyển sang
> dùng vector hồ sơ mà không báo. Chính sự chuyển đổi đó (không phải rerank) đã làm điểm khác đi. Vì vậy con số
> "+0,013" bị rút lại; lập luận "lợi ích nhỏ, cái giá lớn" thì càng đúng hơn.

**Đã làm (đúng hướng ở "Đổi gì", thêm vài điều chỉnh):**

1. `rerank.mode` = `ONLINE` / `ASYNC` / `OFF` (`ASYNC` là mặc định lúc lập trình; sau khi đo, đổi thành `OFF`, xem
   cuối mục); `rerank.enabled = false` vẫn là `OFF`. Ở `ASYNC` lần tìm
   chỉ đọc cache (`CACHE` / `PARTIAL` / `NONE`) và xếp hàng cặp thiếu; ở `ONLINE` cặp quá hạn cũng được xếp hàng
   (thay cho "lưu khi kết quả về muộn": cùng mục đích, không phải giữ kết nối HTTP đã bỏ).
2. Worker chấm nền `RerankQueue`: một luồng, mỗi lần 2 cặp cùng văn bản nhu cầu, chống trùng, sức chứa 2 000.
3. AI service: `/rerank` trên executor riêng một luồng; 2 luồng ONNX; chấm từng cặp một; trước mỗi cặp nhường
   `/embed` đang chạy.
4. Log ghi `queryVector` (`QUERY` / `PROFILE_FALLBACK` / `PROFILE`); `evaluate.py` đếm và loại `PROFILE_FALLBACK`.
5. AI service huỷ yêu cầu rerank còn đang chờ lượt khi người gọi đã bỏ chờ (lần đo đầu cho thấy các lô ONLINE bị
   bỏ vẫn chặn executor một luồng và làm mọi lần gọi của worker quá hạn 120 s).
6. Pha rerank đọc văn bản và mã băm đã lưu khi embed (`embedding_source_text`, `embedding_content_hash`) trong một
   truy vấn, thay cho dựng lại văn bản 10 tin mỗi lần tìm (~120 ms); tin chưa embed vẫn dựng như cũ.

**Điều chỉnh so với dự kiến — worker nhường lần tìm.** Đo `/embed` (model thật, máy rảnh p50 ~0,29 s) khi rerank chạy
liên tục cùng lúc:

| AI service | `/embed` p50 / p95 khi rerank chạy cùng lúc |
|---|---|
| Cũ (threadpool chung) | 724 / 1 107 ms |
| Executor riêng, 2 luồng rerank | 595 / 691 ms |
| Executor riêng, 1 luồng rerank | 581 / 635 ms |
| + nhường embed, 2 luồng, 1 cặp mỗi lần | 572 / 1 036 ms |

Kể cả rerank chỉ một luồng, embed vẫn chậm gấp đôi: hai model lớn **tranh băng thông bộ nhớ** của laptop, không chỉ
tranh lõi CPU; một cặp (~1–1,3 s) không ngắt được giữa chừng. Vì vậy tiêu chí ban đầu "`/embed` < 0,5 s khi rerank
nền đang chạy" **không đạt được trên máy này** nếu hai việc thật sự chạy cùng lúc. Quyết định (người dùng đồng ý
26/10/2026): worker **nhường lần tìm** — chỉ chấm khi không có lần tìm nào đang chạy (bộ đếm vào / ra) **và** đã yên
ít nhất 2 s (`rerank.worker-quiet-ms`); chống đói: cặp đã chờ quá 10 phút (`rerank.worker-max-wait-ms`) vẫn được chấm,
từng cặp một. Giới hạn này là của laptop: khi triển khai thật, đặt rerank ở tiến trình hoặc máy riêng thì không cần
nhường nữa.

> 💡 Giống một người dọn dẹp trong cửa hàng: khách vào thì ngừng tay, vắng khách vài giây mới làm tiếp. Nếu khách
> đông mãi, việc nào đã đợi quá 10 phút vẫn được làm, mỗi lần một việc nhỏ.

**Tiêu chí thay cho "Làm sao biết đã đúng" (người dùng chốt 26/10/2026).** Trong một chuỗi tìm kiếm liên tục khi
hàng đợi rerank còn việc:

- tỉ lệ lần tìm `PROFILE_FALLBACK` ≤ 1 % — **tiêu chí chính**, vì chính fallback đã làm sai kết quả đánh giá;
- embed câu truy vấn p50 < 0,5 s, p95 < 0,8 s;
- lần tìm trùng đúng lúc một cặp đang chấm dở chỉ chậm thêm tối đa ~1,3 s;
- hai tiêu chí cũ vẫn giữ: lượt ấm pha rerank p50 < 50 ms; `text-only` lượt thứ hai NDCG của `ASYNC` không kém
  `ONLINE`.

**Số đo sau khi lập trình** (`ptutor_eval_text`, 175 truy vấn × 2 lượt mỗi cấu hình, 26/10/2026; RAM trống 3,3–4,1 GB;
`ket-qua/text-only/`):

| Lượt | Trạng thái rerank | Lần dùng vector hồ sơ | Pha rerank p50 | Pha vector p50 / p95 | Tổng p95 | NDCG@10 |
|---|---|---|---|---|---|---|
| cosine-only | — | 0 | 0 | 150 / 267 ms | 329 ms | 0,5251 |
| ONLINE, cache nguội | `TIMEOUT` 350/350 | 0 | 1 648 ms | 311 / 528 ms | 2 270 ms | 0,5251 |
| ASYNC, cache nguội | `NONE` 350/350 | 0 | 119 ms | 151 / 242 ms | 417 ms | 0,5251 |
| ASYNC, lượt 2 (worker đã chấm 3 500 cặp trong 70 phút) | `CACHE` 350/350 | 0 | 119 ms | 151 / 255 ms | 420 ms | 0,5237 |
| ONLINE, lượt 2 | `CACHE` 350/350 | 0 | 120 ms | 151 / 238 ms | 450 ms | 0,5237 |
| V2-full, hàng đợi còn việc, worker chấm chen 60 lần giữa các lần tìm | `NONE` | 0 | 133 ms | 248 / 514 ms (max 651) | 705 ms (max 840) | — |

| Tiêu chí | Kết quả |
|---|---|
| Lần dùng vector hồ sơ ≤ 1 % (tiêu chí chính) | **0 / 1 400** — đạt (22/10: 189 / 350) |
| Embed p50 < 0,5 s, p95 < 0,8 s khi hàng đợi còn việc | 248 / 514 ms — đạt |
| Trùng cặp đang chấm chỉ chậm thêm ≤ ~1,3 s | pha vector max 651 ms, tổng max 840 ms — đạt |
| ASYNC tổng p95 < 1 s | 417–705 ms — đạt |
| Lượt 2: ASYNC không kém ONLINE | bằng nhau (0,0000) — đạt |
| Lượt ấm pha rerank p50 < 50 ms | 119 ms — **không đạt** lúc đo: mỗi lần tìm dựng lại văn bản 10 tin để lấy mã băm. Đã sửa (đọc `embedding_source_text` / `embedding_content_hash` đã lưu trong một truy vấn; test xác nhận đúng là văn bản rerank chấm); theo người dùng, chỉ kiểm bằng test, chưa đo lại |

**Lợi ích thật của rerank.** Khi đã chấm đủ top-10 mọi truy vấn: cosine-only → ASYNC lượt 2 NDCG **−0,0014**,
KTC [−0,0039; +0,0011]; MS@10 0,0000. Rerank có đổi thứ tự trong top-10 nhưng **không làm thứ tự tốt hơn**, kể cả ở
kịch bản mà nhu cầu chỉ nằm trong văn bản.

> 💡 Nói bằng lời thường: lần đầu tiên rerank được chạy đúng cách (chấm đủ mọi cặp, không ai phải chờ, không dùng
> nhầm vector), và kết quả là nó không giúp gì trên dữ liệu mô phỏng.

**Quyết định (người dùng chọn 26/10/2026): mặc định `rerank.mode = OFF`.** Lý do: không có lợi ích đo được; model
rerank tốn ~1,1 GB RAM của AI service (đo: 2,26 → 1,16 GB khi tắt) và CPU nền trên máy 16 GB. AI service cũng tắt theo
(`AI_RERANK_ENABLED=false` trong `docker-compose.yml`); khi `OFF` không xếp hàng, không gọi `/rerank`, job chấm sẵn
ban đêm bỏ qua (có test). Code `ASYNC` giữ nguyên, bật lại bằng cấu hình.

**Điều kiện bật lại.** Mô phỏng chỉ có sáu loại nhu cầu viết bằng lời và văn bản do nhóm sinh ra; văn bản thật đa
dạng hơn, nên có thể rerank giúp ở đó. Chỉ bật lại khi có bằng chứng trên dữ liệu thật:

1. Có người dùng thật và log click đủ (đề xuất: ≥ 300 lần tìm có ít nhất một click).
2. Chạy ở chế độ **ghi log mà không dùng**: bật `ASYNC` để có điểm rerank trong log từng cặp, nhưng xếp hạng vẫn như
   `OFF`. *(Chế độ "chỉ ghi" này chưa có trong code — cần thêm một giá trị `rerank.mode` khi tới lúc.)*
3. Ngoại tuyến, so hai thứ tự (có và không có điểm rerank) với các click thật: NDCG theo click, KTC 95 %.
4. Bật `ASYNC` khi chênh lệch dương và KTC không chứa 0; ghi kết quả vào file này.


> 💡 **Câu trả lời khi bị hỏi "rerank có tác dụng không"** (sửa 26/10/2026): "Trên dữ liệu mô phỏng thì không. Lần
> đo đầu tưởng có +0,013 NDCG, nhưng kiểm tra lại thì đó là do hệ thống dùng nhầm vector hồ sơ khi AI service quá tải
> — rerank thật ra chưa chấm được cặp nào. Sau khi chuyển rerank sang chạy nền và chấm đủ mọi cặp, chênh lệch là
> −0,001 (KTC chứa 0). Nhóm giữ cơ chế chạy nền nhưng tắt mặc định, và đặt điều kiện bật lại: chứng minh được lợi ích
> trên click của người dùng thật."

## 13.3. T3 — Hiệu chỉnh kịch bản "thị trường đông"

> 💡 Đây là bài kiểm tra có **cạnh tranh thật**: nhiều ứng viên hơn, gia sư giỏi ít chỗ, không phải ai cũng được
> nhận. Thiếu nó thì không đánh giá được reciprocal, chống dồn, slot khám phá và chiều gia sư tìm học viên.

### Thấy gì

- 13.1: gia sư gần như nhận tất cả; Q không liên quan tới độ phù hợp.
- Chiều gia sư → học viên chỉ có 58 truy vấn; V2 (NDCG 0,553) không hơn xếp theo Q (0,562) hay ngẫu nhiên (0,538).
- Kịch bản crowded đã dựng dữ liệu (600 gia sư, 300 học sinh, bản sao `ptutor_eval_seed`) nhưng bước mô phỏng thị
  trường lỗi ở `restore()`, chưa có kết quả.

### Đổi gì (trong `world.py`, kịch bản `crowded`)

1. **Xác suất nhận thấp hơn và có giới hạn chỗ:** chỉnh hệ số chặn của mô hình quyết định (`BETA["b0"]`) để trung bình
   P(nhận) của các đơn nằm trong **0,4–0,6**; gia sư "nổi tiếng" (khoảng 15%) chỉ 1–2 chỗ.
2. **Q có tương quan vừa phải với độ phù hợp:** "chất lượng" ẩn của gia sư cộng một phần nhỏ vào mức hấp dẫn và vào
   `rel` (ví dụ gia sư chất lượng cao được +1 mức khi đã phù hợp cơ bản), sao cho tương quan giữa Q và `rel` khoảng
   0,2–0,4 — có liên quan nhưng không quyết định, như ngoài đời.
3. **Sửa `restore()`** (tạo lại database từ bản sao) rồi chạy mô phỏng thị trường 8 vòng.

### Vì sao chỉnh mô phỏng mà không chỉnh thuật toán trước

Nếu tối ưu thuật toán trên một thế giới không có cạnh tranh, kết quả sẽ là "tắt mọi thứ liên quan tới cạnh tranh" —
đúng cho thế giới đó nhưng sai cho nền tảng thật. Thứ tự đúng: làm cho bài kiểm tra có hiện tượng cần kiểm tra, rồi
mới chỉnh thuật toán.

### Làm sao biết đã đúng (kiểm tra bộ mô phỏng, trước khi đọc kết quả thuật toán)

- Trung vị số ứng viên sau lọc cứng ≥ 50 ở cả hai chiều.
- EA@10 của xếp ngẫu nhiên trong khoảng 0,4–0,6 (không còn bão hoà).
- Xếp theo Q tốt hơn ngẫu nhiên một chút (không còn kém hơn).
- Xếp theo Q có số lần bị từ chối **cao nhất** (dồn vào người giỏi) — hiện tượng mà reciprocal và chống dồn sinh ra để
  xử lý.

## 13.4. T4 — Chống dồn đơn chỉ phạt khi thật sự dồn

> 💡 **Chống dồn đơn** (`b_cap`): tin dạy có nhiều đơn đang chờ so với số chỗ còn lại thì bị hạ điểm, để học sinh
> không cùng nộp vào một người rồi cùng bị từ chối.

### Thấy gì

V2-R → V2-RC (bật chống dồn) trên `base`: NDCG −0,008 (p = 0,04), EA@10 −0,003 (p = 0,04), số lần bị từ chối 0,22 →
**0,27** (tăng, ngược mục đích).

### Hiểu thế nào

Công thức hiện tại `b_cap = r / (r + 0,5·n)` (r = số chỗ còn lại, n = số đơn đang chờ) **phạt ngay từ đơn chờ đầu
tiên**, kể cả khi còn thừa chỗ. Trên thị trường thừa chỗ, nó chỉ đẩy những gia sư tốt (thường có vài đơn chờ) xuống
dưới, nhường chỗ cho người kém phù hợp hơn → học sinh nộp vào người kém phù hợp → bị từ chối nhiều hơn.

### Đổi gì

```
b_cap = 1                                   khi n < r      (còn đủ chỗ cho mọi đơn đang chờ: không phạt)
b_cap = r / (r + λ · (n − r + 1))           khi n ≥ r      (bắt đầu dồn)
```

| Còn chỗ r | Đang chờ n | Cũ (λ = 0,5) | Mới (λ = 0,5) |
|---|---|---|---|
| 3 | 1 | 0,86 | **1,00** |
| 3 | 2 | 0,75 | **1,00** |
| 3 | 3 | 0,67 | 0,86 |
| 1 | 1 | 0,67 | 0,67 |
| 1 | 3 | 0,40 | 0,40 |

Chiều gia sư → học viên (`b′_cap`, mỗi tin học chỉ nhận một gia sư, r = 1) không đổi với n ≥ 1, chỉ khác khi n = 0
(cả hai cách đều = 1).

Quét λ ∈ {0,25; 0,5; 1} trên crowded.

### Vì sao không chọn cách khác

| Phương án | Lý do không chọn |
|---|---|
| Bỏ chống dồn | Chỉ có bằng chứng trên thị trường thừa chỗ (13.1); trên thị trường thật, dồn đơn là vấn đề đã được ghi nhận (Su và cộng sự, 2022) |
| Giữ công thức, chỉ giảm λ | Vẫn phạt khi chưa dồn; chỉ phạt nhẹ hơn |
| Tối ưu toàn cục (ghép cả thị trường) | Nặng, cần nhiều tin đăng mở cùng lúc — vẫn là hướng phát triển (10 mục 10.5) |

### Làm sao biết đã đúng

Trên crowded, so V2-R / V2-RC cũ / V2-RC mới: **số lần bị từ chối và số vòng chờ giảm**, Gini lượt hiển thị giảm,
NDCG không giảm quá 0,01. Chọn λ theo điểm cân bằng của các chỉ số này.

## 13.5. T5 — Reciprocal có trọng số, a nặng hơn b

> 💡 **Reciprocal** gộp hai góc nhìn: a = "người tìm có thích ứng viên này không", b = "ứng viên có khả năng đồng ý
> không". Hiện dùng trung bình điều hoà `2ab/(a+b)`, coi hai phía nặng như nhau và bị kéo mạnh về phía nhỏ hơn.

### Thấy gì

V2-a → V2-R (bật reciprocal) trên `base`: số lần bị từ chối 0,25 → 0,22 (đúng hướng), nhưng MS@10 **−0,024**,
KTC [−0,036; −0,014], p < 0,001 — top-10 có ít người "vừa phù hợp vừa sẽ nhận" hơn.

### Hiểu thế nào

b là ước lượng thô (nhiều gia sư ít lịch sử nhận / từ chối, nên b gần mức chung), còn a dựa trên nhiều đặc trưng
hơn. Trung bình điều hoà cho hai đại lượng có độ tin cậy khác nhau một vai trò như nhau → phần nhiễu của b làm đảo
thứ tự những người phù hợp.

### Đổi gì

```
R = a^α · b^(1 − α)          (trung bình nhân có trọng số; α = 0,5 gần với cách gộp hiện tại)
```

Quét α ∈ {0,5; 0,65; 0,8} trên crowded; giữ trung bình điều hoà làm một cấu hình so sánh. Tham số
`reciprocal.alpha`, đưa vào phân tích độ nhạy.

### Vì sao chọn trung bình nhân có trọng số

- Vẫn giữ tinh thần hai chiều: b = 0 thì R = 0 (không ai đề xuất người chắc chắn từ chối).
- Có một tham số rõ nghĩa ("tin a bao nhiêu so với b") để chỉnh và giải thích.
- Trung bình điều hoà có trọng số cũng được, nhưng khó giải thích hơn và nhạy với b nhỏ hơn mức cần.
- Không bỏ reciprocal: bằng chứng hiện có chỉ ở thị trường thừa chỗ (13.1), và nó đã giảm số lần bị từ chối ngay cả
  ở đó.

### Làm sao biết đã đúng

Trên crowded: α được chọn cho số lần bị từ chối thấp hơn V2-a mà MS@10 không kém V2-a; ghi bảng đầy đủ các α.

## 13.6. T2 — Sửa prompt trích lịch và tách câu (A1, A2)

> 💡 A1 là thành phần có tác dụng rõ nhất trong mọi lượt chạy (bỏ A1: NDCG −0,022, p = 0,003). Cải thiện phần còn yếu
> của nó là việc rẻ nhất mà lợi rõ nhất.

### Thấy gì (`ket-qua/llm/chi-tiet.json`)

| Phần | Kết quả | Kiểu lỗi chính |
|---|---|---|
| A1 gắn tag | F1 0,97 | — |
| A1 trích lịch | đúng toàn bộ ngày 77%; recall theo ngày 0,77; IoU khung giờ 0,97 | 19/60 câu sai; **phần lớn là câu chỉ có giờ bắt đầu thì trả về rỗng**: "thứ 2 đến thứ 6 từ 18h", "thứ 3, 5, 7 lúc 20h", "17h30 thứ 6", "chủ nhật cả ngày", "cuối tuần cả ngày"; một lỗi thật: "Sau 20h tối thứ 2, 4" → thứ 2 và **thứ 5** |
| A2 tách câu | đúng toàn bộ 91% câu | "gia sư giỏi" bị hiểu là học sinh giỏi (2 câu); "buổi tối" không nói ngày → điền cả 7 ngày |

### Hiểu thế nào

Prompt `/enrich` (`ai-service/app/llm.py`) chỉ quy định "tối / chiều / sáng" không rõ giờ; **không nói gì** về giờ bắt
đầu không có giờ kết thúc hay "cả ngày", nên model chọn cách an toàn là bỏ trống. Một phần "lỗi" là do quy ước của bộ
nhãn (ví dụ "sau 8h tối" kết thúc 22:00 hay 21:00) — quy ước này do Claude tự đặt khi soạn nhãn.

### Đổi gì

1. Thêm quy tắc vào prompt `/enrich`: chỉ có giờ bắt đầu → kéo dài 2 tiếng; "cả ngày" → 08:00–21:00; "sau X giờ tối"
   → tới 22:00; "các ngày thường" → thứ 2–6; kèm 2–3 ví dụ ngắn.
2. Prompt `/parse-query`: "gia sư giỏi" là mong muốn về gia sư, không phải trình độ học sinh; nói buổi mà không nói
   ngày thì không điền ngày.
3. **Nhóm duyệt lại bộ nhãn** (200 nhãn trong `llm_eval_cases.py`) và thống nhất quy ước với prompt; tự viết thêm
   20–30 câu không nhìn prompt để kiểm tra độc lập.

### Làm sao biết đã đúng

Chạy lại `llm_eval.py`: trích lịch đúng toàn bộ ngày ≥ 90%, không còn câu "có giờ bắt đầu → rỗng"; A2 giữ ≥ 91% và
hết hai kiểu lỗi trên; tag không giảm. Sau đó chạy lại A1 cho dữ liệu đánh giá (`enrichment` chạy lại) và so NDCG.

## 13.7. T6 — L1: giảm độ mạnh prior và mức khám phá

> 💡 **κ (độ mạnh prior)** = trọng số do nhóm đặt "đáng giá" bao nhiêu quan sát. κ lớn → hệ thống tin trọng số ban
> đầu lâu hơn, học chậm hơn. **Khám phá** = cố tình thử bộ trọng số hơi khác để học thêm.

### Thấy gì (`base-dev/9.9.4-hoc-trong-so.csv`, 3 000 lần tìm × 10 lần lặp)

| Cấu hình | Regret (càng thấp càng tốt) | Sai số trọng số | Bước lớn nhất |
|---|---|---|---|
| Trọng số cố định | 1 128 | 0,300 | 0 |
| L1, κ = 200 (sau khi sửa prior hệ số chặn và thang prior) | **1 144** (kém hơn) | 0,229 | 0,022 |
| L1, κ = 50 | ~6% thấp hơn cố định | 0,15 | — |
| Greedy (không khám phá) | 1 134 | 0,213 | 0,016 |

### Hiểu thế nào

Sau khi đưa prior về đúng thang phần thưởng (β ≈ 2,2), κ = 200 trở thành prior **rất mạnh**: trọng số gần như đứng
yên, nên không thu được lợi ích từ việc học. Khám phá (Thompson) không hơn Greedy vì 3 000 lần tìm chỉ lặp lại 106
truy vấn — ít thông tin mới để khám phá.

### Đổi gì

1. `learning.prior-strength` mặc định **50**; quét κ ∈ {20; 50; 100} cùng `max-step` ∈ {0,05; 0,1}.
2. `learning.exploration-scale` mặc định **0,5** (bớt "nhảy" thứ tự giữa các ngày khi khám phá không đem lại lợi ích
   rõ).
3. Chạy lại thí nghiệm học trên kịch bản có truy vấn đa dạng (mỗi lần tìm một truy vấn mới từ thế giới crowded) và
   kịch bản sở thích thay đổi giữa chừng (`drift`, 09 mục 9.9 / kế hoạch 21/10 mục 2.5).

### Vì sao không bỏ L1

L1 là cơ chế để nền tảng tự chỉnh khi có người dùng thật; giá trị của nó nằm ở chỗ **an toàn** (không bao giờ ra
khỏi biên quanh trọng số nhóm đặt) và **không tệ hơn** trọng số cố định khi ít dữ liệu. Chọn κ bằng thí nghiệm là
đúng cách đặt câu hỏi; nếu kết quả cuối cho thấy L1 chỉ ngang trọng số cố định, báo cáo nêu thẳng như vậy.

### Làm sao biết đã đúng

Với κ chọn được: regret thấp hơn trọng số cố định ở cả kịch bản truy vấn đa dạng và `drift`; trong `drift`, có quên
(γ = 0,99) bắt kịp nhanh hơn không quên.

## 13.8. T7 — Tách hệ số Q và các cổng chặn để xem cái nào làm hạ điểm

> 💡 Điểm cuối = R × (0,7 + 0,3·Q) × G, trong đó G gồm hai "cổng chặn mềm": lịch không giao nhau (× 0,4), gia sư vừa
> từ chối học sinh này (× 0,5). Câu hỏi: phần nhân thêm này có làm thứ tự tốt hơn không?

### Thấy gì

Bản cộng tuyến tính đơn giản (V2-linear) NDCG 0,411, nhỉnh hơn V2-a 0,408 (chưa có ý nghĩa, p = 0,26) và V2-full
0,394.

### Đổi gì

Không đổi công thức ngay. Thêm ba cấu hình tách: tắt hệ số Q; tắt cổng lịch; tắt cổng từ chối — chạy trên `base` và
crowded. **Chỉ kết luận về Q trên crowded** (trên `base`, Q không liên quan tới độ phù hợp do cách dựng mô phỏng,
13.1).

### Làm sao biết

Thành phần nào làm NDCG / số lần bị từ chối xấu đi có ý nghĩa trên crowded thì chỉnh (giảm hệ số, đổi ngưỡng) hoặc bỏ,
và ghi lại vào file này.

## 13.9. Thứ tự làm và trạng thái

| # | Thay đổi | Mục | Trạng thái |
|---|---|---|---|
| 1 | Sửa lỗi cache rerank phía học sinh → rerank chạy nền (`ASYNC`) | 13.2 | **Đã làm 26/10/2026.** Có `ASYNC`, worker nhường lần tìm, AI service huỷ việc bị bỏ, ghi `queryVector`; đo xong. Mặc định đổi thành `OFF` vì rerank không có lợi ích đo được |
| 2 | Sửa prompt lịch / tách câu; nhóm duyệt nhãn; chạy lại `llm_eval` | 13.6 | Đã quyết, chưa code |
| 3 | Hiệu chỉnh mô phỏng crowded, sửa `restore()`, chạy thị trường | 13.3 | Đã quyết, chưa code |
| 4 | Chống dồn chỉ phạt khi dồn (quét λ); reciprocal có trọng số (quét α) | 13.4, 13.5 | Đã quyết, chưa code — chốt tham số trên crowded |
| 5 | L1: κ = 50, khám phá 0,5, thí nghiệm truy vấn đa dạng và `drift` | 13.7 | Đã quyết, chưa code |
| 6 | Tách Q và các cổng chặn | 13.8 | Đã quyết, chưa code |

## 13.10. Câu hỏi phản biện dự kiến

| Câu hỏi | Trả lời ngắn | Cơ sở |
|---|---|---|
| Vì sao không bỏ rerank khi nó gần như không giúp? | (sửa 26/10/2026) Đã tắt mặc định: chấm đủ mọi cặp thì không giúp (NDCG −0,001, KTC chứa 0); mức +0,013 lúc trước là do dùng nhầm vector hồ sơ. Giữ code chạy nền (không bắt người dùng chờ) và chỉ bật lại khi chứng minh được trên click thật | 13.2 |
| Sao lần đo trước lại ra "+0,013 có ý nghĩa"? | AI service quá tải vì các lô rerank bị bỏ dở, embed câu truy vấn quá hạn, backend lặng lẽ dùng vector hồ sơ (54 % lần tìm). Từ nay log ghi `queryVector` và bộ đánh giá loại các lần đó; ở `base` loại ra thì không kết luận nào đổi | 13.0, 13.2 |
| Chống dồn làm số lần bị từ chối tăng, sao vẫn giữ? | Vì công thức cũ phạt cả khi chưa dồn; đã sửa để chỉ phạt khi số đơn chờ vượt số chỗ, và kiểm tra trên thị trường có cạnh tranh | 13.1, 13.4 |
| Sao không đánh giá luôn trên kịch bản base? | Kịch bản base không có cạnh tranh chỗ và Q không gắn với độ phù hợp — thiếu đúng hiện tượng cần kiểm tra | 13.1 |
| Reciprocal làm giảm MS@10, có mâu thuẫn mục tiêu? | Đó là dấu hiệu b nhiễu được cho trọng số ngang a; đã thêm tham số α để chỉnh mức tin b | 13.5 |
| Trọng số tự học (L1) có thật sự học được không? | Với prior quá mạnh thì không; chọn κ bằng thí nghiệm, và cơ chế luôn ở trong biên an toàn quanh trọng số nhóm đặt | 13.7 |
| Độ chính xác LLM cao vậy có đáng tin? | Bộ nhãn đầu do Claude soạn theo quy ước của prompt nên có thể lạc quan; nhóm duyệt lại và thêm câu viết độc lập trước khi dùng số này (cập nhật dòng này khi đã làm) | 13.6 |
