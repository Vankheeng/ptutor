---
id: trang-thai-tin-dang
title: Giải nghĩa trạng thái tin đăng
type: glossary
audience: [STUDENT, TUTOR]
features: [studying-request, teaching-request]
routes: [/student/studying-requests, /student/studying-requests/:requestId, /tutor/sections/:section]
status: draft
version: 1
updated: 2026-11-03
source:
  - "enum: RequestStatus (DRAFT, PENDING_REVIEW, OPEN, MATCHED, CLOSED, CANCELLED, REJECTED)"
  - "backend: StudyingRequestService (update/updateStatus/cancel/activateAfterPayment); TeachingRequestService"
  - "backend: TutorStudentRequestService.accept (tin tìm gia sư -> MATCHED); StudentTutorRequestService.updateStatus (tin nhận dạy -> MATCHED khi đủ số học viên)"
  - "frontend: components/student/studyingRequestUtils.ts statusLabel (hiển thị mã tiếng Anh viết thường); pages/admin/requestReviewFormat.ts (Chờ duyệt)"
error_codes: [INVALID_STUDYING_REQUEST_STATUS_TRANSITION, INVALID_TEACHING_REQUEST_STATUS_TRANSITION]
keywords: [trạng thái tin, tin đăng, chờ duyệt, bản nháp, đang mở, tạm đóng, đã ghép, bị từ chối, trang thai tin dang, cho duyet, ban nhap, dang mo, tam dong, draft, pending review, open, matched, closed]
todo:
  - "Trang của học viên hiện hiển thị trạng thái tin bằng chữ tiếng Anh viết thường (draft, pending review, open, matched, closed, cancelled, rejected). Admin xác nhận các nhãn tiếng Việt dùng trong tài liệu: Bản nháp, Chờ duyệt, Đang mở, Đã ghép, Tạm đóng, Đã hủy, Bị từ chối."
  - "Tin chờ duyệt được xét trong bao lâu?"
---

# Giải nghĩa trạng thái tin đăng

Trạng thái dưới đây áp dụng cho cả tin tìm gia sư của học viên và tin nhận dạy của gia sư. Trên trang của học viên, trạng thái tin hiện bằng chữ tiếng Anh viết thường, ghi trong ngoặc ở từng mục.

## Bản nháp (draft)

Tin "Bản nháp" đã được tạo nhưng chưa trả phí đăng tin, nên chưa ai thấy. Bước tiếp theo: bạn trả phí để đăng tin, hoặc sửa thông tin trước khi trả phí. Bạn cũng có thể hủy bản nháp.

## Chờ duyệt (pending review)

Tin "Chờ duyệt" đã trả phí và đang chờ nhân viên chăm sóc khách hàng xét duyệt. Tin tìm gia sư rơi vào trạng thái này khi bạn tự nhập môn học. Tin nhận dạy rơi vào trạng thái này khi có môn học tự nhập hoặc có ghi chú. Bước tiếp theo: nhân viên chăm sóc khách hàng duyệt hoặc từ chối, bạn chỉ cần chờ thông báo. Gia sư vẫn sửa hoặc hủy được tin nhận dạy đang chờ duyệt.

## Đang mở (open)

Tin "Đang mở" đã được đăng. Với tin tìm gia sư, gia sư thấy tin và gửi đề xuất dạy. Với tin nhận dạy, học viên thấy tin và gửi đơn đăng ký học. Bạn có thể sửa, tạm đóng hoặc hủy tin.

## Đã ghép (matched)

Tin tìm gia sư "Đã ghép" khi học viên đã chấp nhận một đề xuất của gia sư. Tin nhận dạy "Đã ghép" khi gia sư đã chấp nhận đủ số học viên đăng tin. Bước tiếp theo: tạo và ký hợp đồng. Tin đã ghép không nhận thêm đề xuất hay đơn đăng ký.

## Tạm đóng (closed)

Tin "Tạm đóng" do chính người đăng đóng lại. Tin không nhận đề xuất hay đơn đăng ký mới. Bạn mở lại được bất cứ lúc nào, tin trở về "Đang mở".

## Đã hủy (cancelled)

Tin "Đã hủy" do người đăng hủy. Tin không dùng lại được. Khi học viên hủy tin tìm gia sư, các đề xuất đang chờ của gia sư cho tin đó cũng bị hủy theo.

## Bị từ chối (rejected)

Tin "Bị từ chối" không được nhân viên chăm sóc khách hàng duyệt. Bạn nhận thông báo ghi lý do, và phí đăng tin được hoàn vào số dư ví. Muốn đăng lại, bạn tạo tin mới.
