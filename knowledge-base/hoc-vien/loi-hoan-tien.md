---
id: loi-hoan-tien
title: Lỗi thường gặp khi gửi yêu cầu hoàn tiền
type: error
audience: [STUDENT]
features: [refund]
routes: [/student/contracts/:contractId/refund-request, /student/refund-requests/:refundRequestId, /student/contracts/:contractId/lessons]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: components/refund/refundFormat.ts errorMessages (câu tiếng Việt cho từng mã lỗi)"
  - "frontend: pages/student/StudentRefundRequestCreatePage.tsx (Chọn ít nhất một buổi và nguyên nhân., Vui lòng mô tả sự việc.)"
  - "backend: RefundRequestService.create/cancel; LessonService.confirmByStudent"
error_codes: [REFUND_REQUEST_ALREADY_OPEN, CONTRACT_NOT_REFUNDABLE, LESSON_NOT_DISPUTABLE, INVALID_CLAIMED_OUTCOME, INVALID_REFUND_REQUEST_STATUS_TRANSITION, LESSON_COMPLETION_ON_HOLD]
keywords: [lỗi hoàn tiền, không gửi được yêu cầu hoàn tiền, không hủy được yêu cầu, loi hoan tien, khong gui duoc yeu cau hoan tien]
todo: []
---

# Lỗi thường gặp khi gửi yêu cầu hoàn tiền

## "Hợp đồng này đã có một yêu cầu hoàn tiền đang xử lý."

Câu này nghĩa là hợp đồng đã có một yêu cầu hoàn tiền chưa có kết luận. Mỗi hợp đồng chỉ có một yêu cầu mở tại một thời điểm. Cách khắc phục: chờ yêu cầu cũ có kết luận rồi gửi yêu cầu mới, hoặc hủy yêu cầu cũ nếu nó còn "Chờ tiếp nhận".

## "Hợp đồng chưa có học phí nào đã đóng để hoàn."

Câu này nghĩa là hợp đồng chưa ký, hoặc bạn chưa đóng khoản học phí nào. Cách khắc phục: không có khoản nào để hoàn; nếu buổi học không diễn ra, bạn có thể gửi khiếu nại.

## "Có buổi học không còn ở trạng thái chờ xác nhận. Vui lòng tải lại trang."

Câu này nghĩa là một buổi bạn chọn vừa được xác nhận, tự hoàn tất hoặc bị hủy. Cách khắc phục: tải lại trang và chọn lại các buổi còn chờ xác nhận.

## "Vui lòng chọn nguyên nhân cho từng buổi." hoặc "Chọn ít nhất một buổi và nguyên nhân."

Câu này nghĩa là bạn chưa chọn buổi nào, hoặc có buổi chưa chọn nguyên nhân. Cách khắc phục: chọn **Gia sư không dạy** hoặc **Tôi nghỉ buổi này** cho từng buổi.

## "Vui lòng mô tả sự việc."

Câu này nghĩa là ô mô tả đang trống. Cách khắc phục: viết ngắn gọn chuyện gì đã xảy ra với buổi học.

## "Yêu cầu đã được xử lý. Vui lòng tải lại trang."

Câu này hiện khi bạn hủy yêu cầu, nghĩa là yêu cầu không còn "Chờ tiếp nhận" vì kế toán đã tiếp nhận. Cách khắc phục: tải lại trang; yêu cầu đang được xử lý thì không hủy được.

## "Buổi học đang bị tạm giữ, chưa thể xác nhận."

Câu này hiện khi bạn xác nhận buổi học đang nằm trong yêu cầu hoàn tiền hoặc hợp đồng có khiếu nại chưa xong. Cách khắc phục: chờ kết luận rồi xác nhận lại.
