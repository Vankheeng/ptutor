---
id: khieu-nai-bi-khieu-nai
title: "Khi bạn bị khiếu nại: xem và phản hồi"
type: policy
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/notifications, /tutor/notifications]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: ComplaintsPage/ComplaintDetailPage/ComplaintResponseForm/ComplaintEvidenceField"
  - "backend: ComplaintController GET /against-me, /against-me/{id}, POST /against-me/{id}/responses"
  - "backend: ComplaintService.findAgainstMe (chỉ thấy khiếu nại IN_REVIEW, AWAITING_EVIDENCE, RESOLVED, REJECTED), respond (một lần, khi IN_REVIEW hoặc AWAITING_EVIDENCE)"
  - "backend: ComplaintParties.respondentOf; dto/request/ComplaintRespondentResponseRequest (tối đa 5000 ký tự, tối đa 5 minh chứng)"
  - "enum: NotificationEventType COMPLAINT_RECEIVED_AGAINST_YOU"
error_codes: [RESPONDENT_RESPONSE_NOT_ALLOWED, RESPONDENT_RESPONSE_ALREADY_SUBMITTED, COMPLAINT_NOT_FOUND]
keywords: [bị khiếu nại, phản hồi khiếu nại, giải trình, bi khieu nai, phan hoi khieu nai, giai trinh]
todo: []
---

# Khi bạn bị khiếu nại: xem và phản hồi

## Ai là bên bị khiếu nại

Bên bị khiếu nại là bên còn lại của hợp đồng: học viên khiếu nại thì gia sư là bên bị khiếu nại, và ngược lại.

## Khi nào bạn biết mình bị khiếu nại

Bạn chỉ thấy khiếu nại về mình khi nhân viên chăm sóc khách hàng đã tiếp nhận xử lý. Lúc đó bạn nhận thông báo "Complaint received about you". Khiếu nại còn "Chờ tiếp nhận" hoặc đã bị người gửi hủy thì bạn không thấy.

## Phản hồi khiếu nại

Bên bị khiếu nại được gửi một phản hồi kèm bằng chứng:

- Chỉ gửi được khi khiếu nại đang "Đang xử lý" hoặc "Chờ bằng chứng".
- Chỉ gửi một lần, không sửa được sau khi gửi.
- Nội dung phản hồi bắt buộc, tối đa 5.000 ký tự.
- Tối đa 5 bằng chứng; chọn tệp để tải lên PTutor.

Bạn nên phản hồi sớm và đầy đủ, vì nhân viên xem cả hai phía trước khi kết luận.

## Sau khi khiếu nại có kết luận

Bạn nhận thông báo kết luận. Nếu khiếu nại được chấp nhận, thông báo ghi bên có lỗi và cách giải quyết. Nếu khiếu nại bị từ chối, thông báo ghi lý do.

## Giao diện phản hồi khiếu nại

Vào **Khiếu nại** → **Khiếu nại nhận được**, mở vụ việc và dùng mục **Phản hồi** khi khiếu nại đang được xử lý.

## Thời hạn phản hồi

Hệ thống hiện không có đồng hồ hạn phản hồi cho bên bị khiếu nại và không tự kết luận bên im lặng là có lỗi. Đề xuất vận hành là phản hồi trong **3 ngày làm việc** sau khi nhận thông báo; nhân viên cân nhắc chứng cứ hiện có nếu không nhận được phản hồi. Hãy trả lời sớm và chỉ gửi thông tin có thể kiểm chứng.
