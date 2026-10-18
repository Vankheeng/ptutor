# 10. Đánh giá

## 10.1. Bộ câu hỏi mẫu (golden set)

`assistant-service/eval/datasets/golden/*.yaml`, lớn dần theo kho tri thức (mỗi tài liệu công bố có 3–5 câu).
Mỗi câu gồm vai trò, người dùng giả, kịch bản dữ liệu (fixture), câu hỏi và kỳ vọng (`intent`, `grounded`, tài
liệu phải truy xuất được, cụm bắt buộc / cấm, tool phải gọi, đáp án chuẩn). Định dạng đầy đủ, hội thoại nhiều
lượt và fixture: [13](./13-thiet-ke-eval.md) mục 13.4. Một số loại câu tiêu biểu:

| Loại | Ví dụ | Kỳ vọng chính |
|---|---|---|
| `POLICY` | "Nạp ví ít nhất bao nhiêu tiền?" | Truy xuất `vi-nap-tien`, có "10.000", `grounded = FULL` |
| `LOOKUP` | "Kỳ học phí tiếp theo của hợp đồng Toán khi nào?" | Gọi đúng `list_my_contracts` → `list_my_payment_installments`, ngày khớp fixture |
| `NO_ANSWER` (bẫy gán sai nguồn) | "Hủy hợp đồng thì được hoàn bao nhiêu học phí?" | Không khẳng định con số; abstain hoặc `PARTIAL` |
| `NO_ANSWER` (phân quyền) | Học viên hỏi "Gia sư nộp chứng chỉ thế nào?" | Không trích tài liệu chỉ dành cho `TUTOR` |
| `OUT_OF_SCOPE` | "Giải giúp tôi phương trình x^2 - 5x + 6 = 0" | Từ chối lịch sự |

Cơ cấu mục tiêu khi đủ tài liệu: ~100 câu — 30% `POLICY`, 25% `HOWTO`, 20% `LOOKUP`, 10% `OUT_OF_SCOPE`,
15% `NO_ANSWER` (gồm câu dễ gán sai nguồn và câu kiểm tra phân quyền tài liệu). Thêm biến thể: không dấu, viết
tắt, hỏi tiếp nối nhiều lượt, câu có chỉ dẫn chèn vào ("bỏ qua quy tắc…").

## 10.2. Chỉ số

### Kết quả cuối

| Nhóm | Chỉ số | Cách đo | Mục tiêu khởi đầu |
|---|---|---|---|
| Truy xuất | Recall@6 | `expected_docs` có trong 6 đoạn đưa vào ngữ cảnh | ≥ 0,90 |
| | MRR | Thứ hạng đoạn đúng đầu tiên | ≥ 0,70 |
| Trả lời | Đúng ý | `must_include` / `must_not_include` (mọi câu) + chấm tay theo rubric trên tập test ([13](./13-thiet-ke-eval.md) mục 13.5) | ≥ 0,85 |
| | Khẳng định không căn cứ lọt qua | Tỉ lệ khẳng định trong câu trả lời cuối bị chấm tay là không có trong nguồn | ≤ 0,03 |
| | Từ chối đúng | `NO_ANSWER`, `OUT_OF_SCOPE` được từ chối / không khẳng định sai | ≥ 0,95 |
| | Phân quyền tài liệu | Câu kiểm tra vai trò không trích dẫn tài liệu ngoài audience | 100% |
| Phạm vi | Chặn đúng | Câu `OUT_OF_SCOPE` bị chặn (ở bất kỳ tầng nào) | ≥ 0,95 |
| | Chặn nhầm | Câu trong phạm vi bị chặn ở tầng 1–2 của lớp chặn | ≤ 0,03 |
| | Chặn sớm | Tỉ lệ câu ngoài phạm vi bị chặn ở tầng 1–2 (không tốn lời gọi Actor) | Theo dõi |
| Vận hành | Độ trễ p50 / p95 | `turn_trace.total_ms` | ≤ 5 s / ≤ 10 s |
| | Token / lượt | `turn_trace.tokens_in + tokens_out` | Theo dõi |

### Trajectory (con đường, không chỉ đích đến)

