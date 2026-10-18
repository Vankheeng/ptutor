# 5. Công cụ tra cứu dữ liệu người dùng

## 5.1. Nguyên tắc

Model chỉ **đề nghị** gọi tool; **harness quyết định** có cho chạy hay không (`ToolGateMiddleware` ở hook
`wrap_tool_call`, [02](./02-kien-truc.md) mục 2.3). Mọi nguyên tắc dưới đây được thực thi bằng code.

1. **Chỉ GET.** `BackendClient` từ chối mọi phương thức khác; tool nào cũng có `reversibility = READ_ONLY`.
2. **Chỉ endpoint "của tôi".** Mọi tool trỏ tới `/users/me/**`, `/students/me/**`, `/tutors/me/**`. Không có
   tham số `userId` nào do model điền.
3. **Token do harness gắn.** Header `Authorization` (backend chuyển tiếp) được `ToolGateMiddleware` gắn vào lời
   gọi; model không thấy và không sửa được. Token không ghi log, không lưu DB.
4. **Lọc trường.** Mỗi tool khai báo danh sách trường được đưa cho model; các trường khác (email, họ, số tài khoản
   ngân hàng…) bị bỏ.
5. **Kiểm tra nguồn gốc ID.** ID do model điền phải là UUID và đã xuất hiện trong kết quả tool trước đó của hội
   thoại (`seen_ids` = các ID trong `session_state.focus`, tối đa 20 gần nhất —
   [04](./04-luong-hoi-dap.md) mục 4.10) — chống đoán ID. ID đã bị đẩy ra khỏi `focus` thì model phải tra lại bằng tool liệt kê.
6. **Chặn lặp.** Cùng tool + cùng tham số lần thứ hai trong một lượt → không gọi lại, trả "đã có kết quả ở trên".
7. **Kết quả là dữ liệu.** Văn bản tự do trong kết quả (mô tả tin đăng, nội dung khiếu nại) được bọc
   `<tool_result>`; vì không có tool ghi, chỉ dẫn chèn vào cũng không gây ra được hành động nào.

## 5.2. Định nghĩa một tool

Mỗi tool khai báo đủ **phạm vi** (vai trò, đường dẫn), **mục allowlist** và **mức khả năng hoàn tác**:

```python
Tool(
    name="list_my_contracts",
    description="Liệt kê hợp đồng của người dùng hiện tại, kèm trạng thái.",
    roles={"STUDENT", "TUTOR"},
    features={"contract", "payment"},          # dùng cho chọn tool động
    reversibility="READ_ONLY",
    method="GET",
    path="/api/v1/users/me/contracts",
    params_schema={"status": {"enum": ["PENDING", "ACTIVE", "COMPLETED", "CANCELLED"], "optional": True}},
    id_params=[],                              # tham số phải thuộc seen_ids
    fields=["id", "subjectName", "gradeName", "teachingMode", "price", "paymentPeriod",
            "totalLessons", "startDate", "endDate", "status", "tutorFirstName", "studentFirstName"],
    max_items=10,
)
```

Allowlist là chính danh sách khai báo này: `BackendClient` chỉ nhận cặp (phương thức, mẫu đường dẫn) có trong
registry. Thêm tool = thêm một khai báo, không sửa vòng lặp agent.

**Chọn tool động** (`ToolSelectionMiddleware`): mỗi lời gọi model chỉ thấy tối đa 6 tool — tool của vai trò, ưu
tiên tool có `features` trùng với các đoạn tài liệu truy xuất được và trang đang mở; câu hỏi quy định thuần
(không có dấu hiệu "của tôi", "hiện tại"…) có thể không gửi tool nào.

## 5.3. Danh sách tool

Đường dẫn lấy theo controller trên `dev`. Tham số query cụ thể (phân trang, lọc) đối chiếu lại khi triển khai.

### Dùng chung `STUDENT`, `TUTOR`

| Tool | Endpoint | Trường giữ lại (chính) | Dùng khi |
|---|---|---|---|
| `list_my_contracts` | `GET /api/v1/users/me/contracts` | id, môn, khối, hình thức, giá, kỳ thanh toán, số buổi, ngày bắt đầu/kết thúc, `status`, tên (first name) bên kia | "Hợp đồng của tôi…" |
| `get_my_contract` | `GET /api/v1/users/me/contracts/{contractId}` | như trên | Hỏi về một hợp đồng cụ thể |
| `get_my_wallet` | `GET /api/v1/users/me/wallet` | số dư, trạng thái ví | "Ví còn bao nhiêu?" |
| `list_my_wallet_transactions` | `GET /api/v1/users/me/wallet/transactions` | id, loại, mục đích, số tiền, trạng thái, thời gian | "Giao dịch gần đây" |
| `list_my_transactions` | `GET /api/v1/users/me/transactions` | id, loại thanh toán, số tiền, trạng thái, thời gian | "Tôi đã thanh toán những gì?" |
| `get_my_payment` | `GET /api/v1/users/me/payments/{paymentId}` | loại, số tiền, phương thức, `PaymentStatus`, thời gian | "Thanh toán VNPay vừa rồi thành công chưa?" |
| `list_my_withdrawals` | `GET /api/v1/users/me/wallet/withdrawals` | id, số tiền, `WithdrawalRequestStatus`, thời gian | "Yêu cầu rút tiền tới đâu rồi?" |
| `list_my_complaints` | `GET /api/v1/users/me/complaints` | id, tiêu đề / loại, `ComplaintStatus`, thời gian | "Khiếu nại của tôi…" |
| `get_my_complaint` | `GET /api/v1/users/me/complaints/{complaintId}` | trạng thái, yêu cầu bổ sung (nếu có), thời gian | "Vì sao khiếu nại cần bổ sung?" |
| `list_my_support_requests` | `GET /api/v1/users/me/support-requests` | mã, trạng thái, câu hỏi (cắt ngắn), thời gian gửi / trả lời | "Câu hỏi tôi gửi nhân viên đã có trả lời chưa?" ([14](./14-yeu-cau-ho-tro.md)) |
| `get_unread_notification_count` | `GET /api/v1/users/me/notifications/unread-count` | số lượng | "Tôi có thông báo mới không?" |

