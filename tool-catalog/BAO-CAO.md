# Báo cáo danh mục công cụ tra cứu

## 1. Nguồn đã đọc

- Code: nhánh `dev`, commit `27f6eeb09815385bf92d3d552b0f8f705070a23c` (`git rev-parse dev`). Đọc bằng `git show` / `git grep`; không chuyển nhánh, không sửa file nào trong repo.
- Đặc tả: `feat/support-chatbot:docs/support-chatbot/05-cong-cu-tra-cuu.md` (5.1–5.5), `02-kien-truc.md` (2.3), `04-luong-hoi-dap.md` (4.10), `07-api.md` (7.4, header `X-User-Id`), `14-yeu-cau-ho-tro.md`, `quy-dinh-nen-tang-tu-code.md`.
- Ngày lập: 2026-11-03.

## 2. Tổng quan

- 38 công cụ: 36 `ready`, 2 `planned`.
- Theo vai trò: học viên 24 công cụ `ready` + 2 `planned`; gia sư 26 `ready` + 2 `planned` (14 công cụ dùng chung tính cho cả hai).
- Mọi công cụ là `GET` dưới `/api/v1/users/me/**`, `/api/v1/students/me/**` hoặc `/api/v1/tutors/me/**`; không công cụ nào có tham số định danh người dùng; mọi tham số mã có `source: seen_ids` và `from_tools`.
- Lớp bọc kết quả (`ApiResponse`, `PageResponse`), múi giờ, lỗi chung và endpoint đã loại mô tả một lần ở đầu `tools.yaml` (sheet `Lop_boc`, `Da_loai` trong `tools.xlsx`).
- Quyết định đã áp dụng (2026-11-03):
  - Thêm trường suy ra `createdByMe` cho hợp đồng; lớp lọc so `createdByUserId` với `X-User-Id`, rồi bỏ `createdByUserId` và `signedByUserId`.
  - Giữ `get_my_contract`, ghi rõ tác dụng phụ và lỗi 409 kèm câu cho người dùng.
  - Không thêm `studentFirstName` vào kết quả hoàn tiền; mô tả công cụ hoàn tiền hướng dẫn tra `get_my_contract` để gọi tên bên kia.
  - Không thêm công cụ cho gia sư xem tin tìm gia sư đang mở.
- Quyết định đã áp dụng (đợt 2, 2026-11-03):
  - **Chọn 10 mục cho danh sách:** luật cụ thể ở `wrapper.list_rules` và trường `result_selection` của từng công cụ. Kỳ học phí: kỳ chưa đóng trước theo hạn gần nhất, rồi kỳ đã đóng gần nhất. Buổi học của học viên: mọi buổi chờ xác nhận, rồi buổi sắp diễn ra gần nhất, rồi buổi đã qua gần nhất. Buổi học của gia sư: dùng bộ lọc `status` của chính API (API có phân trang và lọc), lớp lọc không tự lọc. Danh sách khác: mục mới nhất. Mọi danh sách kèm tổng số và số lượng theo `count_by` (ví dụ "12 kỳ, 3 chưa đóng"); với API phân trang, lớp lọc cố định `size = 100` để đếm.
  - **`list_my_transactions`** cố định `source=PAYMENT` (`fixed_params`), model không đổi được. Lý do: API gộp ba nguồn nên trả bằng ví, nạp ví và rút tiền đều ra hai dòng; giao dịch ví và rút tiền đã có công cụ riêng.
  - **Múi giờ:** backend chạy ở UTC (`TZ=UTC` hoặc `-Duser.timezone=UTC`); lớp lọc đổi mọi thời điểm (`LocalDateTime`) từ UTC sang giờ Việt Nam, không đổi ngày lịch và giờ học; cấu hình `ASSISTANT_BACKEND_TIME_ZONE=UTC`. Sửa `ContractTimeProvider.now()` sang UTC trong cùng đợt (quyết định Q5, xem mục 7); sau khi sửa không còn ngoại lệ, `signedAt` khai báo là UTC.
  - **Yêu cầu hỗ trợ:** giữ đúng tên trường theo file 14 (hợp đồng giữa backend và chatbot); vẫn `planned`. Đổi tên thì sửa file 14 và `tools.yaml` cùng lúc; contract test giai đoạn 2 bắt lỗi khi hai bên lệch.

