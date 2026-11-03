---
id: de-nghi-day-xu-ly
title: Xem, chấp nhận, từ chối đề nghị dạy của gia sư
type: guide
audience: [STUDENT]
features: [matching]
routes: [/student/studying-requests, /student/studying-requests/:requestId, /student/studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: components/student/StudentTutorOffers.tsx (Chấp nhận/Từ chối khi đề nghị PENDING và tin OPEN; Tạo hợp đồng khi ACCEPTED và tin OPEN/MATCHED)"
  - "frontend: pages/student/StudentTutorOfferDetailPage.tsx (Chi tiết đề nghị từ gia sư)"
  - "backend: StudentTutorOfferController PATCH /{tutorRequestId}/accept, /reject -> TutorStudentRequestService.accept/reject"
error_codes: [STUDYING_REQUEST_ALREADY_MATCHED, INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION, INVALID_STUDYING_REQUEST_STATUS_TRANSITION]
keywords: [đề nghị từ gia sư, chấp nhận gia sư, từ chối gia sư, chọn gia sư, de nghi tu gia su, chap nhan gia su, tu choi gia su, chon gia su]
todo:
  - "Học viên có xem được hồ sơ, chứng chỉ, đánh giá của gia sư trước khi chấp nhận không? Nếu có, ở trang nào?"
---

# Xem, chấp nhận, từ chối đề nghị dạy của gia sư

## Xem đề nghị dạy của gia sư

1. Vào **My Studying Requests**, mở tin tìm gia sư.
2. Kéo xuống mục **Đề nghị từ gia sư**. Mỗi đề nghị ghi giá đề nghị, hình thức dạy, lịch mong muốn và trạng thái.
3. Muốn xem đầy đủ, mở trang **Chi tiết đề nghị từ gia sư**. Trang ghi **Giá đề nghị**, **Hình thức dạy**, **Lịch học mong muốn**, **Lời nhắn** và thời gian gửi.

Bạn nhận thông báo mỗi khi có gia sư gửi đề nghị mới.

## Chấp nhận đề nghị dạy

1. Trong mục **Đề nghị từ gia sư**, tìm đề nghị đang chờ.
2. Bấm **Chấp nhận**.

Nút **Chấp nhận** chỉ hiện khi đề nghị đang chờ và tin của bạn đang mở. Mỗi tin chỉ chấp nhận được một gia sư. Sau khi chấp nhận, tin chuyển sang đã ghép, các gia sư khác không gửi thêm đề nghị được, và gia sư được chọn nhận thông báo.

## Từ chối đề nghị dạy

1. Trong mục **Đề nghị từ gia sư**, tìm đề nghị đang chờ.
2. Bấm **Từ chối**.

Gia sư nhận thông báo đề nghị bị từ chối.

## Bước tiếp theo sau khi chấp nhận

Sau khi chấp nhận, đề nghị đó hiện nút **Tạo hợp đồng**. Bạn bấm nút này để soạn hợp đồng gửi gia sư ký. Xem tài liệu "Cách soạn hợp đồng sau khi chấp nhận đề nghị dạy".
