---
id: tin-nhan-day-quy-dinh
title: Quy định tin nhận dạy
type: policy
audience: [TUTOR]
features: [teaching-request]
routes: [/tutor/sections/teaching-requests, /tutor/sections/teaching-requests/:requestId]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: MyTeachingRequestsPage"
  - "backend: TeachingRequestController POST/GET/PUT /api/v1/tutors/me/teaching-requests, PATCH /{id}/status, POST /{id}/cancel"
  - "backend: TeachingRequestService.create/update/updateStatus/cancel/activateAfterPayment/requiresReview (ghi chú hoặc môn tự nhập -> PENDING_REVIEW)"
  - "backend: PaymentController POST /api/v1/tutors/me/teaching-requests/{id}/payments/vnpay|wallet"
  - "backend: AdminTeachingRequestService.approve/reject (hoàn phí vào ví)"
  - "backend: PublicTeachingRequestController; StudentTutorRequestService (quantity -> MATCHED)"
  - "frontend: MyTeachingRequestsPage, TeachingRequestDetailPage"
error_codes: [SUBJECT_SOURCE_REQUIRED, INVALID_SUBJECT, INVALID_GRADE, INVALID_TEACHING_REQUEST_STATUS_TRANSITION, INVALID_TEACHING_REQUEST_PAYMENT_STATUS]
keywords: [tin nhận dạy, đăng tin dạy, yêu cầu dạy, đăng bài gia sư, tin nhan day, dang tin day, yeu cau day, teaching request]
todo: []
---

# Quy định tin nhận dạy

## Giao diện tin nhận dạy

Gia sư mở **Yêu cầu dạy của tôi** để tạo và quản lý tin. Trang có biểu mẫu **Tạo yêu cầu dạy**, **Chỉnh sửa yêu cầu dạy** và các thao tác trên từng tin theo trạng thái.

## Nội dung tin nhận dạy

Tin nhận dạy mô tả lớp gia sư muốn mở. Tin có môn học, các lớp dạy, khu vực dạy, hình thức dạy, số học viên cần nhận và lịch dạy. Môn học phải chọn đúng một cách: chọn trong danh sách môn, hoặc tự nhập tên môn.

## Đăng tin nhận dạy và xét duyệt

Tin nhận dạy mới tạo là bản nháp. Gia sư trả phí đăng tin qua VNPay hoặc bằng số dư ví. Sau khi trả phí:

- Tin chọn môn trong danh sách và không có ghi chú được đăng ngay, chuyển sang đang mở.
- Tin có môn học tự nhập hoặc có ghi chú chuyển sang chờ duyệt.

Khi bị từ chối, gia sư nhận thông báo ghi lý do và được hoàn phí đăng tin vào số dư ví.

Tin nhận dạy đang mở hiện ở trang chủ PTutor và trang **Browse Teaching Requests** của học viên.

## Sửa, tạm đóng, hủy tin nhận dạy

- Sửa được khi tin là bản nháp, đang chờ duyệt hoặc đang mở. Sửa tin đang mở mà có môn tự nhập hoặc có ghi chú thì tin quay lại chờ duyệt.
- Tạm đóng được tin đang mở, mở lại được tin tạm đóng.
- Hủy được khi tin là bản nháp, chờ duyệt, đang mở hoặc tạm đóng.

## Tin nhận dạy đủ học viên

Khi gia sư chấp nhận đủ số học viên đã ghi trong tin, tin chuyển sang đã ghép và không nhận thêm đơn đăng ký học.

## Tiêu chí duyệt tin nhận dạy (đề xuất)

Với môn tự nhập hoặc ghi chú, nhân viên kiểm tra tên môn và lớp nhất quán, mô tả rõ, không đưa thông tin liên hệ ngoài nền tảng, không quảng cáo hoặc cam kết gây hiểu lầm. Nếu từ chối, ghi rõ lý do; hệ thống hoàn phí tin bị từ chối vào ví. Đề xuất xét trong 2 ngày làm việc, nhưng mã nguồn chưa tự bảo đảm thời hạn.
