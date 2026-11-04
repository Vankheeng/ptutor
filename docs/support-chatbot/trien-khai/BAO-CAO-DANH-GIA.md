# Báo cáo đánh giá trợ lý hỗ trợ (bản tham khảo của Claude)

> Ngày chạy: 2026-11-04. Nhánh `feat/support-chatbot-claude`. Thiết kế: [10-danh-gia.md](../10-danh-gia.md),
> [13-thiet-ke-eval.md](../13-thiet-ke-eval.md). Mọi số dưới đây chạy lại được bằng lệnh ở mục 2.

## 0. Đọc trước: số liệu này đo cái gì, không đo cái gì

| Điều kiện | Ảnh hưởng tới cách đọc số |
|---|---|
| **Chưa có model ngôn ngữ** (người dùng chưa chọn model). Mọi lần chạy dùng **Actor trích xuất** tất định (`eval/scripted_policy.py`): chép câu có nhiều từ chung nhất với câu hỏi, gọi tool theo từ khoá. | Đo **harness, truy xuất, công cụ, an toàn** (tầng T0–T2). **Không** đo chất lượng diễn đạt, khả năng từ chối đúng lúc hay chọn tool của một LLM. Tỉ lệ đạt tổng (65,7%) **không** phải chất lượng trợ lý khi chạy thật. |
| **Embedding giả lập** (`fake-hash-v1`, băm từ / cặp từ / 3 ký tự, 1024 chiều) | Gần với tìm kiếm từ khoá. E1 "vector vs lai" vì thế gần như không khác nhau; số với bge-m3 thật ở mục 6.1. |
| Kho tri thức **66 tài liệu, còn 95 câu hỏi todo mở ở 50 tài liệu** (bản khởi tạo + 1 tài liệu M17). Eval đọc thẳng thư mục `knowledge-base/` (kho trong bộ nhớ), tương đương nạp với `--allow-todo`. | Câu hỏi rơi vào phần còn todo không có đáp án chính thức; nhóm `NO_ANSWER` đo đúng điều này. |
| Công cụ tra cứu chạy bằng **fixture** (`eval/fixtures/scenarios/*`), không gọi backend thật. | Đo đường đi qua ToolGate / LoopGuard / lọc trường thật; không đo hợp đồng trường với backend (T2 contract test cần backend chạy). |
| Độ trễ đo trên máy dev, chạy tuần tự, Actor giả lập trả lời tức thì. | Bảng B2 chỉ có ý nghĩa cho phần **không phải model** (truy xuất, chặn phạm vi). Token là **ước lượng** (số ký tự / 4) trên prompt thật. |

**Tầng T3–T4 (model thật) chưa chạy** — chờ người dùng chọn model và đặt khoá trong `.env` (mục 9 có ước tính chi phí).

## 1. Tóm tắt

- **An toàn: 26/26 case (100%)** ở mọi cấu hình; mọi grader an toàn (lọc trường nhạy cảm, nguồn gốc ID, đối tượng trích
  dẫn, không có lời gọi ghi) đạt 100% trên 444 lượt của tập test × 3 lần.
- **Truy xuất (tập test, 110 câu một lượt)**: Recall@6 = 90,2%, MRR = 0,762 (embedding giả lập). Với bge-m3 thật trên
  Postgres (220 câu, đo ở M6): truy xuất lai 89,5% so với chỉ vector 76,8%.
- **Tra cứu (LOOKUP, test)**: 80% case gọi đúng công cụ; 100% số liệu trong câu trả lời có trong kết quả tool; 0 lần lộ
  trường nhạy cảm trong ngữ cảnh model.
- **Eval tìm ra 5 lỗi thật của harness** và đã sửa (mục 5) — đây là kết quả hữu ích nhất của đợt này.
- **E2 (verifier) và E4 (viết lại câu hỏi) không cho kết luận được với Actor giả lập**: Actor chép nguyên văn nên
  verifier không có gì để bắt; sổ tay `focus` đã đủ để tham chiếu đúng đối tượng. Cần chạy lại ở T3.
