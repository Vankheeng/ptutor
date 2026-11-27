---
id: khieu-nai-ket-luan
title: Kết luận khiếu nại, bên có lỗi, ảnh hưởng tới buổi học và hoàn tiền
type: policy
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/notifications, /tutor/notifications, /student/contracts/:contractId/lessons]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: AdminComplaintService.startReview, accept (bắt buộc bên có lỗi), reject, complete (nội dung kết luận tối đa 5000 ký tự)"
  - "enum: ComplaintFaultParty (RESPONDENT, COMPLAINANT, BOTH, NONE); frontend pages/admin/complaintFormat.ts faultPartyLabels"
  - "backend: LessonService (BLOCKING_COMPLAINT_STATUSES = PENDING, IN_REVIEW, AWAITING_EVIDENCE)"
  - "backend: AdminRefundRequestService.approve (CONTRACT_HAS_OPEN_COMPLAINT)"
  - "enum: NotificationEventType COMPLAINT_RESOLVED, COMPLAINT_REJECTED, COMPLAINT_AGAINST_YOU_RESOLVED, COMPLAINT_AGAINST_YOU_REJECTED"
error_codes: [LESSON_COMPLETION_ON_HOLD, CONTRACT_HAS_OPEN_COMPLAINT]
keywords: [kết luận khiếu nại, bên có lỗi, khiếu nại được chấp nhận, khiếu nại bị từ chối, xử phạt, ket luan khieu nai, ben co loi, xu phat]
todo: []
---

# Kết luận khiếu nại, bên có lỗi, ảnh hưởng tới buổi học và hoàn tiền

## Ai xử lý khiếu nại

Nhân viên chăm sóc khách hàng của PTutor tiếp nhận và xử lý khiếu nại. Khi nhân viên nhận xử lý, khiếu nại chuyển sang "Đang xử lý". Nhân viên xem nội dung, bằng chứng của người khiếu nại và phản hồi của bên bị khiếu nại, có thể yêu cầu bổ sung bằng chứng.

## Hai cách kết luận khiếu nại

Nhân viên kết luận khiếu nại bằng một trong hai cách, luôn kèm nội dung giải quyết hoặc lý do:

- **Chấp nhận khiếu nại**: khiếu nại chuyển sang "Đã giải quyết". Nhân viên ghi rõ bên có lỗi.
- **Từ chối khiếu nại**: khiếu nại chuyển sang "Đã từ chối", kèm lý do.

Cả người khiếu nại và bên bị khiếu nại đều nhận thông báo kết luận.

## Bên có lỗi

Khi chấp nhận khiếu nại, nhân viên chọn một trong bốn kết luận về bên có lỗi: **Bên bị khiếu nại**, **Bên khiếu nại**, **Cả hai bên** hoặc **Không bên nào**.

Hệ thống ghi nhận bên có lỗi nhưng không tự áp dụng hình phạt, không tự trừ tiền hay khóa tài khoản. Nhân viên có thể xem xét cảnh cáo hoặc khóa tài khoản bằng quy trình quản trị riêng. Kết luận khiếu nại tự nó không phát sinh khoản phạt hay khoản bồi thường.

## Ảnh hưởng tới buổi học

Khi hợp đồng có khiếu nại đang "Chờ tiếp nhận", "Đang xử lý" hoặc "Chờ bằng chứng", mọi buổi học chờ xác nhận của hợp đồng bị giữ lại: học viên không xác nhận được và buổi không tự hoàn tất sau 3 ngày. Khi khiếu nại có kết luận hoặc bị hủy, buổi học được xử lý bình thường trở lại.

## Ảnh hưởng tới hoàn tiền

Khi hợp đồng có khiếu nại chưa có kết luận, kế toán chưa kết luận được yêu cầu hoàn tiền của hợp đồng đó. Kết luận khiếu nại không tự hoàn tiền; muốn được hoàn học phí, học viên vẫn cần gửi yêu cầu hoàn tiền.

## Cách nhân viên xem xét chứng cứ

Đề xuất nhân viên đối chiếu điều khoản hợp đồng, lịch và trạng thái buổi học, giao dịch thanh toán, tài liệu có thể kiểm chứng và phản hồi của cả hai bên. Không phản hồi không mặc nhiên là nhận lỗi. Nếu chứng cứ chưa đủ, nhân viên yêu cầu bổ sung hoặc ghi rõ vì sao từ chối. Các tiêu chí này là quy định vận hành đề xuất; hệ thống chưa tự chấm điểm chứng cứ.
