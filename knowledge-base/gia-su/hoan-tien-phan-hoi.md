---
id: hoan-tien-phan-hoi
title: Phản hồi yêu cầu hoàn tiền trong 48 giờ
type: guide
audience: [TUTOR]
features: [refund]
routes: [/tutor/refund-requests, /tutor/refund-requests/:refundRequestId, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/tutor/TutorRefundRequestsPage.tsx, TutorRefundRequestDetailPage.tsx (Phản hồi của bạn: Tôi đã dạy, Tôi không dạy, Học viên nghỉ; Gửi phản hồi)"
  - "backend: TutorRefundRequestController GET, POST /{refundRequestId}/response -> RefundRequestService.respond (một lần; trước hạn; lời nhắn tối đa 2000 ký tự; tối đa 5 minh chứng; đủ ý kiến cho từng buổi)"
  - "backend: RefundPolicy.tutorResponseWindow (app.refund.tutor-response-hours mặc định 48)"
  - "enum: NotificationEventType REFUND_REQUEST_SUBMITTED"
error_codes: [TUTOR_RESPONSE_ALREADY_SUBMITTED, TUTOR_RESPONSE_NOT_ALLOWED, LESSON_POSITION_REQUIRED]
keywords: [phản hồi hoàn tiền, học viên đòi hoàn tiền, yêu cầu hoàn tiền gia sư, 48 giờ, phan hoi hoan tien, hoc vien doi hoan tien]
todo:
  - "Nếu gia sư không phản hồi trong 48 giờ thì kế toán kết luận theo nguyên tắc nào?"
---

# Phản hồi yêu cầu hoàn tiền trong 48 giờ

## Khi học viên gửi yêu cầu hoàn tiền

Khi học viên cho rằng một số buổi bạn đã đánh dấu đã dạy không diễn ra, học viên gửi yêu cầu hoàn tiền. Bạn nhận thông báo "Student requested a refund" kèm hạn phản hồi. Bạn có 48 giờ kể từ lúc học viên gửi yêu cầu để phản hồi.

## Các bước phản hồi yêu cầu hoàn tiền

1. Trong menu, chọn **Yêu cầu hoàn tiền**.
2. Mở yêu cầu cần phản hồi. Trang ghi các buổi bị phản đối, nguyên nhân học viên chọn và hạn phản hồi.
3. Ở mục **Phản hồi của bạn**, chọn ý kiến cho từng buổi: **Tôi đã dạy**, **Tôi không dạy** hoặc **Học viên nghỉ**.
4. Viết lời nhắn giải thích.
5. Nếu có, thêm đường dẫn minh chứng (tối đa 5).
6. Bấm **Gửi phản hồi**.

Trang báo "Đã gửi phản hồi." và kế toán đang xử lý yêu cầu nhận thông báo.

## Quy định phản hồi yêu cầu hoàn tiền

- Bạn chỉ phản hồi được một lần cho mỗi yêu cầu, không sửa được sau khi gửi.
- Bạn phải cho ý kiến về từng buổi bị phản đối.
- Lời nhắn bắt buộc, tối đa 2.000 ký tự.
- Quá 48 giờ, hoặc khi yêu cầu đã có kết luận hay bị hủy, bạn không phản hồi được nữa.

## Sau khi phản hồi

Kế toán PTutor xem xét ý kiến hai bên và kết luận từng buổi. Buổi kết luận "đã dạy" chuyển sang "Đã học". Buổi kết luận học viên nghỉ: bạn nhận 20% đơn giá buổi đó vào ví. Buổi kết luận gia sư vắng: học viên được hoàn 100% đơn giá.
