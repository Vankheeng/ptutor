---
id: tin-nhan-day-quy-dinh
title: Quy định tin nhận dạy
type: policy
audience: [TUTOR]
features: [teaching-request]
routes: [/tutor/sections/:section, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: TeachingRequestController POST/GET/PUT /api/v1/tutors/me/teaching-requests, PATCH /{id}/status, POST /{id}/cancel"
  - "backend: TeachingRequestService.create/update/updateStatus/cancel/activateAfterPayment/requiresReview (ghi chú hoặc môn tự nhập -> PENDING_REVIEW)"
  - "backend: PaymentController POST /api/v1/tutors/me/teaching-requests/{id}/payments/vnpay|wallet"
  - "backend: AdminTeachingRequestService.approve/reject (hoàn phí vào ví)"
  - "backend: PublicTeachingRequestController; StudentTutorRequestService (quantity -> MATCHED)"
  - "frontend: TutorLayout mục 'Yêu cầu dạy của tôi' -> TutorComingSoonPage (Sắp ra mắt)"
error_codes: [SUBJECT_SOURCE_REQUIRED, INVALID_SUBJECT, INVALID_GRADE, INVALID_TEACHING_REQUEST_STATUS_TRANSITION, INVALID_TEACHING_REQUEST_PAYMENT_STATUS]
keywords: [tin nhận dạy, đăng tin dạy, yêu cầu dạy, đăng bài gia sư, tin nhan day, dang tin day, yeu cau day, teaching request]
todo:
  - "Khi nào có giao diện để gia sư tạo, sửa, đóng, hủy tin nhận dạy? Tên trang và tên nút là gì?"
  - "Tiêu chí PTutor dùng để duyệt tin nhận dạy có môn học tự nhập hoặc có ghi chú là gì?"
---

# Quy định tin nhận dạy

## Giao diện tin nhận dạy

Hiện giao diện gia sư chưa có trang tạo và quản lý tin nhận dạy. Mục **Yêu cầu dạy của tôi** trong menu đang hiện "Sắp ra mắt". Các quy định dưới đây là quy định của hệ thống và sẽ áp dụng khi có giao diện.

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
