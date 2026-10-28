# Kết quả tổng kết (Bước 43b, 27–28/10/2026)

> 💡 **Đây là bộ số dùng cho báo cáo.** Mọi tham số (λ, α, κ, γ, hệ số Q, cổng chặn, cả tham số của bộ mô phỏng) đã
> chọn ở các lượt tinh chỉnh trên thế giới `crowded` hạt giống 20261020 ([`../crowded/`](../crowded/README.md),
> lý do ở [13](../../13-tinh-chinh-sau-danh-gia.md)). Số ở đây lấy trên **một thế giới khác**: cùng công thức sinh,
> hạt giống 20261027, nên gia sư, học sinh, tin đăng và truy vấn đều khác. Không tham số nào được chỉnh lại trên thế
> giới này. Nhờ vậy số đo không bị "luyện" trên chính dữ liệu dùng để báo cáo.
>
> Số trên **dữ liệu mô phỏng có đáp án ẩn** (09 mục 9.1). File gốc nằm ở [`../crowded-final/`](../crowded-final/README.md);
> cách đọc chỉ số: 13 mục 13.0.

**Thế giới.** 600 gia sư, 945 tin dạy, 400 học sinh, 360 tin học, 1 369 truy vấn (1 180 học sinh tìm gia sư, 189 gia
sư tìm học sinh), chia đôi hiệu chỉnh / kiểm tra. Kiểm định trên **587 truy vấn S2T nửa kiểm tra** (0 lần phải dùng
vector hồ sơ) và 97 truy vấn T2S. A1 chạy thật (2 305 lời gọi LLM); embedding đủ cho mọi tin, gia sư, học sinh.
Kiểm tra bộ mô phỏng đạt 6 / 7 tiêu chí 13.3; tương quan Q–rel 0,195 [0,184; 0,206], sát cận dưới 0,2 (hạn chế 4).

**Cấu hình** (09 mục 9.4). **V2-final** là hệ thống như khi phát hành, không ghi đè thuộc tính nào: R = a^0,65 · b^0,35,
chống dồn `ON_SHORTAGE` λ 0,5, nhân (0,7 + 0,3 Q) và cổng chặn G, 2 ô khám phá (vị trí 5, 12), lịch sử H1, **rerank
tắt**, trọng số prior (L1 đánh giá riêng ở mục 5). V2-a / V2-R / V2-RC bật dần các thành phần; ba cấu hình "V2-final −"
tắt một thành phần. Random, Popularity, Semantic, V1, V2-linear xếp lại ứng viên mà V2-a đã log (`baselines.py`).

## 1. Chất lượng xếp hạng (S2T, 587 truy vấn)

| Cấu hình | NDCG@10 | P@5 | EA@10 | MS@10 | Gini | Coverage@20 | T2S NDCG (97) |
|---|---|---|---|---|---|---|---|
| Random | 0,323 | 0,423 | 0,479 | 0,222 | 0,334 | 0,982 | 0,460 |
| Popularity | 0,457 | 0,680 | 0,467 | 0,355 | 0,881 | 0,305 | 0,613 |
| Semantic | 0,431 | 0,529 | 0,544 | 0,305 | 0,576 | 0,856 | 0,539 |
| V1 (thiết kế cũ) | 0,583 | 0,806 | 0,536 | 0,444 | 0,820 | 0,444 | — |
| V2-linear | 0,661 | 0,751 | 0,560 | 0,428 | 0,544 | 0,894 | 0,803 |
| V2-a | 0,669 | 0,760 | 0,555 | 0,428 | 0,575 | 0,878 | 0,804 |
| V2-R | 0,619 | 0,738 | 0,576 | 0,434 | 0,654 | 0,809 | 0,770 |
| V2-RC | 0,619 | 0,739 | 0,576 | 0,434 | 0,657 | 0,802 | 0,770 |
| **V2-final** | **0,607** | **0,700** | **0,572** | **0,425** | **0,667** | **0,803** | **0,750** |
| V2-final − A1 | 0,509 | 0,673 | 0,523 | 0,381 | 0,797 | 0,606 | 0,609 |
| V2-final − H1 | 0,605 | 0,697 | 0,573 | 0,422 | 0,644 | 0,831 | 0,753 |
| V2-final tắt Q | 0,555 | 0,658 | 0,596 | 0,406 | 0,558 | 0,877 | 0,753 |