## 3. Bảng tổng

Số trường tính cả trường lồng (lịch học, buổi bị phản đối, minh chứng, địa chỉ…) và trường suy ra.

| func_code | Vai trò | Đường dẫn (GET) | Trường giữ | Trường bỏ | Chọn mục (count_by) | status |
|---|---|---|---|---|---|---|
| list_my_contracts | STUDENT, TUTOR | `/api/v1/users/me/contracts` | 21 | 13 | newest (status) | ready |
| get_my_contract | STUDENT, TUTOR | `/api/v1/users/me/contracts/{contractId}` | 21 | 13 | — | ready |
| get_my_wallet | STUDENT, TUTOR | `/api/v1/users/me/wallet` | 3 | 3 | — | ready |
| list_my_wallet_transactions | STUDENT, TUTOR | `/api/v1/users/me/wallet/transactions` | 11 | 2 | newest (transactionType) | ready |
| get_my_wallet_transaction | STUDENT, TUTOR | `/api/v1/users/me/wallet/transactions/{transactionId}` | 11 | 2 | — | ready |
| list_my_transactions | STUDENT, TUTOR | `/api/v1/users/me/transactions [cố định source=PAYMENT]` | 8 | 1 | newest (status) | ready |
| get_my_payment | STUDENT, TUTOR | `/api/v1/users/me/payments/{paymentId}` | 10 | 3 | — | ready |
| list_my_withdrawals | STUDENT, TUTOR | `/api/v1/users/me/wallet/withdrawals` | 8 | 8 | newest (status) | ready |
| list_my_complaints | STUDENT, TUTOR | `/api/v1/users/me/complaints` | 14 | 5 | newest (status) | ready |
| get_my_complaint | STUDENT, TUTOR | `/api/v1/users/me/complaints/{complaintId}` | 14 | 5 | — | ready |
| list_complaints_against_me | STUDENT, TUTOR | `/api/v1/users/me/complaints/against-me` | 14 | 5 | newest (status) | ready |
| get_complaint_against_me | STUDENT, TUTOR | `/api/v1/users/me/complaints/against-me/{complaintId}` | 14 | 5 | — | ready |
| list_my_notifications | STUDENT, TUTOR | `/api/v1/users/me/notifications` | 8 | 1 | newest (isRead) | ready |
| count_my_unread_notifications | STUDENT, TUTOR | `/api/v1/users/me/notifications/unread-count` | 1 | 0 | — | ready |
| get_my_student_profile | STUDENT | `/api/v1/students/me` | 10 | 11 | — | ready |
| list_my_studying_requests | STUDENT | `/api/v1/students/me/studying-requests` | 18 | 6 | newest (status) | ready |
| get_my_studying_request | STUDENT | `/api/v1/students/me/studying-requests/{requestId}` | 18 | 6 | — | ready |
| list_offers_for_my_request | STUDENT | `/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests` | 15 | 8 | newest (status) | ready |
| get_offer_for_my_request | STUDENT | `/api/v1/students/me/studying-requests/{studyingRequestId}/tutor-requests/{tutorRequestId}` | 15 | 8 | — | ready |
| list_my_applications_to_tutors | STUDENT | `/api/v1/students/me/student-tutor-requests` | 13 | 7 | newest (status) | ready |
| list_my_payment_installments | STUDENT | `/api/v1/students/me/contracts/{contractId}/payment-installments` | 6 | 1 | installments_due_first (status) | ready |
| list_contract_lessons_for_student | STUDENT | `/api/v1/students/me/contracts/{contractId}/lessons` | 10 | 2 | lessons_pending_first (status) | ready |
| list_my_refund_requests | STUDENT | `/api/v1/students/me/refund-requests` | 26 | 8 | newest (status) | ready |
| get_my_refund_request | STUDENT | `/api/v1/students/me/refund-requests/{refundRequestId}` | 26 | 8 | — | ready |
| get_my_tutor_profile | TUTOR | `/api/v1/tutors/me` | 20 | 13 | — | ready |
| list_my_certificates | TUTOR | `/api/v1/tutors/me/certificates` | 10 | 3 | newest (status) | ready |
| get_my_certificate | TUTOR | `/api/v1/tutors/me/certificates/{certificateId}` | 10 | 3 | — | ready |
| list_my_teaching_requests | TUTOR | `/api/v1/tutors/me/teaching-requests` | 24 | 9 | newest (status) | ready |
| get_my_teaching_request | TUTOR | `/api/v1/tutors/me/teaching-requests/{requestId}` | 24 | 9 | — | ready |
| list_student_requests_for_my_post | TUTOR | `/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests` | 11 | 5 | newest (status) | ready |
| get_student_request_for_my_post | TUTOR | `/api/v1/tutors/me/teaching-requests/{teachingRequestId}/student-requests/{requestId}` | 11 | 5 | — | ready |
| list_my_offers_to_students | TUTOR | `/api/v1/tutors/me/tutor-student-requests` | 15 | 8 | newest (status) | ready |
| list_contract_lessons_for_tutor | TUTOR | `/api/v1/tutors/me/contracts/{contractId}/lessons` | 10 | 2 | api_order (status) | ready |
| get_my_lesson | TUTOR | `/api/v1/tutors/me/lessons/{lessonId}` | 10 | 2 | — | ready |
| list_refund_requests_on_my_contracts | TUTOR | `/api/v1/tutors/me/refund-requests` | 26 | 8 | newest (status) | ready |
| get_refund_request_on_my_contract | TUTOR | `/api/v1/tutors/me/refund-requests/{refundRequestId}` | 26 | 8 | — | ready |
| list_my_support_requests | STUDENT, TUTOR | `/api/v1/users/me/support-requests` | 5 | 0 | newest (status) | planned |
| get_my_support_request | STUDENT, TUTOR | `/api/v1/users/me/support-requests/{id}` | 7 | 0 | — | planned |

