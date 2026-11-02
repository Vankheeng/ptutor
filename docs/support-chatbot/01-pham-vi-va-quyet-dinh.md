# 1. Phạm vi và quyết định

> 💡 **File này nói về gì:** trợ lý giải quyết vấn đề gì, ai được dùng, làm gì / không làm gì, và các quyết định lớn kèm lý do. Đây là file nên đọc đầu tiên trong phần kỹ thuật.

## 1.1. Bối cảnh

> 💡 **Nói đơn giản:** Nghiệp vụ PTutor có nhiều "trạng thái" (tin đăng chờ duyệt, hợp đồng chờ ký, khiếu nại chờ bằng chứng…). Người dùng hay không biết mình đang ở bước nào → hỏi nhân viên rất nhiều. Bảng dưới liệt kê các trạng thái này lấy đúng từ code.

Nghiệp vụ của PTutor đan xen nhiều luồng, mỗi luồng có trạng thái riêng (theo enum trên `dev`):

| Luồng | Trạng thái (enum) |
|---|---|
| Tin học của học viên (`studying_requests`), tin dạy của gia sư (`teaching_requests`) | `RequestStatus`: `DRAFT`, `PENDING_REVIEW`, `OPEN`, `MATCHED`, `CLOSED`, `CANCELLED`, `REJECTED` |
| Đề nghị / ứng tuyển hai chiều (`student_tutor_requests`, `tutor_student_requests`) | `ApplicationStatus`: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` |
| Hợp đồng | `ContractStatus`: `PENDING`, `ACTIVE`, `COMPLETED`, `CANCELLED`; yêu cầu hủy: `CancelContractRequestStatus` |
| Thanh toán | `PaymentType`: `WALLET_TOP_UP`, `TUITION_PAYMENT`, `STUDYING_REQUEST_FEE`, `TEACHING_REQUEST_FEE`; `PaymentStatus`; kỳ thanh toán: `PaymentInstallmentStatus` |
| Buổi học (phục vụ thanh toán theo buổi) | `LessonStatus`: `SCHEDULED`, `PENDING_CONFIRMATION`, `CONFIRMED`, `COMPLETED`, `CANCELLED` |
| Ví, rút tiền | `WalletTransaction*`, `WithdrawalRequestStatus`: `PENDING`, `PROCESSING`, `CANCELLED`, `REJECTED`, `COMPLETED` |
| Hồ sơ, chứng chỉ gia sư | `TutorProfileStatus`, `CertificateStatus` (`PENDING`, `VERIFIED`, `REJECTED`, `EXPIRED`) |
| Khiếu nại | `ComplaintStatus`: `PENDING`, `IN_REVIEW`, `AWAITING_EVIDENCE`, `RESOLVED`, `REJECTED`, `CANCELLED` |
| Tài khoản | `UserStatus`, tạm khóa (`SuspensionType`) |

Người dùng thường không biết mình đang ở bước nào, bước tiếp theo là gì, vì sao bị từ chối, và quy định
áp dụng ra sao. Câu hỏi lặp lại dồn về nhân viên (chăm sóc khách hàng `EmployeeJobFunction.CUSTOMER_SERVICE` và kế toán
`ACCOUNTANT` — hai chức năng nhân viên duy nhất của nền tảng).

## 1.2. Mục tiêu

> 💡 **Nói đơn giản:** Năm điều trợ lý phải đạt, mỗi điều có cách đo cụ thể ở file 10 (để chứng minh được trong báo cáo, không chỉ nói "tốt").

| Mã | Mục tiêu | Đo bằng ([10](./10-danh-gia.md)) |
|---|---|---|
| M1 | Trả lời đúng câu hỏi về quy định, có trích dẫn | Tỉ lệ trả lời đúng, độ chính xác trích dẫn |
| M2 | Hướng dẫn thao tác đúng vai trò, dẫn tới đúng trang | Tỉ lệ hướng dẫn đúng, tỉ lệ link hợp lệ |
| M3 | Tra cứu nhanh dữ liệu của chính người hỏi | Tỉ lệ chọn đúng tool, 0 rò rỉ dữ liệu người khác |
| M4 | Không bịa quy định | Tỉ lệ khẳng định không có căn cứ lọt qua; tỉ lệ từ chối đúng |
| M5 | Phản hồi đủ nhanh | p95 thời gian một lượt ≤ 10 giây (khởi đầu; gồm cả bước kiểm chứng) |

## 1.3. Đối tượng và phạm vi theo vai trò

> 💡 **Nói đơn giản:** Ai được dùng trợ lý và thấy được gì. Học viên chỉ thấy tài liệu dành cho học viên, gia sư chỉ thấy tài liệu cho gia sư; cả hai chỉ tra được dữ liệu **của chính mình**. Admin không chat, chỉ quản lý kho tài liệu.

Vai trò lấy từ claim `role` trong access token (`UserRole`).

| Vai trò | Dùng trợ lý | Tài liệu được thấy | Tra cứu dữ liệu |
|---|---|---|---|
| Khách (chưa đăng nhập) | **Không** | — | — |
| `STUDENT` | Có | `PUBLIC` + `STUDENT` | Có — dữ liệu của chính mình ([05](./05-cong-cu-tra-cuu.md)) |
| `TUTOR` | Có | `PUBLIC` + `TUTOR` | Có — dữ liệu của chính mình |
| `EMPLOYEE` | Không dùng widget hỏi đáp | — | **Không** |
| `ADMIN` | Quản lý kho tri thức (soạn, duyệt, công bố, khôi phục phiên bản), xem thống kê và câu hỏi chưa trả lời được | Tất cả (trong trang quản trị) | **Không** |

## 1.4. Trong phạm vi / ngoài phạm vi

> 💡 **Nói đơn giản:** Ranh giới việc trợ lý làm. Quan trọng nhất: trợ lý **chỉ đọc và hướng dẫn**, không bao giờ làm thay (hủy, thanh toán…).

**Trong phạm vi**

- Câu hỏi về quy định, chính sách, phí, trạng thái và ý nghĩa trạng thái, quy trình xử lý.
- Hướng dẫn thao tác trên giao diện, kèm link tới trang (chỉ trang có trong danh sách route cho phép).
- Tra cứu chỉ-đọc dữ liệu của chính người hỏi.
- Hội thoại nhiều lượt (hiểu câu hỏi tiếp nối như "còn hợp đồng kia thì sao?").
- Chuyển câu hỏi chưa trả lời được cho nhân viên hỗ trợ — **người dùng tự gửi**; nhân viên `CUSTOMER_SERVICE` xử lý trên
  trang riêng ([14](./14-yeu-cau-ho-tro.md)).

**Ngoài phạm vi**

- Thực hiện thao tác ghi thay người dùng (hủy hợp đồng, thanh toán, rút tiền, gửi khiếu nại…) — QĐ7.
- Tra cứu lịch từng buổi học: buổi học chỉ phục vụ việc thanh toán theo buổi, nên trợ lý trả lời qua **kỳ thanh
  toán** của hợp đồng, không có tool riêng cho buổi học.
- Tư vấn học tập, giải bài tập (thuộc "chatbot hỗ trợ học tập", là tính năng khác).
- Tìm và gợi ý gia sư — thuộc tính năng recommendation; trợ lý chỉ hướng dẫn cách dùng trang tìm kiếm.
- Trả lời thay nhân viên trong việc phân xử khiếu nại hay quyết định hoàn tiền.
- Câu hỏi không liên quan đến nền tảng — bị chặn bởi lớp chặn ngoài phạm vi ([04](./04-luong-hoi-dap.md) mục 4.11).

## 1.5. Quyết định

> 💡 **Nói đơn giản:** Mỗi dòng là một lựa chọn thiết kế (đánh mã QĐ1, QĐ2…) và lý do chọn. Các file sau hay nhắc "QĐx" — quay lại đây để xem vì sao. Khi bảo vệ, bảng này trả lời câu "tại sao làm vậy?".

| Mã | Quyết định | Lý do |
|---|---|---|
| QĐ1 | Service riêng `assistant-service` | `ai-service` là service không trạng thái cho đề xuất; trợ lý có trạng thái (hội thoại, kho tri thức, log) và vòng đời khác. Lỗi hay nâng cấp bên này không ảnh hưởng bên kia |
| QĐ2 | Backend là cổng duy nhất | Dùng lại xác thực JWT, `AccountStatusFilter`, giới hạn tần suất; `assistant-service` chỉ lắng nghe nội bộ như quy ước của `ai-service` |
| QĐ3 | RAG truy xuất lai, lọc vai trò, bắt buộc trích dẫn | Vector cho ngữ nghĩa, full-text cho từ khóa chính xác ("VNPay", "AWAITING_EVIDENCE"), rerank để xếp lại |
| QĐ4 | Kho tri thức trong DB, admin quản lý | Người vận hành là admin, không làm việc qua Git; bản đang dùng phải sửa / công bố được ngay trên hệ thống. Bản khởi tạo sinh từ code để admin không phải viết từ đầu |
| QĐ5 | Tool chỉ GET, token người hỏi | Dùng lại nguyên kiểm soát quyền của backend: trợ lý không đọc được nhiều hơn những gì người dùng tự xem trên giao diện |
| QĐ6 | Schema riêng, role DB riêng | Dữ liệu nghiệp vụ chỉ đi qua API (QĐ5) |
| QĐ7 | Không thao tác ghi | Tránh hành động không thể hoàn tác do model hiểu nhầm; không cần cơ chế người dùng phê duyệt |
| QĐ8 | bge-m3 qua `ai-service` | Thuộc nhóm tốt nhất cho tiếng Việt trong cỡ ~500M tham số, chạy CPU, đã có sẵn cùng model rerank; đổi được qua `EmbeddingProvider` nếu đánh giá cho thấy cần ([12](./12-co-so-ly-thuyet.md) mục 12.5) |
| QĐ9 | Chỉ `STUDENT`, `TUTOR` dùng hỏi đáp; nhân viên không tra cứu | Theo phạm vi nhóm chốt (Q1, Q2) |
| QĐ10 | Agent đơn + harness 6 hook | Bài toán chỉ cần retrieval + tool + structured output; thêm agent / tree search chỉ tăng độ trễ, chi phí, điểm lỗi ([12](./12-co-so-ly-thuyet.md)) |
| QĐ11 | Kiểm chứng trích dẫn | Bắt loại lỗi "trích dẫn có thật nhưng không chứa điều được khẳng định" mà kiểm tra hình thức không bắt được |
| QĐ12 | Cổng hồi quy cho mọi thay đổi | Không đo được thì không được thay đổi; thay đổi làm giảm chỉ số cũ là thoái lui |
| QĐ13 | Bộ nhớ chỉ trong một hội thoại; không lưu dài hạn / xuyên phiên. Phiên kết thúc khi đóng tab hoặc đăng xuất, không hết hạn theo thời gian | Dữ liệu người dùng đổi trạng thái liên tục và đã có trong DB nghiệp vụ; lưu bản sao gây trả lời bằng dữ liệu cũ, rủi ro gán nhầm người dùng, trái tối thiểu hóa dữ liệu ([04](./04-luong-hoi-dap.md) mục 4.10) |
| QĐ14 | Lớp chặn câu hỏi ngoài phạm vi trước khi gọi model (luật + so khớp embedding, Actor là tầng cuối) | Tiết kiệm chi phí, giữ trợ lý đúng vai; dùng lại vector đã có nên gần như không tốn thêm; ưu tiên không chặn nhầm ([04](./04-luong-hoi-dap.md) mục 4.11) |
| QĐ15 | Chuyển câu hỏi cho nhân viên bằng **yêu cầu hỗ trợ** riêng (không dùng Khiếu nại), người dùng tự gửi | Đúng nghĩa nghiệp vụ; giữ QĐ7 ([14](./14-yeu-cau-ho-tro.md)) |
| QĐ16 | Không dùng Graph RAG | Kho nhỏ, theo chủ đề, câu hỏi chủ yếu 1–2 đoạn; quan hệ dữ liệu người dùng đã có trong DB và lấy qua tool; chi phí dựng / duy trì đồ thị cao ([12](./12-co-so-ly-thuyet.md) mục 12.5) |

## 1.6. Các câu hỏi đã chốt

> 💡 **Nói đơn giản:** Những câu hỏi nhóm đã trả lời trong quá trình thiết kế (Q1–Q6) và chúng làm thiết kế thay đổi thế nào.

| # | Câu hỏi | Chốt | Ảnh hưởng |
|---|---|---|---|
| Q1 | Mở cho khách chưa đăng nhập? | Không | Không có endpoint public |
| Q2 | Nhân viên tra cứu dữ liệu qua trợ lý? | Không | Không có tool cho `EMPLOYEE` / `ADMIN` |
| Q3 | Học viên cần xem danh sách buổi học? | Buổi học chỉ phục vụ thanh toán theo buổi | Bỏ tool buổi học và đề xuất thêm endpoint; dùng tool kỳ thanh toán |
| Q4 | Ai viết, duyệt nội dung kho tri thức? | Admin | Kho tri thức trong DB, trang quản trị là bắt buộc (QĐ4) |
| Q5 | Nhà cung cấp embedding? | `ai-service` bge-m3 | Vector 1024 chiều; đoạn ≤ ~400 token (`/embed` cắt ở 512 token); phụ thuộc nhánh recommendation vào `dev` |
| Q6 | Lưu hội thoại bao lâu? | Nhóm thiết kế đề xuất | 90 ngày cho nội dung; không lưu kết quả tool, không lưu token ([09](./09-bao-mat-va-quyen-rieng-tu.md)) |

## 1.7. Phụ thuộc

> 💡 **Nói đơn giản:** Những thứ phải có trước thì trợ lý mới chạy thật được — không nằm trong tay phần chatbot.

- **`ai-service` phải có trên `dev`** (đang ở nhánh `feat/tutor-recommendation`). Trước khi gộp, `assistant-service`
  dùng `EmbeddingProvider` giả lập (`fake`) để phát triển và test; không được nạp kho tri thức thật bằng
  embedding giả.
- **Trang quản trị trên frontend**: `dev` chưa có khu vực admin; trang quản lý kho tri thức và trang xử lý yêu cầu
  hỗ trợ của nhân viên cần layout quản trị chung (có thể dùng chung với các chức năng `Admin*` khác).