- **Cổng hồi quy hoạt động**: chặn đúng một thay đổi ngưỡng chặn phạm vi làm chặn nhầm câu trong phạm vi (mục 7.3).

## 2. Chạy lại

```bash
cd assistant-service
.venv/Scripts/python -m eval.build_datasets          # golden từ cau-hoi-mau.csv (220 câu)
.venv/Scripts/python -m eval.build_lookup_cases      # LOOKUP, nhiều lượt, ngoài phạm vi, bẫy
.venv/Scripts/python -m eval.build_verifier_claims   # 76 khẳng định cho verifier
bash eval/run_experiments.sh stage8                  # baseline dev + test (gate, k=3), E1, E2, E4
.venv/Scripts/python -m eval.latency_cost eval/reports/stage8/*/*/      # B2
.venv/Scripts/python -m eval.verifier --k 3          # đo riêng verifier
.venv/Scripts/python -m eval.scope_eval              # hiệu chỉnh lớp chặn phạm vi
.venv/Scripts/python -m eval.compare eval/baselines/baseline.json eval/reports/<run_id>   # cổng hồi quy
```

Định danh lần chạy báo cáo: `dataset_version = 687e8ac98932`, `config_hash` baseline = `e4c0370bbb65`.

## 3. Bộ dữ liệu

| Bộ | Số case | Chia | Nguồn |
|---|---|---|---|
| golden (một lượt, có đáp án / không có đáp án) | 220 | dev 128 / test 92, chia **theo tài liệu** | `knowledge-base/_eval/cau-hoi-mau.csv` |
| golden LOOKUP | 32 (19 công cụ) | dev 22 / test 10, chia **theo công cụ** | `eval/build_lookup_cases.py` + 19 fixture mới |
| golden ngoài phạm vi + bẫy | 10 + 6 | theo `sha256(id)` | như trên |
| nhiều lượt | 8 (2 kịch bản E4 "hỏi lặp một chi tiết") | dev 4 / test 4 | như trên |
| an toàn | 26 (thêm `safety-support-status-01` ở M17) | luôn chạy đủ | `datasets/safety/safety.yaml` |
| verifier | 76 khẳng định (33 SUPPORTED, 33 UNSUPPORTED, 10 PARTIAL) | — | `datasets/verifier/claims.jsonl` |
| lớp chặn phạm vi | dev 134 + 20 / test 98 + 20 | theo tài liệu | `datasets/scope/scope_eval.yaml` |

Chia dev / test để chỉnh trên dev, báo cáo trên test (13 mục 13.9). Ngưỡng duy nhất chọn ở đợt này (`COVERAGE_MIN`
của Actor, δ của lớp chặn) đều chọn trên dev.

**Đã sửa bộ chấm:** câu có đáp án (POLICY, HOWTO, FAQ, ERROR) nay có kỳ vọng `grounded ∈ {FULL, PARTIAL}`. Trước đó
câu không có con số trong `must_include` vẫn "đạt" khi trợ lý từ chối — phát hiện khi quét ngưỡng ở mục 5.

## 4. Kết quả baseline (tập test, `gate` k = 3, đạt = đa số trong 3 lần)

| Nhóm | n | Đạt | CI 95% (bootstrap) | Recall@6 | MRR |
|---|---|---|---|---|---|
| ERROR | 10 | 90,0% | 70,0 – 100 | 100% | 0,950 |
| FAQ | 12 | 91,7% | 75,0 – 100 | 75,0% | 0,479 |
| HOWTO | 15 | 73,3% | 53,3 – 93,3 | 93,3% | 0,847 |
| POLICY | 26 | 61,5% | 42,3 – 80,8 | 96,2% | 0,782 |
| NO_ANSWER | 29 | 3,4% | 0 – 10,3 | 86,2% | 0,753 |
| LOOKUP | 10 | 80,0% | 50,0 – 100 | — | — |
| OUT_OF_SCOPE (golden + an toàn) | 9 | 88,9% | 66,7 – 100 | — | — |
| TRAP | 3 | 100% | — | — | — |
| nhiều lượt | 4 | 75,0% | 25,0 – 100 | — | — |
| **an toàn** | **26** | **100%** | — | — | — |
| tổng | 140 | 65,7% | 57,9 – 73,6 | 90,2% | 0,762 |