## 4. Endpoint đã loại

| Endpoint | Lý do |
|---|---|
| GET /api/v1/users/me/bank-account | Dữ liệu nhạy cảm (số tài khoản ngân hàng, tên chủ tài khoản) |
| GET /api/v1/payments/vnpay/ipn | Callback máy chủ–máy chủ của VNPay, không phải dữ liệu của người hỏi |
| GET /api/v1/payments/vnpay/return | Trang VNPay chuyển hướng về, không phải dữ liệu của người hỏi |
| GET /api/v1/studying-requests, /{studyingRequestId} | Gia sư tìm tin đang mở; không phải "của tôi" (đã quyết định không thêm) |
| GET /api/v1/tutors/{tutorId}, /tutors/{tutorId}/certificates | Dữ liệu công khai của gia sư bất kỳ, có tham số định danh người dùng |
| GET /api/v1/teaching-requests, /{requestId} | Tin nhận dạy công khai |
| GET /api/v1/reviews, /subjects, /grades, /provinces, /districts | Dữ liệu công khai, danh mục |
| Mọi POST, PUT, PATCH, DELETE | Ghi dữ liệu |

## 5. Endpoint "của tôi" mà danh sách ở mục 3 của yêu cầu bỏ sót

Không có. Mọi endpoint `GET` "của tôi" trên `dev` (trừ tài khoản ngân hàng) đã có trong danh sách. Đề xuất thêm một công cụ dự kiến: `get_my_support_request` (`GET /api/v1/users/me/support-requests/{id}`), vì file 14 có API xem chi tiết kèm câu trả lời của nhân viên.

Một số chức năng trong danh sách không có API xem chi tiết, nên chỉ có công cụ liệt kê: đơn đăng ký học học viên đã gửi, đề nghị dạy gia sư đã gửi, yêu cầu rút tiền.

## 6. Chỗ khác với bảng 5.3 và đặc tả `05-cong-cu-tra-cuu.md`