**Kiểm định ghép cặp theo truy vấn** (Wilcoxon, KTC 95 % bootstrap, `9.6-kiem-dinh.csv`):

| So sánh | ΔNDCG@10 | ΔEA@10 | ΔMS@10 |
|---|---|---|---|
| Random → V2-final | +0,284 [+0,269; +0,299] | +0,094 | +0,203 |
| Popularity → V2-final | +0,150 [+0,139; +0,161] | +0,106 | +0,070 |
| Semantic → V2-final | +0,176 [+0,162; +0,190] | +0,029 | +0,120 |
| V1 → V2-final | +0,024 [+0,013; +0,035] | +0,036 | −0,019 |
| V1 → V2-a | +0,087 [+0,074; +0,099] | +0,018 | −0,017 |
| V2-linear → V2-a | +0,008 [+0,004; +0,012] | −0,005 | −0,001 (p 0,66) |
| V2-a → V2-R | −0,050 [−0,058; −0,044] | +0,021 | +0,006 (p 0,045) |
| V2-R → V2-RC | 0,000 (p 0,89) | 0,000 | 0,000 |
| V2-RC → V2-final − H1 (chỉ ô khám phá) | −0,014 [−0,017; −0,011] | −0,003 | −0,011 |
| V2-final − H1 → V2-final (H1) | +0,002 [−0,002; +0,006] (p 0,43) | −0,001 | +0,003 |
| V2-final − A1 → V2-final (A1) | +0,097 [+0,088; +0,107] | +0,050 | +0,044 |
| V2-final tắt Q → V2-final (Q) | +0,052 [+0,044; +0,060] | −0,024 | +0,019 |

Mọi chênh lệch không ghi p có p < 0,001.

> 💡 **Đọc thế nào.** V2-final hơn mọi cách làm tham chiếu ở cả "phù hợp" (NDCG) lẫn "được nhận" (EA). Phần
> reciprocal (V2-a → V2-R) đổi một ít NDCG lấy EA: đó là chủ ý, vì NDCG chỉ đo mức phù hợp, còn reciprocal kéo lên
> những gia sư có khả năng nhận cao hơn. Chống dồn (V2-R → V2-RC) không đổi gì ở phép tìm tĩnh vì lúc đó chưa có đơn
> chờ nào; tác dụng của nó chỉ thấy trên thị trường (mục 2).

## 2. Thị trường 8 vòng (400 học sinh)

Mỗi vòng, học sinh chưa có gia sư tìm, xem top 10 và gửi tối đa 3 đơn qua API thật; gia sư quyết định theo mô hình ẩn,
trong giới hạn số chỗ. Mọi cấu hình rút **cùng một bộ số ngẫu nhiên** cho cùng (học sinh, tin, vòng), nên so được
theo từng học sinh (`market.csv`, `final-kiem-dinh-thi-truong.csv`).

| Cấu hình | Ghép | Vòng chờ | Bị từ chối / HS | Gini hiển thị | Hiển thị gia sư mới | rel khi ghép |
|---|---|---|---|---|---|---|
| Random | 1,000 | 1,32 | 1,57 | 0,374 | 18,1 % | 1,82 |
| Popularity | 1,000 | 2,94 | 6,64 | 0,752 | 0,03 % | 1,88 |
| V2-a | 1,000 | 1,57 | 2,44 | 0,601 | 13,2 % | 2,31 |
| V2-R | 1,000 | 1,58 | 2,33 | 0,671 | 10,8 % | 2,23 |
| V2-RC | 0,998 | 1,16 | 0,97 | 0,510 | 20,0 % | 2,20 |
| **V2-final** | **1,000** | **1,12** | **0,85** | **0,500** | **20,3 %** | **2,09** |
| V2-final tắt Q | 1,000 | 1,12 | 0,82 | 0,504 | 22,1 % | 2,09 |

