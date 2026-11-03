---
id: tin-tim-gia-su-quy-dinh
title: "Quy định tin tìm gia sư: chờ duyệt, sửa, tạm đóng, hủy"
type: policy
audience: [STUDENT]
features: [studying-request]
routes: [/student/studying-requests, /student/studying-requests/new, /student/studying-requests/:requestId, /student/studying-requests/:requestId/edit]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: StudyingRequestController (create, update, PATCH status, POST cancel)"
  - "backend: StudyingRequestService.create/update/updateStatus/cancel/activateAfterPayment/requiresReview/validate"
  - "backend: StudyingRequestService.search (gia sư chỉ thấy tin OPEN)"
  - "backend: AdminStudyingRequestService.approve/reject (lý do từ chối tối đa 500 ký tự, hoàn phí)"
  - "backend: TutorStudentRequestService.accept (một tin chỉ chấp nhận một gia sư)"
error_codes: [INVALID_STUDYING_REQUEST, INVALID_PRICE_RANGE, INVALID_STUDYING_REQUEST_STATUS_TRANSITION, STUDYING_REQUEST_ALREADY_MATCHED]
keywords: [tin tìm gia sư, đăng tin, yêu cầu tìm gia sư, môn học khác, tạm đóng tin, hủy tin, tin tim gia su, dang tin, tam dong, huy tin, studying request]
todo:
  - "Tin tìm gia sư đang mở có tự hết hạn sau một thời gian không? (code không có)"
  - "Tiêu chí PTutor dùng để duyệt hoặc từ chối tin có môn học tự nhập là gì?"
---

# Quy định tin tìm gia sư: chờ duyệt, sửa, tạm đóng, hủy

## Tin tìm gia sư gồm những gì

Tin tìm gia sư mô tả nhu cầu học của bạn. Tin bắt buộc có tiêu đề, lớp và hình thức học (trực tuyến hoặc trực tiếp). Bạn chọn môn học trong danh sách, hoặc chọn môn khác và ghi tên môn vào ô ghi chú. Các thông tin khác không bắt buộc: mô tả, địa chỉ, mức giá tối thiểu và tối đa, mục tiêu học, lịch mong muốn và các khung giờ rảnh trong tuần.

Mức giá không được âm, và giá tối thiểu không được lớn hơn giá tối đa. Nếu bỏ trống giá, tin hiện "Có thể thương lượng".

## Đăng tin tìm gia sư và xét duyệt

Tin mới tạo là bản nháp. Bạn trả phí đăng tin để đăng. Sau khi trả phí:

- Tin chọn môn trong danh sách được đăng ngay và chuyển sang đang mở.
- Tin có môn học tự nhập chuyển sang chờ duyệt. Nhân viên chăm sóc khách hàng xem xét, chọn hoặc tạo môn học phù hợp rồi duyệt. Nếu bị từ chối, bạn nhận lý do và được hoàn phí vào ví.

Chỉ tin đang mở mới hiện với gia sư và nhận được đề xuất dạy.

## Sửa tin tìm gia sư

Bạn chỉ sửa được tin đang là bản nháp hoặc đang mở. Tin đang chờ duyệt, đã ghép, tạm đóng, đã hủy hay bị từ chối thì không sửa được. Muốn sửa tin đang tạm đóng, bạn mở lại tin trước.

## Tạm đóng và mở lại tin tìm gia sư

Bạn tạm đóng được tin đang mở khi chưa muốn nhận thêm đề xuất. Tin tạm đóng mở lại được bất cứ lúc nào. Tạm đóng không mất phí và không làm mất các đề xuất đã nhận.

## Hủy tin tìm gia sư

Bạn hủy được tin đang là bản nháp, đang mở, đã ghép hoặc tạm đóng. Khi hủy tin, mọi đề xuất dạy đang chờ của gia sư cho tin đó cũng bị hủy. Tin đã hủy không khôi phục được. Hệ thống không tự hoàn phí đăng tin khi bạn tự hủy.

## Chấp nhận gia sư cho tin

Mỗi tin tìm gia sư chỉ chấp nhận được một gia sư. Khi bạn chấp nhận một đề xuất, tin chuyển sang "đã ghép" và không nhận thêm đề xuất mới. Bước tiếp theo là tạo hợp đồng với gia sư đó.