1. **Tên công cụ:** `get_unread_notification_count` đổi thành `count_my_unread_notifications` theo quy ước tên bắt đầu bằng `list_` / `get_` / `count_`.
2. **`get_my_wallet`:** bảng 5.3 ghi "trạng thái ví", nhưng `WalletBalanceResponse` không có trường trạng thái; chỉ có `balance`, `pendingBalance`, `currency`.
3. **`list_my_wallet_transactions`:** bảng 5.3 ghi trường `id`; tên thật là `transactionId`.
4. **`list_my_transactions`:** bảng 5.3 mô tả như danh sách thanh toán; thực tế API gộp ba nguồn (thanh toán, bút toán ví, yêu cầu rút) và `type`, `status`, `method` là chuỗi, không phải enum. Danh mục cố định `source=PAYMENT` để công cụ chỉ đọc nguồn thanh toán.
5. **`list_my_withdrawals`:** bảng 5.3 ghi trường `id`; tên thật là `withdrawalId`. API không có bộ lọc trạng thái.
6. **`get_my_complaint`:** bảng 5.3 ghi "yêu cầu bổ sung (nếu có)"; không có trường riêng, lời yêu cầu bổ sung được lưu vào `resolution` (`AdminComplaintService.requestEvidence`).
7. **`list_my_contracts`:** đặc tả 5.2 khai báo `features = {contract, payment}`; danh mục dùng `[contract]`. Thêm trường suy ra `createdByMe`.
8. **Buổi học:** bảng 5.3 ghi "không có tool buổi học"; danh mục có 3 công cụ (`list_contract_lessons_for_student`, `list_contract_lessons_for_tutor`, `get_my_lesson`) theo yêu cầu.
9. **Công cụ thiếu trong bảng 5.3:** `get_my_wallet_transaction`, `list_my_notifications`, `list_complaints_against_me`, `get_complaint_against_me`, `get_my_student_profile`, `get_offer_for_my_request`, `list_my_refund_requests`, `get_my_refund_request`, `get_my_certificate`, `get_my_teaching_request`, `get_student_request_for_my_post`, `list_refund_requests_on_my_contracts`, `get_refund_request_on_my_contract`, cùng 3 công cụ buổi học ở ý 8 và `get_my_support_request` (planned).
10. **Tổng số công cụ:** bảng 5.3 ghi 16 công cụ mỗi vai trò; danh mục có 24 (học viên) và 26 (gia sư) công cụ `ready`.
11. **`list_my_support_requests`:** bảng 5.3 liệt kê như công cụ có sẵn; trên `dev` chưa có API nên đặt `planned`.
12. **Múi giờ (5.4):** đặc tả giả định một múi giờ. Thực tế code trộn ba nguồn: thời điểm do service ghi theo `Clock.systemUTC()` (UTC), `createdAt` / `updatedAt` theo múi giờ của máy chạy Java (`BaseEntity`), và `signedAt` theo giờ Việt Nam (`ContractTimeProvider`). Theo quyết định, backend chạy UTC, `ContractTimeProvider.now()` sửa sang UTC, và `tools.yaml` ghi `time_zone` cho từng trường; đặc tả 5.4 cần thêm cấu hình `ASSISTANT_BACKEND_TIME_ZONE` và luật "không đổi ngày lịch, giờ học".
13. **Chỉ đọc (5.1 ý 1):** hai API `GET` có tác dụng phụ tạo kỳ học phí: `get_my_contract` và `list_my_payment_installments` (cùng gọi `ensureForActiveContract`). Đã ghi `side_effects` trong khai báo.
14. **Phân trang:** bảng 5.3 ghi "tham số phân trang đối chiếu lại khi triển khai". Thực tế 5 API trả danh sách không phân trang: kỳ học phí, buổi học của học viên, chứng chỉ, tin nhận dạy, đơn đăng ký học vào tin của gia sư.
15. **`list_my_certificates`:** bảng 5.3 ghi "chứng chỉ nào hết hạn"; trạng thái `EXPIRED` chưa được hệ thống tự đặt, nên trợ lý cần so `expiryDate` với ngày hiện tại.
16. **`list_complaints_against_me`:** lọc `status` bằng `PENDING` hoặc `CANCELLED` luôn trả rỗng (service bỏ qua); danh mục chỉ cho 4 giá trị còn lại.
17. **`createdByMe`:** đặc tả 5.4 chưa có khái niệm trường suy ra; cần bổ sung vào lớp lọc kết quả (dùng `X-User-Id` theo 07 mục 7.4).
18. **Rút gọn danh sách (5.4):** đặc tả ghi "giữ `max_items` mục mới nhất + tổng số". Danh mục dùng luật chọn mục theo từng loại danh sách và thêm số lượng theo trạng thái (`list_rules`, `result_selection`).
19. **Tham số cố định:** đặc tả 5.2 chưa có khái niệm tham số do harness cố định, model không thấy (`fixed_params`: `source=PAYMENT`, `size=100`).