Dao động giữa 3 lần = 0 (Actor tất định) — k = 3 chỉ có ý nghĩa khi chạy model thật.

Grader (444 lượt): an toàn 100%; `numbers_match_fixture` 96/96; `tool_budget` 105/105; `tool_sequence` 72/81;
`intent` 219/246; `grounded` 189/276; `must_include` 9/36; `scope_not_blocked` 21/21; `memory_reference` 3/3.

**Đọc bảng:**
- `NO_ANSWER` 3,4%: Actor trích xuất luôn chép một câu "gần nhất" thay vì nói "chưa có thông tin". Quét ngưỡng phủ
  từ trên dev (mục 5.5) cho thấy **không có ngưỡng nào** tách được câu có / không có đáp án bằng trùng từ — việc này
  cần LLM (T3). Đây là chỉ số quan trọng nhất phải đo lại.
- `must_include` 25%: Actor chỉ chép một câu, nên con số ở câu khác của cùng đoạn bị thiếu.
- Hai lỗi LOOKUP trên test đều là Actor chọn nhầm công cụ bằng trùng từ: "Có ai khiếu nại tôi không?" →
  `list_my_complaints` thay vì `list_complaints_against_me`; "Gia sư đã nhận lời đề nghị học của tôi chưa?" →
  `list_my_studying_requests` thay vì `list_my_applications_to_tutors`. Công cụ đúng **luôn có** trong danh sách ≤ 6 công cụ được gửi.

## 5. Lỗi harness eval tìm ra (đã sửa)

| # | Lỗi | Phát hiện bởi | Sửa |
|---|---|---|---|
| 5.1 | Lớp chặn phạm vi chặn nhầm câu tra cứu ngắn có từ nghiệp vụ ("Tôi có thông báo nào mới?", "Yêu cầu hoàn tiền của tôi kết quả thế nào?", "Còn cái tiếng Anh thì kỳ học phí tới khi nào?") ở tầng câu mẫu | case LOOKUP / nhiều lượt mới | Câu nhắc `platform_terms` không bao giờ bị chặn ở tầng câu mẫu (`scope_guard.py`). Chặn nhầm trên test: **4,1% → 1,0%** (δ = 0,05) |
| 5.2 | Câu hỏi cá nhân không nhận ra → không gửi công cụ: "Có ai khiếu nại tôi không?", "rut tien thang nay xong chua", "…gần nhất cộng vào ví chưa?" | LOOKUP | Thêm dấu hiệu trong `tool_selection.py` |
| 5.3 | Sổ tay `focus` không biết người dùng đang nói đối tượng nào: sau "Hợp đồng **Toán** thế nào rồi?" (công cụ trả cả hai hợp đồng), "cái đó" ở 4 lượt sau trỏ nhầm sang Tiếng Anh | E4 `mt-memory-focus-under-spam-02` | `FocusItem.mentioned_turn`: đối tượng được gọi tên trong câu hỏi thành "đang bàn nhất" (chỉ xếp thứ tự, lời người dùng không thêm được ID nào). Lời nhắc viết lại câu hỏi nói rõ mục cuối là gần nhất |
| 5.4 | Bộ chấm cho "đạt" khi trợ lý từ chối câu có đáp án | quét ngưỡng ở 5.5 | `build_datasets.py` thêm `grounded_in: [FULL, PARTIAL]` |
| 5.5 | (Actor giả lập) gọi tool cho mọi câu khi có tool; không có intent HOWTO | case nhiều lượt | Actor v2: chỉ gọi tool khi câu là câu cá nhân / dùng đại từ / gọi tên đối tượng đang bàn; HOWTO theo từ "làm sao, ở đâu, cách…". `COVERAGE_MIN` quét trên dev: |

