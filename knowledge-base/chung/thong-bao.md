---
id: thong-bao
title: Xem thông báo và đánh dấu đã đọc
type: guide
audience: [STUDENT, TUTOR]
features: [notification]
routes: [/student/notifications, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/common-user/NotificationsPage.tsx (Notifications, Mark all as read), components/NotificationFilters.tsx (All, unread, read; số chưa đọc), components/NotificationList.tsx (bấm để đánh dấu đã đọc)"
  - "backend: NotificationController GET, PATCH /{id}/read, PATCH /read-all, GET /unread-count"
  - "backend: NotificationTemplateFactory (tiêu đề và nội dung tiếng Anh); NotificationCampaignScheduler (thông báo chung do PTutor gửi)"
  - "enum: NotificationType (PAYMENT, REQUEST, CONTRACT, SYSTEM, COMPLAINT)"
error_codes: []
keywords: [thông báo, đánh dấu đã đọc, thông báo chưa đọc, không nhận được thông báo, thong bao, danh dau da doc, chua doc, notifications]
todo:
  - "PTutor có gửi thông báo qua email hoặc điện thoại không, hay chỉ trong ứng dụng? (code chỉ gửi email cho mã OTP)"
---

# Xem thông báo và đánh dấu đã đọc

## Xem thông báo

1. Mở trang thông báo: học viên chọn **Notifications**, gia sư chọn **Thông báo** trong menu.
2. Trang **Notifications** liệt kê thông báo mới nhất trước. Thông báo chưa đọc có viền màu nổi bật.
3. Dùng các thẻ **All**, **unread**, **read** để lọc tất cả, chưa đọc hoặc đã đọc. Cạnh thẻ lọc có số thông báo chưa đọc.

## Đánh dấu thông báo đã đọc

- Bấm vào một thông báo chưa đọc để đánh dấu đã đọc.
- Bấm **Mark all as read** để đánh dấu tất cả là đã đọc.

## Các loại thông báo bạn nhận

PTutor gửi thông báo trong ứng dụng khi có việc liên quan tới bạn: đề xuất và đơn đăng ký học, hợp đồng cần ký hoặc đã ký, thanh toán thành công, nhắc đóng học phí trước hạn 3 ngày, buổi học chờ xác nhận, yêu cầu hoàn tiền, khiếu nại, kết quả duyệt tin, hồ sơ và chứng chỉ, tài khoản bị khóa hoặc mở lại. PTutor cũng có thể gửi thông báo chung cho mọi người dùng.

## Thông báo đang hiển thị bằng tiếng Anh

Tiêu đề và nội dung thông báo hiện bằng tiếng Anh. Một số tiêu đề thường gặp:

- "Payment successful": thanh toán thành công.
- "Tuition payment reminder": nhắc đóng học phí.
- "Contract signature required": có hợp đồng chờ bạn ký.
- "Lesson awaiting confirmation": buổi học chờ bạn xác nhận.
- "Student requested a refund": học viên yêu cầu hoàn tiền.
- "Additional evidence required": cần bổ sung bằng chứng cho khiếu nại.
