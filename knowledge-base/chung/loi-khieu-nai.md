---
id: loi-khieu-nai
title: Lỗi thường gặp với khiếu nại
type: error
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/notifications, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: ComplaintService (create, update, cancel, submitEvidence, respond, ensurePending)"
  - "backend: dto/request/ComplaintCreateRequest, ComplaintEvidenceSubmissionRequest, ComplaintRespondentResponseRequest"
  - "frontend: chưa có trang khiếu nại cho người dùng; câu báo lỗi là câu tiếng Anh của máy chủ"
error_codes: [CONTRACT_NOT_FOUND, INVALID_COMPLAINT_STATUS_TRANSITION, RESPONDENT_RESPONSE_NOT_ALLOWED, RESPONDENT_RESPONSE_ALREADY_SUBMITTED, COMPLAINT_NOT_FOUND]
keywords: [lỗi khiếu nại, không gửi được khiếu nại, không sửa được khiếu nại, loi khieu nai, khong gui duoc khieu nai]
todo:
  - "Khi có giao diện khiếu nại, cập nhật các câu báo lỗi theo câu hiển thị thật."
---

# Lỗi thường gặp với khiếu nại

## "Contract not found…"

Câu này nghĩa là hợp đồng không tồn tại hoặc bạn không phải học viên hay gia sư của hợp đồng đó. Cách khắc phục: chọn đúng hợp đồng của mình.

## "Only PENDING complaints can be updated" hoặc "… cancelled"

Câu này nghĩa là khiếu nại đã được nhân viên tiếp nhận nên không sửa hay hủy được. Cách khắc phục: chờ nhân viên xử lý; nếu cần bổ sung thông tin, chờ yêu cầu bổ sung bằng chứng.

## "Evidence can only be submitted for an … complaint"

Câu báo lỗi tiếng Anh bắt đầu bằng "Evidence can only be submitted for an" nghĩa là chỉ bổ sung bằng chứng được khi khiếu nại đang "Chờ bằng chứng". Cách khắc phục: chờ nhân viên yêu cầu bổ sung.

## "At least one evidence is required" / "At most 5 evidences can be submitted at once"

Câu này nghĩa là mỗi lần bổ sung phải có từ 1 đến 5 bằng chứng. Cách khắc phục: gửi lại với số bằng chứng trong khoảng này; nếu nhiều hơn 5, chia thành nhiều lần khi được yêu cầu.

## "A response can only be submitted while the complaint is under review"

Câu này hiện với bên bị khiếu nại, nghĩa là khiếu nại đã có kết luận nên không phản hồi được nữa.

## "A response has already been submitted for this complaint"

Câu này nghĩa là bạn đã gửi phản hồi rồi. Mỗi bên bị khiếu nại chỉ phản hồi một lần.

## "Title must not exceed 255 characters" / "Content must not exceed 10000 characters"

Câu này nghĩa là tiêu đề hoặc nội dung quá dài. Cách khắc phục: rút gọn tiêu đề dưới 255 ký tự, nội dung dưới 10.000 ký tự.