Quét `COVERAGE_MIN` trên dev (sau sửa 5.4):

| COVERAGE_MIN | tổng | ERROR | FAQ | HOWTO | NO_ANSWER | POLICY |
|---|---|---|---|---|---|---|
| **0,0 (chọn)** | **57,0%** | 92,9% | 63,0% | 76,2% | 2,7% | 58,6% |
| 0,3 | 52,5% | 35,7% | 63,0% | 76,2% | 8,1% | 55,2% |
| 0,5 | 47,5% | 14,3% | 37,0% | 61,9% | 32,4% | 41,4% |
| 0,7 | 50,0% | 7,1% | 29,6% | 38,1% | 81,1% | 20,7% |

## 6. Thí nghiệm

### 6.1. E1 — Truy xuất

| Cấu hình | Embedding | Tập | Recall@6 | MRR | Đạt |
|---|---|---|---|---|---|
| Chỉ vector | giả lập | test 110 câu | 92,4% | 0,747 | 57,3% |
| Lai (vector + full-text, RRF) — baseline | giả lập | test 110 câu | 90,2% | 0,762 | 57,3% |
| Chỉ vector | **bge-m3** (Postgres) | 220 câu (chưa chia) | 76,8% | — | — |
| Lai | **bge-m3** (Postgres) | 220 câu (chưa chia) | **89,5%** | 0,739 | — |
| Lai | giả lập (Postgres) | 220 câu (chưa chia) | 89,1% | 0,710 | — |
| Lai + rerank | bge-m3 | — | **chưa đo** | — | — |

- Với embedding giả lập (bản chất là so từ), thêm full-text không giúp Recall (−2,2 điểm, trong nhiễu với n = 110),
  MRR tăng nhẹ. Với bge-m3 thật, lai hơn chỉ vector **12,7 điểm** — đúng giả thuyết của thiết kế: vector ngữ nghĩa bỏ
  sót từ khoá / mã lỗi / con số mà full-text bắt được.
- Rerank chưa đo: job đo bị hệ thống dừng vì máy thiếu RAM (ai-service + Docker). Chạy lại:
  `EVAL_WITH_RERANK=1 ASSISTANT_EMBEDDING_PROVIDER=ai-service bash eval/run_experiments.sh e1-rerank`.

### 6.2. E2 — Kiểm chứng trích dẫn

| Cấu hình (test) | Đạt | Lời gọi model | Token (ước lượng) |
|---|---|---|---|
| Không verifier | 65,7% | 537 | 739.119 |
| Verifier, không thử lại | 65,7% | 852 | 849.759 |
| Verifier + 1 lần thử lại (baseline) | 65,7% | 852 | 849.759 |

- **Không kết luận được về chất lượng**: Actor giả lập chép nguyên văn nên mọi khẳng định đều là chuỗi con của nguồn →
  verifier không bác lần nào, thử lại không bao giờ xảy ra. Ba cấu hình cho cùng kết quả.
- **Chi phí thì đo được**: verifier thêm 315 lời gọi / 444 lượt (+0,71 lời gọi / lượt) và **+13% token**
  (~+250 token / lượt). Đây là cái giá phải đặt cạnh "khẳng định không căn cứ lọt qua" khi chạy model thật.
- Đo riêng verifier (`eval.verifier`, k = 3, verifier giả lập so chuỗi con): recall bắt lỗi **100%**, bác nhầm
  **3,0%** (1/33: câu đổi "đ" → "đồng"), khớp nhãn 3 lớp 85,5% (PARTIAL luôn thành UNSUPPORTED). Bộ khẳng định **chưa
  có câu diễn đạt lại** (cần người viết) và **nhãn gán theo cách tạo, chưa qua hai người gán** — hai việc cho nhóm trước
  khi dùng số này trong luận văn.

### 6.3. E4 — Viết lại câu hỏi (bộ nhiều lượt, 8 hội thoại, dev + test)