Tin của gia sư mới chiếm 18,1 % số tin.

| So sánh (theo học sinh) | Δ bị từ chối / HS | Δ vòng chờ |
|---|---|---|
| Random → V2-final | −0,72 [−0,95; −0,49] | −0,20 [−0,27; −0,13] |
| Popularity → V2-final | −5,79 [−6,30; −5,29] | −1,82 [−1,99; −1,66] |
| V2-a → V2-final | −1,59 [−1,81; −1,37] | −0,45 [−0,52; −0,38] |
| V2-a → V2-R | −0,11 [−0,35; +0,14] (p 0,28) | +0,01 (p 0,79) |
| **V2-R → V2-RC (chống dồn)** | **−1,36 [−1,61; −1,12]** | **−0,42 [−0,51; −0,34]** |
| V2-RC → V2-final | −0,12 [−0,27; +0,04] (p 0,24) | −0,04 (p 0,40) |
| V2-final → tắt Q | −0,03 [−0,20; +0,13] (p 0,39) | 0,00 (p 0,99) |

> 💡 Chống dồn là thành phần làm giảm số lần bị từ chối: giảm hơn một nửa, từ 2,33 xuống 0,97 lần mỗi học sinh. Reciprocal
> một mình không giảm được, vì b đẩy học sinh vào gia sư có lịch sử nhận cao mà không biết họ đã hết chỗ (13.5). So với
> "gia sư tốt nhất lên đầu" (Popularity), mỗi học sinh bớt gần 6 lần bị từ chối và chờ ít hơn gần 2 vòng.

## 3. Độ nhạy (9.9.1)

**Offline** (`9.9.1-do-nhay.csv`, `9.9.1-tornado.png`): mỗi tham số nhân ×0,5 / ×0,75 / ×1,25 / ×1,5, xếp lại 200 ứng
viên đã log của V2-final trên 587 truy vấn. Gốc offline NDCG 0,620 (V2-final đã log 0,607: phép tính offline không có ô
khám phá).

| Tham số | ΔNDCG@10 (×0,5 … ×1,5) | ΔEA@10 | τ top-10 thấp nhất |
|---|---|---|---|
| α (reciprocal) | −0,071 … +0,056 | +0,001 … −0,018 | 0,44 |
| a.need | −0,025 … +0,010 | +0,001 … −0,001 | 0,73 |
| hệ số Q | −0,020 … +0,003 | +0,017 … −0,017 | 0,65 |
| b.hist | +0,020 … −0,013 | +0,010 … −0,009 | 0,77 |
| 10 tham số còn lại (6 trọng số a, 3 trọng số b, prior đặc trưng thiếu) | ≤ 0,010 | ≤ 0,007 | ≥ 0,86 |
| Cổng chặn lịch, cổng "vừa bị từ chối", phạt khiếu nại | 0 | 0 | ≥ 0,9996 |

- **α nhạy nhất**, vì đây là chỗ đánh đổi giữa phù hợp (a) và được nhận (b). Giảm α thì mất NDCG mà gần như không được
  thêm EA. Tăng α thì đổi EA lấy NDCG. 0,65 nằm ngay trước chỗ EA bắt đầu giảm rõ.
- **Hai cổng chặn và phạt khiếu nại không đổi top 10** trong khoảng ×0,5…×1,5. Thứ tự có đổi (348 và 411 truy vấn),
  nhưng 1 134 ứng viên bị chặn không ai vào được top 10, còn gia sư có khiếu nại chỉ vào top 10 có 4 lần. Đây là tham
  số bảo vệ, không phải tham số chất lượng.

**λ (chống dồn) trên thị trường 8 vòng** (offline không đo được λ vì phép tìm tĩnh gần như không có đơn chờ):