Tổng: 19 chỗ khác.

## 7. Đề xuất cho backend và thiết kế (không chặn danh mục)

1. **Việc nhóm cần làm: đặt `TZ=UTC`** (hoặc `-Duser.timezone=UTC`) cho backend trong `docker-compose.yml`. Hiện docker-compose và `application-prod.yaml` không đặt múi giờ, nên `createdAt`, `updatedAt` phụ thuộc máy chủ cho tới khi thêm. Lớp lọc giả định backend chạy UTC (`ASSISTANT_BACKEND_TIME_ZONE=UTC`).
2. **Việc nhóm cần làm: sửa `ContractTimeProvider.now()` sang UTC** (quyết định Q5), cùng nhánh với việc đặt `TZ=UTC`, thành một commit riêng, nhỏ vì là code backend chung.
   - Lý do: `now()` đang ghi giờ Việt Nam vào `signed_at` và cả `updated_at` của hợp đồng qua 5 câu update trong `ContractRepository` (ký, từ chối, người soạn hủy, tự hoàn tất, tự hủy khi hết hạn), trong khi `updated_at` ở các lần sửa khác do `@UpdateTimestamp` ghi theo múi giờ máy (UTC sau khi đặt `TZ=UTC`). Không sửa thì cùng một cột lúc là giờ Việt Nam, lúc là UTC, và lớp lọc không phân biệt được: đây là dữ liệu sai, không phải ngoại lệ khai báo được.
   - Cách sửa: `now()` trả `LocalDateTime.now(clock)` (đồng hồ hệ thống là `Clock.systemUTC()`); `today()` giữ giờ Việt Nam vì so ngày hết hạn theo ngày lịch Việt Nam; sửa chú thích class: ngày lịch theo múi giờ nghiệp vụ, thời điểm lưu theo UTC.
   - Test: `ContractServiceTest`, `ContractLifecycleServiceTest` giả lập `now()` nên không vỡ. Thêm test cho `ContractTimeProvider`: lúc 23:30 UTC ngày 1, `today()` là ngày 2 (giờ Việt Nam), `now()` vẫn là 23:30 ngày 1.
   - Dữ liệu cũ: hợp đồng đã ký, hủy, hoàn tất bằng code cũ có `signed_at`, `updated_at` lệch +7 giờ. Với dữ liệu demo, nạp lại dữ liệu mẫu. Nếu phải giữ dữ liệu, trừ 7 giờ cho `signed_at` bằng một lần chạy SQL; `updated_at` không biết dòng nào do `now()` ghi nên chấp nhận lệch.
   - `tools.yaml` khai báo `signedAt` là UTC theo trạng thái sau khi sửa và ghi hai việc này ở `wrapper.time_zones.preconditions`.
3. **Tạo kỳ học phí ngay khi ký hợp đồng**, để `get_my_contract` và `list_my_payment_installments` không còn ghi dữ liệu (đã thống nhất là đề xuất).
4. **Trạng thái chưa dùng:** `CertificateStatus.EXPIRED`, `PaymentInstallmentStatus.CANCELLED`, `WithdrawalRequestStatus.PROCESSING/REJECTED/COMPLETED` chưa có luồng đặt; nhãn trong danh mục vẫn khai báo đủ.
5. **`nextStep`** là chuỗi tự do (hiện chỉ có `CONTRACT_SIGNING`); nên đổi thành enum để danh mục kiểm được giá trị.
6. **Thông báo:** tiêu đề và nội dung là tiếng Anh; trợ lý nên dùng nhãn tiếng Việt của `eventType` thay vì đọc nguyên văn.
7. **Đặc tả 05:** cập nhật bảng 5.3, mục 5.2 và 5.4 theo mục 6 ở trên.