| Cấu hình | Đạt | Tham chiếu đúng đối tượng (`memory_reference`) | Lời gọi | Token |
|---|---|---|---|---|
| Không viết lại | 100% (8/8) | 6/6 | 120 | 159.393 |
| Viết lại (baseline) | 75% (6/8) | 6/6 | 162 | 172.659 |

- Cả hai cấu hình đều **trỏ đúng đối tượng** ở các lượt dùng đại từ — kể cả khi lượt gọi tên đã trôi khỏi cửa sổ lịch
  sử (`history_turns = 2`). Phần việc này do **sổ tay `focus`** (sau sửa 5.3) làm, không phải do viết lại câu hỏi.
- Hai case trượt khi có viết lại là do Actor giả lập chọn công cụ bằng trùng từ: câu đã viết lại có thêm
  "(contract Tiếng Anh · Lớp 7)" nên nghiêng sang `get_my_contract` thay vì `list_my_payment_installments`. Một LLM
  không chọn công cụ theo trùng từ → **phải đo lại ở T3** trước khi kết luận viết lại câu hỏi có ích hay không.
- Chi phí: viết lại thêm 42 lời gọi / 66 lượt và +8% token trên bộ nhiều lượt; chỉ chạy khi có lịch sử.
- n = 8 quá nhỏ (CI rất rộng); bộ nhiều lượt cần mở rộng khi có model thật.

### 6.4. B2 — Độ trễ, token, chi phí

Trên máy dev, tuần tự, Actor giả lập (thời gian model ≈ 0):

| Bước | p50 (ms) | p95 (ms) | % thời gian |
|---|---|---|---|
| Truy xuất (kho trong bộ nhớ) | 55 | 61 | 87% |
| Lớp chặn phạm vi (so câu mẫu) | 0 | 32 | 13% |
| Các bước còn lại (kiểm đầu vào, lưu, tool mock, verifier giả lập) | 0 | ≤ 1 | < 1% |

| Cấu hình | p50 lượt | p95 lượt | Token / lượt (ước lượng) |
|---|---|---|---|
| baseline | 77 ms | 110 ms | 1.914 |
| E1 chỉ vector | 77 | 109 | — |
| E2 không verifier | 77 | 110 | ~1.665 |

Token trung bình mỗi lời gọi: Actor 1.427, verifier 351, viết lại 290. **Chi phí tiền: chưa tính được** — chưa có
model nên `eval/configs/pricing.yaml` để trống (không tự đoán đơn giá). Độ trễ thật sẽ do model quyết định.

## 7. Lớp chặn phạm vi và cổng hồi quy

### 7.1. Hiệu chỉnh (embedding giả lập)

| Ngưỡng | Tập | Chặn sớm câu ngoài phạm vi | Chặn nhầm câu trong phạm vi |
|---|---|---|---|
| τ = 0,55, δ = 0,05 (mặc định, giữ) | test | 50% | **1,0%** ("Waiting for review là gì?") |
| τ = 0,55, δ = 0,02 (chọn trên dev) | test | 65% | 2,0% |

Mục tiêu chặn nhầm ≤ 3% đạt ở cả hai. Câu ngoài phạm vi không bị chặn sớm vẫn đi tiếp và được Actor xử lý (tầng 3).

### 7.2. Cổng hồi quy (`eval.compare`)

Thử đổi δ 0,05 → 0,02 rồi so với baseline: tỉ lệ đạt giữ nguyên 65,7% nhưng **cổng trượt** vì case
`tai-khoan-dang-ky-04` ("Sửa số điện thoại ở đâu?") đạt ổn định ở baseline nay bị chặn nhầm (McNemar p = 1,0; đổi
một case đúng lấy một case `oos-homework-01`). Theo thứ tự ưu tiên của thiết kế (chặn nhầm tệ hơn bỏ lọt) giữ
δ = 0,05. Ví dụ này cho thấy vì sao cổng xét **từng case ổn định** chứ không chỉ so hai con số trung bình.

Baseline lưu ở `assistant-service/eval/baselines/baseline.json`.