| λ | Bị từ chối / HS | Δ so với λ 0,5 (theo học sinh) | Gini | Hiển thị gia sư mới |
|---|---|---|---|---|
| 0,25 | 1,03 | +0,18 [+0,02; +0,35] (p 0,09) | 0,528 | 18,4 % |
| 0,375 | 0,93 | +0,08 [−0,08; +0,24] | 0,509 | 18,3 % |
| **0,5** | **0,85** | — | 0,500 | 20,3 % |
| 0,625 | 0,90 | +0,05 [−0,12; +0,22] | 0,484 | 20,9 % |
| 0,75 | 0,94 | +0,09 [−0,08; +0,26] | 0,486 | 22,5 % |

λ = 0,5 cho số lần bị từ chối thấp nhất, nhưng chỉ λ = 0,25 kém hơn rõ. λ nằm ở vùng ổn định.

## 4. Học trọng số L1 (9.9.4–9.9.5)

`learning.py --run V2-final --diverse`: 3 000 lần tìm × 10 hạt giống, đặc trưng lấy từ log V2-final (1 180 lần tìm),
w\* ẩn = (0,15; 0,20; 0,35; 0,10; 0,10; 0,05; 0,05), prior = (0,30; 0,20; 0,20; 0,10; 0,10; 0,05; 0,05).

| Chiến lược | Regret | So với giữ nguyên prior tương ứng | Sai số trọng số cuối |
|---|---|---|---|
| Cố định (prior) | 1 714 | — | 0,30 |
| **L1 (κ 50, ρ 0,5, γ 0,99)** | **1 388** | **−19,0 % (10/10 hạt giống)** | **0,19** |
| L1 với mặc định cũ (κ 200, ρ 1, γ 1) | 1 545 | −9,9 % | 0,25 |
| Greedy (ρ 0) | 1 410 | −17,7 % | 0,23 |
| L1 từ prior chia đều | 1 780 | −26,7 % so với 2 428 | 0,54 → 0,34 |
| L1 từ prior đảo ngược | 2 716 | −23,3 % so với 3 541 | 1,00 → 0,67 |

- Lưới κ × `max-step` (0,05 / 0,1): κ 20 cho regret 1 335 / 1 366, κ 50 cho 1 388 / 1 409, κ 100 cho 1 491 / 1 493. Mọi
  ô đều 10/10 hạt giống tốt hơn cố định. Bước cập nhật lớn nhất không vượt `max-step`.
- **Drift** (w\* đổi ở lần 1 500), regret sau khi đổi: cố định 1 118; L1 γ 1 là 707, γ 0,99 là 732, γ 0,9 là 684; Greedy
  γ 0,99 là 714.
- L1 tốt hơn Greedy 22 regret, nhỏ hơn nhiều so với độ lệch chuẩn khoảng 100 (thế giới cũ: Greedy tốt hơn 28). Vậy là
  khám phá không đắt, nhưng mô phỏng cũng không cho thấy nó có lợi.
- Không hạt giống nào đưa sai số xuống 0,05: biên [0,5; 2] × prior và nhiễu phản hồi giữ L1 ở gần chứ không tới đúng
  w\* (12.11).
- Vòng học **trong sản phẩm** (API → `/events` → job đêm → kích hoạt → phục vụ) đã kiểm chứng riêng ở
  [`../l1-he-thong/`](../l1-he-thong/README.md), trên thế giới hạt giống cũ.

## 5. Độ trễ (laptop 16 GB, máy rảnh)

`latency.py --config V2-final`, 1 369 lần tìm từ bản sao `_seed`, RAM trống 5,71 → 4,95 GB (chỉ language server Java
chạy kèm). Ngân sách 02 mục 2.3: tổng p95 ≤ 1,5 s.

| Chiều | n | p50 | p95 | max | Pha vector p50 / p95 |
|---|---|---|---|---|---|
| S2T | 1 180 | 205 ms | 291 ms | 617 ms | 132 / 211 ms |
| T2S | 189 | 322 ms | 458 ms | 547 ms | 267 / 389 ms |

0 lần quá thời gian chờ, 0 lần phải dùng vector hồ sơ. Gọi AI service để tạo vector chiếm khoảng 2/3 thời gian mỗi lần
tìm.

## 6. Bảng giả thuyết