### Chỉ `STUDENT`

| Tool | Endpoint | Dùng khi |
|---|---|---|
| `list_my_studying_requests` | `GET /api/v1/students/me/studying-requests` | "Tin tìm gia sư của tôi đã được duyệt chưa?" |
| `get_my_studying_request` | `GET /api/v1/students/me/studying-requests/{requestId}` | Hỏi về một tin |
| `list_offers_for_my_request` | `GET /api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests` | "Có gia sư nào ứng tuyển tin của tôi chưa?" |
| `list_my_applications_to_tutors` | `GET /api/v1/students/me/student-tutor-requests` | "Các yêu cầu tôi gửi gia sư thế nào rồi?" |
| `list_my_payment_installments` | `GET /api/v1/students/me/contracts/{contractId}/payment-installments` | "Kỳ học phí tiếp theo khi nào, bao nhiêu?" |

### Chỉ `TUTOR`

| Tool | Endpoint | Dùng khi |
|---|---|---|
| `get_my_tutor_profile` | `GET /api/v1/tutors/me` | "Hồ sơ của tôi duyệt chưa?" (`TutorProfileStatus`) |
| `list_my_certificates` | `GET /api/v1/tutors/me/certificates` | "Chứng chỉ nào bị từ chối / hết hạn?" |
| `list_my_teaching_requests` | `GET /api/v1/tutors/me/teaching-requests` | "Tin dạy của tôi đang ở trạng thái nào?" |
| `list_student_requests_for_my_post` | `GET /api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests` | "Có học viên nào đăng ký tin của tôi?" |
| `list_my_offers_to_students` | `GET /api/v1/tutors/me/tutor-student-requests` | "Các đề nghị dạy tôi đã gửi?" |

Tổng: 11 tool chung + 5 tool học viên = 16 tool cho `STUDENT`; 11 + 5 = 16 tool cho `TUTOR`. Mỗi lời gọi model
chỉ thấy tối đa 6 nhờ chọn tool động.

**Không có tool:**

- **Buổi học**: buổi học chỉ phục vụ thanh toán theo buổi; câu hỏi liên quan trả lời qua
  `list_my_payment_installments` và tài liệu nhóm `payment`.
- **`EMPLOYEE`, `ADMIN`**: không tra cứu dữ liệu qua trợ lý (QĐ9).
- **Tạo / hủy yêu cầu hỗ trợ**: chỉ người dùng tự làm qua hộp thoại ([14](./14-yeu-cau-ho-tro.md)); trợ lý chỉ đọc trạng thái.

## 5.4. Lọc và rút gọn kết quả

- Bỏ trường ngoài danh sách `fields`; với `ContractResponse` bỏ `studentEmail`, `tutorEmail`, `lastName`, các
  ID nội bộ không cần (`createdByUserId`, `signedByUserId`…).
- Danh sách dài → giữ `max_items` mục mới nhất + tổng số, ví dụ `{"total": 37, "items": [...10 mục]}`.
- Thời gian đổi sang giờ Việt Nam, định dạng `dd/MM/yyyy HH:mm`; tiền định dạng `100.000 đ`.
- Trạng thái giữ mã enum (để truy xuất glossary) kèm nhãn tiếng Việt từ bảng ánh xạ cố định.

## 5.5. Lỗi

| Backend trả | Trợ lý xử lý |
|---|---|
| `401` | Báo phiên đăng nhập hết hạn; widget tự làm mới token và gửi lại một lần |
| `403` | "Bạn không có quyền xem thông tin này" (không thử tool khác để vòng qua) |
| `404` | "Không tìm thấy" — không suy đoán |
| `5xx` / timeout (5 giây) | "Tạm thời chưa tra cứu được", vẫn trả phần trả lời từ tài liệu nếu có |
| Bị `ToolGate` chặn (ngoài allowlist, ID lạ, gọi lặp) | Trả cho model thông báo lỗi ngắn để nó đổi cách; ghi `tool_call_log.blocked_reason` |

## 5.6. Làm trước khi API sẵn sàng

- `BackendClient` có chế độ **mock**: đọc fixture theo kịch bản trong `eval/fixtures/scenarios/<kịch bản>/<tool>.json`
  (bật bằng `ASSISTANT_TOOLS_MODE=mock`; định dạng ở [13](./13-thiet-ke-eval.md) mục 13.4). Mock chỉ thay tầng
  HTTP — lời gọi vẫn đi qua `ToolGateMiddleware` đầy đủ. Dùng cho test, eval và demo.
- Mỗi tool có test hợp đồng (contract test, tầng T2): gọi backend thật ở môi trường dev, kiểm tra các trường trong
  `fields` vẫn tồn tại — phát hiện sớm khi DTO đổi tên trường.