## 8. Chấm tay (chưa làm)

Công cụ đã có: `python -m eval.grade_manual export <run> [<run> …] --out <thư mục>` sinh phiếu chấm mù (trộn thứ tự,
ẩn cấu hình), `… score` tính tỉ lệ đạt theo rubric 13.5 và tổng số "khẳng định không căn cứ lọt qua". **Chưa chấm**:
chấm tay câu trả lời của Actor giả lập không có ý nghĩa — làm sau khi chạy T3, khoảng 41 câu POLICY / HOWTO của tập
test cho mỗi cấu hình cần so.

## 9. Việc còn lại để có số cho luận văn

1. **Chọn model** (người dùng). Ước tính một lần chạy tập test chế độ `gate`: ~850 lời gọi, ~0,85 triệu token (đa số
   token vào). Chi phí ≈ 0,85 × đơn giá / 1 triệu token vào (+ phần token ra nhỏ). Toàn bộ E1 + E2 + E4 ≈ 5 lần như
   vậy. Trần chi phí trong code: `--max-llm-calls`, `--max-tokens` của `eval.run`; `ASSISTANT_TURN_TOKEN_BUDGET` cho mỗi
   lượt. Lệnh: `eval.run --config eval/configs/real_llm.yaml --mode gate --split test --yes`.
2. Chạy lại E2, E4, `eval.verifier` với model thật; chấm tay (mục 8).
3. Đo rerank (E1) khi máy đủ RAM; chạy lại E1 với bge-m3 trên tập test.
4. Bổ sung câu diễn đạt lại và gán nhãn hai người cho `claims.jsonl`.
5. Trả lời 95 câu hỏi todo của kho tri thức → nhóm `NO_ANSWER` sẽ thay đổi.

## 10. Việc riêng cho nhóm: thống nhất múi giờ toàn hệ thống

Backend ghi thời điểm theo **hai hệ**: Hibernate (`createdAt`, `updatedAt`, …) theo múi giờ JVM (`Asia/Ho_Chi_Minh` trên
máy dev và trong Docker), còn service ghi `LocalDateTime.now(clock)` với `Clock.systemUTC()` → **UTC**, cả hai đều là
`LocalDateTime` không kèm múi giờ. Frontend hiện coi mọi chuỗi là giờ địa phương → các trường UTC hiển thị **sớm 7 giờ**.
Trợ lý đã tự đổi đúng theo nhãn `time_zone` trong `tool-catalog/tools.yaml` (quyết định 2026-11-03: không đổi
`ContractTimeProvider`, không đặt `TZ=UTC`); phần còn lại của hệ thống cần nhóm sửa.

Các trang hiển thị trường ghi theo UTC (tìm theo tên trường; nên kiểm bằng mắt từng trang):

| Trường (UTC) | Trang / thành phần |
|---|---|
| `markedTaughtAt` | `StudentContractLessonsPage`, `StudentRefundRequestCreatePage`, `TutorRefundRequestDetailPage`, `AdminRefundRequestDetailPage`, `components/refund/RefundRequestOverview` |
| `tutorRespondedAt`, `tutorResponseDeadline` | `TutorRefundRequestsPage`, `TutorRefundRequestDetailPage`, `AdminRefundRequestDetailPage`, `RefundRequestOverview` |
| `reviewedAt` | `AdminRequestReviewPage`, `RequestReviewModal`, `CertificateReviewModal`, `requestReviewFormat.ts`, `RefundRequestOverview` |
| `resolvedAt`, `paidAt` | `AdminComplaintDetailPage`, `AdminRefundRequestDetailPage` |
| `profileReviewedAt` | `AdminTutorReviewPage` |
| `answeredAt`, `closedAt` (M17) | đã xử lý riêng trong `components/support/supportFormat.ts` |

Đề xuất: thống nhất một hệ (khuyến nghị lưu UTC bằng `Instant` / `OffsetDateTime`, trả ISO-8601 có `Z`), rồi bỏ nhãn
`time_zone` riêng từng trường trong `tools.yaml`.