| # | Giả thuyết (09, 12, 13) | Kết quả trên thế giới báo cáo | Kết luận |
|---|---|---|---|
| 1 | V2-a tăng NDCG so với V1 | +0,087 [+0,074; +0,099] | **Đạt** |
| 2 | V2-final hơn mọi tham chiếu | Random +0,284, Popularity +0,150, Semantic +0,176, V1 +0,024 (NDCG); EA hơn cả bốn | **Đạt**. Riêng V2-linear có NDCG cao hơn (−0,054), nhưng EA thấp hơn (+0,013 cho V2-final): đánh đổi có chủ đích của reciprocal |
| 3 | Reciprocal (V2-R) tăng EA@10, MS@10 | EA +0,021, MS +0,006 (p 0,045) | **Đạt**, mức nhỏ |
| 4 | Reciprocal giảm số lần bị từ chối | Thị trường −0,11 (p 0,28); phép một lượt còn tăng | **Không đạt** khi đứng một mình; đúng như dự đoán 13.5 |
| 5 | Chống dồn (V2-RC) giảm số lần bị từ chối, rút ngắn thời gian chờ | −1,36 lần / HS, −0,42 vòng (p < 0,001) | **Đạt** |
| 6 | Rerank (V2-RCX) tăng NDCG / P@5 | Không chạy trên thế giới báo cáo; đo ở 13.2: chấm đủ mọi cặp thì không giúp | **Không đạt**, rerank tắt mặc định |
| 7 | Ô khám phá giảm Gini mà NDCG giảm không đáng kể | Gini −0,013, Coverage@20 +0,029; NDCG −0,014 (p < 0,001, khoảng 2 %) | **Đạt một phần**: có cái giá nhỏ nhưng đo được |
| 8 | H1 tăng NDCG | +0,002 (p 0,43) trên phép tìm tĩnh | **Không đạt**: không thấy tác dụng. Thí nghiệm "theo số phiên" của 9.5 chưa làm |
| 9 | A1 (tag do LLM) giúp xếp hạng | +0,097 NDCG, +0,050 EA | **Đạt** |
| 10 | Q giúp đưa gia sư tốt lên | Tổng thể +0,052 NDCG; nhưng bỏ gia sư nổi tiếng ra thì tắt Q **tốt hơn** +0,049 [+0,040; +0,058]; thị trường không khác | **Không đạt theo nghĩa xếp hạng**: lợi ích đến từ cách dựng mô phỏng (13.8). Giữ vì lý do tin cậy |
| 11 | L1 có regret thấp hơn trọng số cố định | −19,0 %, 10/10 hạt giống | **Đạt** |
| 12 | L1 sửa được prior sai | Chia đều −26,7 %, đảo ngược −23,3 %; không bắt kịp L1 từ prior đúng | **Đạt một phần**: biên an toàn giới hạn mức sửa (đánh đổi có chủ đích) |
| 13 | L1 có khám phá hơn Greedy | Chênh 22 regret, độ lệch chuẩn khoảng 100 | **Không phân biệt được** |
| 14 | Tham số tự đặt không phải "điểm may mắn" | 16 / 17 tham số offline đổi NDCG ≤ 0,025 khi nhân ×0,5…×1,5; λ ổn định quanh 0,5; α nhạy (đánh đổi NDCG / EA) | **Đạt**, trừ α |
| 15 | Độ trễ p95 ≤ 1,5 s | S2T 291 ms, T2S 458 ms | **Đạt** |

Độ chính xác LLM (A1 gắn tag, trích lịch; A2 tách câu) không phụ thuộc thế giới mô phỏng; số báo cáo ở
[`../llm/`](../llm/README.md), lấy trên bộ câu độc lập (13.6).

## 7. Hạn chế

1. **Dữ liệu mô phỏng.** Thuộc tính ẩn, mô hình quyết định, mô hình click và văn bản (viết theo mẫu) do nhóm đặt
   (09 mục 9.8). Có hai thế giới, cùng một công thức sinh: thế giới tinh chỉnh và thế giới báo cáo. Số ở đây cho thấy kết luận
   không phụ thuộc hạt giống ngẫu nhiên, nhưng không cho thấy chúng không phụ thuộc công thức sinh.
