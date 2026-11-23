---
id: trang-thai-hoan-tien
title: Giải nghĩa trạng thái yêu cầu hoàn tiền
type: glossary
audience: [STUDENT, TUTOR]
features: [refund]
routes: [/student/refund-requests, /student/refund-requests/:refundRequestId, /tutor/refund-requests, /tutor/refund-requests/:refundRequestId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: RefundRequestStatus (PENDING, IN_REVIEW, REFUNDED, REJECTED, CANCELLED); LessonOutcome (TAUGHT, TUTOR_ABSENT, STUDENT_ABSENT)"
  - "frontend: components/refund/refundFormat.ts (Chờ tiếp nhận, Đang xử lý, Đã hoàn tiền, Đã từ chối, Đã huỷ; Đã dạy, Gia sư không dạy, Học viên nghỉ)"
  - "backend: RefundRequestService.create/cancel; AdminRefundRequestService.startReview/approve/reject"
error_codes: [INVALID_REFUND_REQUEST_STATUS_TRANSITION]
keywords: [trạng thái hoàn tiền, chờ tiếp nhận, đang xử lý, đã hoàn tiền, đã từ chối, trang thai hoan tien, cho tiep nhan, dang xu ly, da hoan tien]
todo:
  - "Kế toán tiếp nhận và kết luận yêu cầu hoàn tiền trong bao lâu?"
---

# Giải nghĩa trạng thái yêu cầu hoàn tiền

## Chờ tiếp nhận

Yêu cầu hoàn tiền "Chờ tiếp nhận" vừa được học viên gửi, chưa có kế toán nhận xử lý. Bước tiếp theo: gia sư phản hồi trong 48 giờ; kế toán tiếp nhận. Học viên còn hủy được yêu cầu ở trạng thái này.

## Đang xử lý

Yêu cầu hoàn tiền "Đang xử lý" đã được một kế toán PTutor tiếp nhận. Học viên không hủy được nữa. Gia sư vẫn phản hồi được nếu còn trong 48 giờ và chưa phản hồi. Bước tiếp theo: kế toán kết luận sau khi gia sư phản hồi hoặc hết hạn phản hồi, và khi hợp đồng không còn khiếu nại chưa xong.

## Đã hoàn tiền

Yêu cầu hoàn tiền "Đã hoàn tiền" đã được kế toán kết luận và có ít nhất một khoản được hoàn. Tiền hoàn đã cộng vào ví học viên ("Đã cộng vào ví học viên"). Nếu có buổi học viên nghỉ, phần của gia sư đã cộng vào ví gia sư ("Đã cộng vào ví gia sư"). Không ai cần làm gì thêm.

## Đã từ chối

Yêu cầu hoàn tiền "Đã từ chối" không được hoàn tiền. Kế toán ghi lý do, hai bên nhận thông báo. Các buổi trong yêu cầu được xử lý bình thường trở lại.

## Đã huỷ

Yêu cầu hoàn tiền "Đã huỷ" do học viên hủy khi còn chờ tiếp nhận. Các buổi được xử lý bình thường trở lại.

## Kết luận từng buổi học

Mỗi buổi bị phản đối có một kết luận: "Đã dạy" (buổi chuyển sang đã học, không hoàn), "Gia sư không dạy" (hoàn 100% đơn giá), hoặc "Học viên nghỉ" (hoàn 80% đơn giá, gia sư nhận 20%).