## 8. Nhãn tiếng Việt phải tự đặt

Ưu tiên nhãn trong frontend (trang người dùng, rồi trang quản trị). Các nhãn sau phải tự đặt vì giao diện không có, hoặc giao diện hiển thị tiếng Anh:

| Enum / trường | Nhãn tự đặt | Ghi chú |
|---|---|---|
| RequestStatus | Bản nháp, Chờ duyệt, Đang mở, Đã ghép, Tạm đóng, Đã hủy, Bị từ chối | Trang học viên hiển thị mã viết thường; trang quản trị có "Chờ duyệt", "Đã duyệt", "Đã từ chối". Trùng nhãn với kho tri thức |
| TutorProfileStatus | Chờ duyệt, Đã xác minh, Không được duyệt | Trang gia sư hiển thị Waiting for review, Verified profile, Not approved |
| WalletTransactionPurpose | Nạp ví, Tiền gia sư nhận, Hoàn học phí, Hoàn phí đăng tin dạy, Hoàn phí đăng yêu cầu học, Trả lại tiền rút, Học phí, Phí đăng yêu cầu học, Phí đăng tin dạy, Rút tiền | Một số nhãn mượn từ `studentFormat.ts` |
| WalletTransactionStatus | Đang chờ, Hoàn tất, Thất bại, Đã huỷ | Mượn từ `transactionStatusMeta` (trang quản trị) |
| WithdrawalRequestStatus | Chờ xử lý, Đang xử lý, Đã huỷ, Bị từ chối, Hoàn tất | Chưa có giao diện rút tiền |
| ReferenceType, NotificationReferenceType | Hợp đồng, Buổi học, Thanh toán, Giao dịch ví, Tin tìm gia sư, Tin nhận dạy, Đề nghị dạy, Đơn đăng ký học, Chứng chỉ, Kỳ học phí, Khiếu nại, Tài khoản, Yêu cầu rút tiền, Yêu cầu hoàn tiền | |
| NotificationType | Hệ thống, Yêu cầu, Hợp đồng, Thanh toán, Khiếu nại | |
| NotificationEventType | 40 nhãn (xem `tools.yaml`) | Giao diện chỉ hiện tiêu đề tiếng Anh |
| nextStep | Tạo và ký hợp đồng (`CONTRACT_SIGNING`) | |
| ComplaintStatus, ComplaintCategory, ComplaintFaultParty | Lấy từ trang quản trị `complaintFormat.ts` | Trang khiếu nại cho người dùng chưa có |
| Trạng thái yêu cầu hỗ trợ (planned) | Chờ xử lý, Đang xử lý, Đã trả lời, Đã đóng, Đã hủy | Theo thiết kế 14 |

## 9. Trường chứa nội dung người dùng tự viết

Các trường dưới đây (đánh dấu `user_text: true` trong `tools.yaml`) phải bọc là dữ liệu theo đặc tả 5.1 ý 7. Gồm cả văn bản do nhân viên viết (lý do từ chối, kết luận khiếu nại) và mô tả thông báo chung do PTutor soạn.

