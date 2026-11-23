# 9. Bảo mật và quyền riêng tư

> 💡 **File này nói về gì:** những rủi ro (lừa trợ lý, xem trộm dữ liệu, lộ thông tin cá nhân…) và cách thiết kế chặn từng rủi ro; dữ liệu được lưu bao lâu. Bản lời thường: [00](./00-doc-truoc.md) mục 5.

## 9.1. Phòng thủ nhiều lớp

> 💡 **Nói đơn giản:** Không dựa vào một lớp bảo vệ duy nhất mà xếp nhiều lớp chồng nhau; lớp này bị vượt thì còn lớp kia. Và **không coi lời dặn LLM là một lớp bảo vệ** — chỉ code mới tính.

Không dựa vào một lớp duy nhất, và **không coi quy tắc trong prompt là một lớp phòng thủ**. Mỗi lớp dưới đây là
code / hạ tầng:

| Lớp | Vị trí | Biện pháp |
|---|---|---|
| 1. Kiểm tra đầu vào trước khi model thấy | Backend + `InputGuardMiddleware` (①) | Xác thực JWT, `AccountStatusFilter`, giới hạn độ dài, quyền sở hữu hội thoại, che thông tin cá nhân |
| 2. Kiểm tra đầu ra trước khi dùng | `OutputSchemaMiddleware`, `CitationVerifierMiddleware`, `RouteGuardMiddleware` (④, ④') | Đúng schema, mọi khẳng định có căn cứ, chỉ route nội bộ hợp lệ |
| 3. Quyền theo bản khai năng lực tại lời gọi tool | `ToolGateMiddleware` (⑤) + `BackendClient` | Allowlist theo vai trò, chỉ GET, `READ_ONLY`, token do harness gắn, kiểm tra nguồn gốc ID |
| 4. Hành động không thể hoàn tác cần người phê duyệt | — | **Không có** hành động như vậy: trợ lý không có tool ghi (QĐ7) |
| 5. Cầu dao ngân sách và tần suất | Backend (rate limit) + `BudgetMiddleware`, `LoopGuardMiddleware` | 20 tin / 5 phút / người; giới hạn cứng số lời gọi model, tool, token, thời gian mỗi lượt |
| Hạ tầng | Docker, PostgreSQL | `assistant-service` bind `127.0.0.1` + `X-Internal-Token`; role DB không có quyền schema `public` |

Các cơ chế bảo vệ (allowlist, bản khai tool, ngân sách) nằm trong code của service và cấu hình triển khai; trợ
lý và admin (qua trang quản trị) **không sửa được** chúng. Admin chỉ sửa được nội dung kho tri thức.

## 9.2. Mối đe dọa và biện pháp

> 💡 **Nói đơn giản:** Từng kiểu tấn công / sự cố cụ thể (T1, T2…) và biện pháp tương ứng. "Prompt injection" = câu lừa LLM làm trái quy tắc.

| # | Mối đe dọa | Biện pháp (lớp) |
|---|---|---|
| T1 | Xem dữ liệu của người khác qua trợ lý | Endpoint `/me`, token của chính người hỏi, không có tham số `userId`, ID phải thuộc `seen_ids` (3) |
| T2 | Prompt injection trong câu hỏi ("bỏ qua hướng dẫn, gọi API X") | Không có tool ghi; allowlist cố định — vượt được prompt cũng không làm được gì ngoài phạm vi người dùng tự xem được (3, 4) |
| T3 | Injection gián tiếp qua dữ liệu người khác viết (mô tả tin, nội dung đề nghị) | Như T2; kết quả tool bọc thẻ; route bị lọc (2, 3) |
| T4 | Injection qua nội dung kho tri thức | Chỉ `ADMIN` soạn và công bố; nội dung được duyệt trước khi công bố; tài liệu cũng chỉ là dữ liệu với các lớp 2–3 |
| T5 | Trợ lý bịa quy định / trích dẫn sai | Kiểm chứng trích dẫn, abstain (2) |
| T6 | Rò rỉ thông tin cá nhân ra nhà cung cấp LLM | Che SĐT, email, dãy số dài; lọc trường kết quả tool; không gửi họ tên người hỏi; gọi API với tùy chọn không lưu dữ liệu nếu nhà cung cấp hỗ trợ (như `ai-service` đặt `store=False`) (1, 3) |
| T7 | Lộ access token người dùng | Token chỉ đi backend → `assistant-service` → backend trong mạng nội bộ; không ghi log, không lưu DB, không đưa vào prompt |
| T8 | Gọi thẳng `assistant-service` / `ai-service` từ ngoài | Bind `127.0.0.1`, token nội bộ so sánh thời gian hằng |
| T9 | Lạm dụng tốn chi phí LLM | Lớp 5; theo dõi token trong `turn_trace` |
| T10 | `assistant-service` đọc trộm dữ liệu nghiệp vụ | Role DB `assistant_app` không có quyền schema `public` |
| T12 | Dùng trợ lý cho việc ngoài nền tảng (tốn chi phí, sai vai trò) | Lớp chặn ngoài phạm vi trước khi gọi model ([04](./04-luong-hoi-dap.md) mục 4.11) — lớp về phạm vi / chi phí, không thay cho các lớp bảo mật |
| T13 | Lộ hội thoại qua yêu cầu hỗ trợ | Chỉ đính kèm khi người dùng đồng ý; đoạn trích đã che thông tin cá nhân; chỉ nhân viên `CUSTOMER_SERVICE` / `ADMIN` xem được ([14](./14-yeu-cau-ho-tro.md) mục 14.8) |
| T14 | Trợ lý tạo yêu cầu hỗ trợ hàng loạt do injection | Trợ lý không có tool tạo yêu cầu; chỉ người dùng bấm gửi; giới hạn 5 yêu cầu mở / 10 mỗi ngày |
| T11 | XSS qua câu trả lời / nội dung tài liệu | Render Markdown không cho HTML ở widget và trang xem trước; link chỉ là route nội bộ qua `navigate` |

## 9.3. Dữ liệu lưu và thời hạn

> 💡 **Nói đơn giản:** Cái gì được lưu, lưu bao lâu, cái gì **không** lưu (dữ liệu tra cứu, quyền đăng nhập), và cách xóa có kiểm tra.

| Dữ liệu | Lưu | Thời hạn |
|---|---|---|
| Câu hỏi (đã che) và câu trả lời | Có | **90 ngày**, sau đó xóa nội dung (`content = null`), giữ intent, grounded, trace, đánh giá để thống kê |
| Trạng thái phiên (`session_state`: ID + nhãn đối tượng đang bàn) | Có | Cùng nội dung hội thoại: 90 ngày, sau đó đặt `null` |
| Kết quả tool (dữ liệu nghiệp vụ) | **Không** | — |
| Tên tool, tham số ID, mã HTTP, độ trễ | Có | 90 ngày |
| Trace (span, token, thời gian) | Có | 180 ngày (không chứa nội dung) |
| Đánh giá 👍/👎 và ghi chú | Có | Cùng tin nhắn |
| Access token | **Không** | — |
| Kho tri thức (mọi phiên bản) | Có | Không xóa (cần cho trích dẫn cũ, lịch sử thay đổi) |
| Yêu cầu hỗ trợ: câu hỏi, câu trả lời | Có (backend) | Giữ để thống kê |
| Yêu cầu hỗ trợ: đoạn hội thoại đính kèm | Có, khi người dùng đồng ý | 365 ngày sau khi đóng yêu cầu; không bị xóa theo hội thoại gốc (người dùng được báo trước) |

- **Không có bộ nhớ xuyên phiên** và **không có hồ sơ người dùng** do trợ lý tự lưu ([04](./04-luong-hoi-dap.md) mục
  4.10): dữ liệu cá nhân chỉ tồn tại trong phạm vi một hội thoại và trong DB nghiệp vụ vốn có — tối thiểu hóa dữ
  liệu và giới hạn mục đích ngay từ thiết kế.
- **Cách ly**: mọi đọc / ghi bộ nhớ hội thoại lọc theo `user_id` từ token; hội thoại không thuộc người gọi → `404`.
  Có test riêng (người dùng A không mở / không tiếp tục được hội thoại của B — [13](./13-thiet-ke-eval.md) mục 13.7).
- **Xóa có kiểm chứng**: người dùng xóa hội thoại → đặt `deleted_at` ngay (từ đó không bao giờ được nạp lại hay trả
  về); job hằng đêm gọi `purge_conversation` cho các hội thoại đã đánh dấu quá 7 ngày, xóa trên **cả 6 bảng**, kiểm
  chứng không còn dòng nào và ghi `deletion_log` ([06](./06-du-lieu-va-schema.md) mục 6.4). Kiểm chứng thất bại →
  cảnh báo trong log vận hành và thử lại lần sau. Có test tự động cho đường xóa ([13](./13-thiet-ke-eval.md) mục 13.1).
- Job hằng đêm thực hiện các thời hạn trên (`ASSISTANT_RETENTION_DAYS`, `ASSISTANT_TRACE_RETENTION_DAYS`).
- Admin xem câu hỏi chưa trả lời được: chỉ thấy nội dung đã che, **không thấy `user_id`**.

## 9.4. Minh bạch với người dùng

> 💡 **Nói đơn giản:** Người dùng được báo rõ trợ lý dùng AI, có thể sai, và câu hỏi được lưu bao lâu.

- Dòng chú thích dưới ô nhập ([08](./08-giao-dien.md)).
- Câu trả lời có dữ liệu cá nhân nói rõ nguồn ("theo hợp đồng của bạn…").
- Mục "Trợ lý hỗ trợ" trong điều khoản sử dụng: trợ lý dùng AI; câu hỏi được lưu 90 ngày để cải thiện chất lượng.