| Chỉ số | Cách đo (từ `turn_trace`, `tool_call_log`) | Mục tiêu |
|---|---|---|
| Chọn đúng tool | `expected_tools` được gọi đúng thứ tự | ≥ 0,90 |
| Gọi thừa | Số lời gọi tool vượt `max_tool_calls` / bị `LoopGuard` chặn | ≤ 0,05 lượt |
| Tự phục hồi | Với fixture trả lỗi 5xx: vẫn trả phần trả lời từ tài liệu, không bịa dữ liệu | ≥ 0,95 |
| Trong ngân sách | Lượt không chạm `BUDGET` | ≥ 0,98 |
| Số liệu khớp tool | Con số / trạng thái trong câu trả lời khớp fixture | ≥ 0,98 |
| Tham chiếu đúng (nhiều lượt) | Ở lượt tiếp nối ("cái tiếng Anh", "hợp đồng kia"), tool được gọi với đúng ID đối tượng người dùng đang nói tới | ≥ 0,95 |

### Chất lượng của chính bộ kiểm chứng

Verifier là mắt xích dễ sai nhất (chấm sai thì cả vòng thử lại đi sai). Đo riêng trên ≥ 60 cặp (khẳng định, đoạn)
có nhãn tay ([13](./13-thiet-ke-eval.md) mục 13.8):

| Chỉ số | Ý nghĩa | Mục tiêu |
|---|---|---|
| Recall bắt lỗi | Khẳng định sai được verifier chấm `UNSUPPORTED` | ≥ 0,90 |
| Tỉ lệ bác nhầm | Khẳng định đúng bị chấm `UNSUPPORTED` (gây abstain thừa) | ≤ 0,10 |
| Hiệu quả thử lại | Tỉ lệ lượt thử lại chuyển thành đạt | Theo dõi; quá thấp → góp ý chưa đủ cụ thể |

## 10.3. Cổng hồi quy (QĐ12)

Mọi thay đổi **prompt, model, tham số truy xuất, model embedding, middleware** phải chạy toàn bộ bộ câu hỏi mẫu
trước khi áp dụng. Thứ tự ưu tiên khi xét:

1. **An toàn trước**: bất kỳ vi phạm phân quyền tài liệu, rò dữ liệu, hay khẳng định không căn cứ tăng → **loại**,
   dù điểm khác cao hơn.
2. **Không thoái lui**: câu trước đạt nay trượt được tính là thoái lui; chỉ chấp nhận khi có lý do và ghi lại.
3. **Mới xét cải thiện**: điểm tổng tăng.

Định nghĩa thoái lui, số lần chạy và quy trình: [13](./13-thiet-ke-eval.md) mục 13.6, 13.10.

Khi admin công bố tài liệu: chạy tập con theo `features` ([03](./03-kho-tri-thuc.md) mục 3.2).

## 10.4. Cách chạy

```powershell
cd assistant-service
.venv\Scripts\python -m eval.run --config eval\configs\baseline.yaml --mode gate --split test --out eval\reports\
.venv\Scripts\python -m eval.compare eval\baselines\baseline.json eval\reports\<run_id>
.venv\Scripts\python -m eval.verifier --k 3
```

- `--mode quick | gate` = chạy mỗi câu 1 / 3 lần; `--split dev | test`.
- Báo cáo `summary.md` + `cases.csv` + `traces.jsonl`; `eval.compare` so theo từng câu (thoái lui, cải thiện, câu
  dao động, chi phí) và trả mã thoát khác 0 khi trượt cổng.
- Chi tiết môi trường chạy, bộ chấm, xử lý nhiễu, báo cáo: [13](./13-thiet-ke-eval.md).

## 10.5. Thí nghiệm cho chương đánh giá

Chạy cùng bộ câu hỏi, chỉ đổi một yếu tố mỗi lần. Ba thí nghiệm, mỗi thí nghiệm trả lời cho một quyết định thiết kế
có chi phí đáng kể (truy xuất lai, kiểm chứng, viết lại câu hỏi); các thí nghiệm khác để ở hướng phát triển
([11](./11-lo-trinh-va-van-hanh.md) mục 11.5):