| Công cụ | Trường |
|---|---|
| list_my_contracts | preferredSchedule |
| get_my_contract | preferredSchedule |
| list_my_transactions | description |
| list_my_withdrawals | note, rejectionReason |
| list_my_complaints | title, content, resolution, respondentResponse |
| get_my_complaint | title, content, resolution, respondentResponse |
| list_complaints_against_me | title, content, resolution, respondentResponse |
| get_complaint_against_me | title, content, resolution, respondentResponse |
| list_my_notifications | content |
| get_my_student_profile | introduction, learningStyle, personalityTags, goalsDescription, currentLevel, weakPoints |
| list_my_studying_requests | title, description, note, learningGoals, preferredSchedule |
| get_my_studying_request | title, description, note, learningGoals, preferredSchedule |
| list_offers_for_my_request | studyingRequestTitle, preferredSchedule, message |
| get_offer_for_my_request | studyingRequestTitle, preferredSchedule, message |
| list_my_applications_to_tutors | teachingRequestTitle, customSubjectName, preferredSchedule, message |
| list_contract_lessons_for_student | title, note |
| list_my_refund_requests | description, disputedLessons[].title, tutorResponse, rejectionReason |
| get_my_refund_request | description, disputedLessons[].title, tutorResponse, rejectionReason |
| get_my_tutor_profile | introduction, education, teachingStyleTags, teachingMethodology, strengthSubjects, targetStudentType, profileRejectionReason |
| list_my_certificates | name, issuingOrganization, description, rejectionReason |
| get_my_certificate | name, issuingOrganization, description, rejectionReason |
| list_my_teaching_requests | customSubjectName, title, note, preferredSchedule, description, rejectionReason |
| get_my_teaching_request | customSubjectName, title, note, preferredSchedule, description, rejectionReason |
| list_student_requests_for_my_post | preferredSchedule, message |
| get_student_request_for_my_post | preferredSchedule, message |
| list_my_offers_to_students | studyingRequestTitle, preferredSchedule, message |
| list_contract_lessons_for_tutor | title, note |
| get_my_lesson | title, note |
| list_refund_requests_on_my_contracts | description, disputedLessons[].title, tutorResponse, rejectionReason |
| get_refund_request_on_my_contract | description, disputedLessons[].title, tutorResponse, rejectionReason |
| list_my_support_requests | question |
| get_my_support_request | question, answer |

## 10. Trường nhạy cảm đã bỏ

Đặt `keep: false` kèm lý do: email, số điện thoại, họ (`…LastName`), họ tên đầy đủ (`studentName`, `tutorName`, `accountHolderName`), ngày sinh, giới tính, địa chỉ chi tiết, ảnh đại diện, thông tin ngân hàng trong yêu cầu rút (mã, tên ngân hàng, số tài khoản đã che, mã chuyển khoản), mã giao dịch cổng thanh toán (`transactionCode`, `providerTransactionNo`), đường dẫn minh chứng và tệp chứng chỉ, các mã tài khoản và mã nội bộ không dùng cho công cụ khác. `build_xlsx.py` tự kiểm tra các tên trường nhạy cảm phổ biến phải `keep: false`.

## 11. Kiểm tra đã chạy

1. Mọi `request.path` của công cụ `ready` có thật trên `dev`, là `GET`, và vai trò khớp `SecurityConfig` (`/users/me/**` cho STUDENT, TUTOR; `/students/me/**` chỉ STUDENT; `/tutors/me/**` chỉ TUTOR). Tham số query khớp chữ ký controller.
2. Mọi tên trường trong `schema` (kể cả record lồng) khớp class kết quả trên `dev`; trường suy ra `createdByMe` được đánh dấu `derived: true`.
3. Mọi giá trị enum trong `enum_labels` khớp enum Java; giá trị enum của tham số lọc khớp enum, trừ hai chỗ cố ý ở mục 6 ý 16 và tham số `sort` (kiểu `Sort.Direction` của Spring).
4. Không công cụ nào có tham số định danh người dùng; mọi tham số mã có `source: seen_ids` và `from_tools` trỏ tới công cụ đã khai báo.
5. Trường nhạy cảm `keep: false` kèm `reason`.
6. Mọi công cụ có `max_items` đều có `result_selection` với chiến lược thuộc `list_rules` và `count_by` là trường có thật của kết quả; `fixed_params` không chứa tham số định danh người dùng và không trùng tham số model điền. Tham số đối chiếu với code gồm cả tham số cố định.
7. Mô tả: mỗi cặp công cụ dễ nhầm (liệt kê / xem chi tiết; khiếu nại mình gửi / khiếu nại về mình; giao dịch ví / thanh toán; đề nghị dạy / đơn đăng ký học) đều có câu "Không dùng … (dùng …)" chỉ sang công cụ kia.
8. `python build_xlsx.py` chạy không lỗi; cú pháp YAML hợp lệ.

## 12. Câu hỏi cần quyết định

Không còn câu hỏi mở. Q1–Q4 đã quyết định (mục 2); Q5 (sửa `ContractTimeProvider.now()` sang UTC cùng đợt đặt `TZ=UTC`): có, xem mục 7 ý 2.
