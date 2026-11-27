---
id: khieu-nai-bang-chung
title: Bổ sung bằng chứng cho khiếu nại
type: policy
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/notifications, /tutor/notifications]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: ComplaintEvidenceField; backend: ComplaintController /evidence-upload"
  - "backend: AdminComplaintService.requestEvidence (chuyển AWAITING_EVIDENCE; lời yêu cầu tối đa 5000 ký tự)"
  - "backend: ComplaintController POST /{complaintId}/evidences -> ComplaintService.submitEvidence (chỉ khi AWAITING_EVIDENCE; quay lại IN_REVIEW)"
  - "backend: dto/request/ComplaintEvidenceSubmissionRequest (1–5 minh chứng mỗi lần)"
  - "enum: NotificationEventType COMPLAINT_EVIDENCE_REQUESTED"
error_codes: [INVALID_COMPLAINT_STATUS_TRANSITION]
keywords: [bổ sung bằng chứng, minh chứng khiếu nại, chờ bằng chứng, bo sung bang chung, minh chung, cho bang chung, evidence]
todo: []
---

# Bổ sung bằng chứng cho khiếu nại

## Khi nào phải bổ sung bằng chứng

Trong lúc xử lý khiếu nại, nhân viên chăm sóc khách hàng có thể yêu cầu người gửi khiếu nại bổ sung bằng chứng. Khi đó, khiếu nại chuyển sang "Chờ bằng chứng" và bạn nhận thông báo "Additional evidence required" kèm nội dung cần bổ sung.

## Cách bổ sung bằng chứng

Khi khiếu nại ở trạng thái "Chờ bằng chứng", người gửi khiếu nại gửi thêm bằng chứng:

- Mỗi lần gửi từ 1 đến 5 bằng chứng.
- Mỗi bằng chứng là tệp ảnh, video hoặc tài liệu tải trực tiếp lên PTutor.

Sau khi gửi, khiếu nại tự quay lại "Đang xử lý" để nhân viên xem tiếp.

## Khi không gửi được bằng chứng

Chỉ bổ sung được bằng chứng khi khiếu nại đang "Chờ bằng chứng". Ở các trạng thái khác, hệ thống từ chối. Bên bị khiếu nại không dùng cách này; bên bị khiếu nại gửi bằng chứng kèm phản hồi của mình.

## Giao diện bổ sung bằng chứng

Vào **Khiếu nại** → mở vụ việc ở trạng thái **Chờ bằng chứng** → dùng mục bổ sung minh chứng để tải tệp rồi gửi.

## Tải minh chứng trực tiếp

Trong biểu mẫu tạo khiếu nại hoặc phần bổ sung minh chứng, chọn tệp trên thiết bị để tải trực tiếp lên PTutor. Không cần đưa ảnh hoặc video lên một dịch vụ bên ngoài để lấy đường dẫn.

## Thời hạn bổ sung

Mã nguồn hiện không tự đặt hạn bổ sung minh chứng hay tự từ chối khiếu nại vì quá hạn. Đề xuất vận hành là **3 ngày làm việc** từ khi nhân viên yêu cầu, có nhắc trong thông báo; nếu cần thêm thời gian, người gửi nên phản hồi qua **Yêu cầu hỗ trợ**. Cho tới khi quy trình này được áp dụng, hãy xem nội dung yêu cầu bổ sung và gửi sớm nhất có thể.
