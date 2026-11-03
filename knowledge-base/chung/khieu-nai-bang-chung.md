---
id: khieu-nai-bang-chung
title: Bổ sung bằng chứng cho khiếu nại
type: policy
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/notifications, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: AdminComplaintService.requestEvidence (chuyển AWAITING_EVIDENCE; lời yêu cầu tối đa 5000 ký tự)"
  - "backend: ComplaintController POST /{complaintId}/evidences -> ComplaintService.submitEvidence (chỉ khi AWAITING_EVIDENCE; quay lại IN_REVIEW)"
  - "backend: dto/request/ComplaintEvidenceSubmissionRequest (1–5 minh chứng mỗi lần)"
  - "enum: NotificationEventType COMPLAINT_EVIDENCE_REQUESTED"
error_codes: [INVALID_COMPLAINT_STATUS_TRANSITION]
keywords: [bổ sung bằng chứng, minh chứng khiếu nại, chờ bằng chứng, bo sung bang chung, minh chung, cho bang chung, evidence]
todo:
  - "Người khiếu nại có thời hạn bao lâu để bổ sung bằng chứng? Quá hạn thì khiếu nại xử lý thế nào? (code không có thời hạn)"
  - "Người dùng tải ảnh, video lên đâu để có đường dẫn làm bằng chứng?"
---

# Bổ sung bằng chứng cho khiếu nại

## Khi nào phải bổ sung bằng chứng

Trong lúc xử lý khiếu nại, nhân viên chăm sóc khách hàng có thể yêu cầu người gửi khiếu nại bổ sung bằng chứng. Khi đó, khiếu nại chuyển sang "Chờ bằng chứng" và bạn nhận thông báo "Additional evidence required" kèm nội dung cần bổ sung.

## Cách bổ sung bằng chứng

Khi khiếu nại ở trạng thái "Chờ bằng chứng", người gửi khiếu nại gửi thêm bằng chứng:

- Mỗi lần gửi từ 1 đến 5 bằng chứng.
- Mỗi bằng chứng là một đường dẫn tới ảnh, video hoặc tài liệu.

Sau khi gửi, khiếu nại tự quay lại "Đang xử lý" để nhân viên xem tiếp.

## Khi không gửi được bằng chứng

Chỉ bổ sung được bằng chứng khi khiếu nại đang "Chờ bằng chứng". Ở các trạng thái khác, hệ thống từ chối. Bên bị khiếu nại không dùng cách này; bên bị khiếu nại gửi bằng chứng kèm phản hồi của mình.

## Giao diện bổ sung bằng chứng

Hiện chưa có giao diện khiếu nại cho học viên và gia sư. Quy định trên sẽ áp dụng khi có giao diện.