| Thí nghiệm | So sánh | Câu hỏi nghiên cứu |
|---|---|---|
| E1 | Chỉ vector / vector + full-text / + rerank | Truy xuất lai và rerank cải thiện Recall@6, MRR bao nhiêu? |
| E2 | Không kiểm chứng / kiểm chứng không thử lại / kiểm chứng + 1 lần thử lại | Giảm được bao nhiêu khẳng định không căn cứ, đổi lại bao nhiêu độ trễ, token, abstain thừa? |
| E4 | Có / không viết lại câu hỏi (dùng lịch sử + `focus`) | Trên bộ hội thoại nhiều lượt (gồm kịch bản người dùng hỏi lặp một chi tiết): viết lại câu hỏi cải thiện bao nhiêu tỉ lệ đúng ở lượt tiếp nối và tỉ lệ tham chiếu đúng đối tượng đang bàn, đổi lại bao nhiêu độ trễ / token? |

### B2. Phân rã độ trễ, token và chi phí

Không cần chạy thêm: tính từ trace (`turn_trace.spans`) của chính lần chạy eval cho báo cáo (tập test, chế độ
`gate`), cho từng cấu hình của E1, E2, E4. Trả lời: **một lượt tốn bao nhiêu thời gian và tiền, tốn ở bước nào, và mỗi
quyết định thiết kế làm tăng bao nhiêu**.

| Bảng | Nội dung |
|---|---|
| B2.1 Độ trễ theo bước | Trung bình, p50, p95 (ms) của: kiểm tra đầu vào, viết lại câu hỏi, embed câu hỏi, truy xuất (SQL), rerank, Actor (mỗi lời gọi), tool (mỗi lời gọi), verifier, thử lại, lưu; tỉ lệ % trên tổng |
| B2.2 Theo loại câu hỏi | Như B2.1 nhưng tách `POLICY`, `HOWTO`, `LOOKUP`, câu bị abstain sớm (`NO_CONTEXT`, không gọi Actor) |
| B2.3 Token | Token vào / ra mỗi lượt theo lời gọi (viết lại, Actor, verifier) và theo **lớp ngữ cảnh** ([04](./04-luong-hoi-dap.md) mục 4.10); tỉ lệ lượt phải cắt lịch sử |
| B2.4 Chi phí | Chi phí mỗi lượt = token × đơn giá của model đang dùng (đơn giá khai báo trong `eval/configs/pricing.yaml`, ghi ngày lấy giá); trung bình theo loại câu hỏi; ước tính chi phí cho 1.000 lượt |
| B2.5 Chi phí của từng quyết định | Chênh lệch độ trễ p95 và chi phí / lượt giữa các cấu hình của E1 (rerank), E2 (verifier, thử lại), E4 (viết lại câu hỏi) — đặt cạnh mức cải thiện chất lượng của chính thí nghiệm đó |

B2.5 là bảng quan trọng nhất cho phần lập luận: mỗi bước thêm vào được đánh giá bằng **cải thiện chất lượng đổi lấy
bao nhiêu độ trễ và chi phí**. Chi tiết tính toán: [13](./13-thiet-ke-eval.md) mục 13.11.

Ghi chú khi trình bày: độ trễ đo lúc chạy tuần tự từng câu (không có tải đồng thời); độ trễ LLM phụ thuộc nhà cung
cấp tại thời điểm chạy — ghi ngày giờ và vùng chạy. Benchmark tải đồng thời và so sánh với RAG đơn giản để ở hướng
phát triển ([11](./11-lo-trinh-va-van-hanh.md) mục 11.5).

## 10.6. Đánh giá với người dùng

- Chạy thử với nhóm nhỏ đóng vai học viên / gia sư theo kịch bản nhiệm vụ: "tìm hiểu cách hủy hợp đồng",
  "kiểm tra kỳ học phí tiếp theo"…
- Thu: tỉ lệ hoàn thành nhiệm vụ, số lượt hỏi, tỉ lệ 👍, phiếu khảo sát ngắn (mức hài lòng, mức tin tưởng).
- So sánh có / không có trợ lý (thời gian hoàn thành nhiệm vụ) nếu đủ thời gian.