2. **Tham số của bộ mô phỏng cũng chọn trên thế giới cũ** (13.3), như tham số của thuật toán. Hai thế giới cho các
   con số gần nhau: V2-a NDCG 0,661 so với 0,669; V2-RC từ chối 0,76 so với 0,97.
3. **Q.** Lợi ích đo được của Q đến từ nhóm gia sư nổi tiếng, mà nhóm này do cách dựng luôn có `quality` cao. Bỏ nhóm
   này ra thì Q làm xếp hạng kém đi (NDCG −0,049; thế giới cũ −0,014). Q được giữ ở hệ số 0,3 vì nó mang tín hiệu tin cậy
   (khiếu nại có lỗi, khoá tài khoản, xác minh) mà mô phỏng không đo được (13.8). Cần kiểm lại khi có đánh giá thật.
4. **Q–rel = 0,195 [0,184; 0,206]**, nằm trong dao động giữa các hạt giống (thế giới cũ 0,203 [0,193; 0,214]). Không
   chỉnh lại bộ mô phỏng ([`../crowded-final/`](../crowded-final/README.md)).
5. **H1** chỉ đo trên phép tìm tĩnh với 4 phiên duyệt mỗi học sinh. Thí nghiệm "NDCG theo số phiên" của 9.5 chưa làm.
6. **Độ nhạy offline** xếp lại 200 ứng viên đã log và không tính ô khám phá. α và hệ số Q chỉ đo offline, chưa đo
   trên thị trường. Kernel của đặc trưng không có trong phép đo vì cần chạy lại backend.
7. **Rerank** không đánh giá trên thế giới báo cáo, vì hệ thống phát hành tắt nó (13.2).
8. **Chiều gia sư tìm học sinh (T2S)** chỉ có 97 truy vấn kiểm định; bảng chính và kiểm định là chiều S2T.
9. **L1** chỉ học phía S2T. Vòng học trong sản phẩm được kiểm chứng trên thế giới cũ (`l1-he-thong`), không chạy lại ở
   đây. R tuyệt đối vẫn được dùng ở hai ngưỡng (thông báo chủ động 0,65, nới tiêu chí 0,50; 12.7).
10. **Độ trễ** đo trên laptop phát triển (16 GB, không GPU), chưa đo trên VPS.
11. **V1** dựng lại từ mô tả của bộ tài liệu cũ (09 mục 9.10), không phải mã nguồn cũ.

## 8. File

Mọi file nằm ở [`../crowded-final/`](../crowded-final/README.md).

| File | Nội dung |
|---|---|
| `kiem-tra-mo-phong.csv` | Kiểm tra bộ mô phỏng 13.3, KTC Q–rel |
| `9.7-bang-ket-qua.csv` / `.json`, `9.7-so-sanh-cau-hinh.png` | Mục 1 (cột thị trường một lượt `match_rate`, `rejections`: phép thử tĩnh của `evaluate.py`) |
| `9.6-kiem-dinh.csv` | Kiểm định ghép cặp theo truy vấn |
| `market.csv`, `final-quet.csv`, `final-kiem-dinh-thi-truong.csv` | Mục 2, λ ở mục 3 |
| `9.9.1-do-nhay.csv`, `9.9.1-tornado.png` | Mục 3 |
| `9.9.4-*`, `9.9.5-drift.*` | Mục 4 |
| `latency.csv` | Mục 5 |

**Chạy lại** (database `ptutor_eval_final*`, không đụng `ptutor` / `ptutor_test`):
`EVAL_SCENARIO=crowded EVAL_SEED=20261027 EVAL_OUT=crowded-final EVAL_DB=ptutor_eval_final EVAL_RESULTS=crowded-final`, rồi
`world.py` → `run.py setup` → `run.py search <cấu hình>` (mỗi cấu hình dựng lại từ `_seed`) → `market.py <cấu hình>` →
`evaluate.py` → `sweep_report.py --preset final` → `sensitivity.py` → `learning.py --run V2-final --diverse` →
`latency.py --config V2-final`.
