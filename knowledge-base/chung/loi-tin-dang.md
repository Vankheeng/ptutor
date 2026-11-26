---
id: loi-tin-dang
title: Lỗi thường gặp khi đăng, sửa, đóng tin
type: error
audience: [STUDENT, TUTOR]
features: [studying-request, teaching-request]
routes: [/student/studying-requests, /student/studying-requests/new, /student/studying-requests/:requestId/edit]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: StudyingRequestService (validate, update, updateStatus, cancel)"
  - "backend: TeachingRequestService (validate, update, updateStatus, cancel)"
  - "backend: PaymentService.createStudyingRequestPayment, payStudyingRequestWithWallet"
  - "frontend: pages/student/StudentStudyingRequestForm.tsx (kiểm tra trên trang), StudentStudyingRequestsPage.tsx (hiển thị câu của máy chủ)"
error_codes: [INVALID_STUDYING_REQUEST_STATUS_TRANSITION, INVALID_TEACHING_REQUEST_STATUS_TRANSITION, INVALID_STUDYING_REQUEST, INVALID_PRICE_RANGE, INVALID_AVAILABILITY, EMPTY_UPDATE_REQUEST, INVALID_STUDYING_REQUEST_PAYMENT_STATUS, SUBJECT_SOURCE_REQUIRED, INSUFFICIENT_WALLET_BALANCE]
keywords: [lỗi đăng tin, không sửa được tin, không đóng được tin, không hủy được tin, loi dang tin, khong sua duoc tin, khong huy duoc tin]
todo: []
---

# Lỗi thường gặp khi đăng, sửa, đóng tin

## "Only DRAFT or OPEN requests can be updated"

Câu này nghĩa là chỉ sửa được tin tìm gia sư đang là bản nháp hoặc đang mở. Lỗi xảy ra khi tin đang chờ duyệt, đã ghép, đã tạm đóng, đã hủy hoặc bị từ chối. Cách khắc phục: nếu tin đang tạm đóng, mở lại tin rồi sửa. Các trạng thái còn lại không sửa được; hãy tạo tin mới nếu cần.

## "Only OPEN and CLOSED status transitions are allowed"

Câu này nghĩa là chỉ tạm đóng được tin đang mở, và chỉ mở lại được tin đang tạm đóng. Cách khắc phục: kiểm tra trạng thái tin. Tin bản nháp cần trả phí trước khi mở.

## "Only DRAFT, OPEN, MATCHED or CLOSED requests can be cancelled"

Câu này nghĩa là tin tìm gia sư không hủy được ở trạng thái hiện tại. Tin đang chờ duyệt, đã hủy hoặc bị từ chối thì không hủy được nữa.

## "Only DRAFT studying requests can be paid"

Câu này nghĩa là chỉ trả phí được cho tin đang là bản nháp. Lỗi xảy ra khi tin đã được trả phí trước đó. Cách khắc phục: tải lại trang để xem trạng thái mới của tin.

## "Minimum price must not exceed maximum price"

Câu này nghĩa là mức giá tối thiểu lớn hơn mức giá tối đa. Cách khắc phục: nhập **Minimum price (VND)** nhỏ hơn hoặc bằng **Maximum price (VND)**, cả hai không âm.

## "Enter the custom subject in the Note field"

Câu này nghĩa là bạn chọn môn học tự nhập nhưng chưa ghi tên môn. Cách khắc phục: ghi tên môn vào ô **Custom subject / note**.

## "Each availability slot needs a weekday, start time, and an end time after the start time"

Câu này nghĩa là một khung giờ rảnh chưa đủ thông tin hoặc giờ kết thúc không sau giờ bắt đầu. Cách khắc phục: chọn đủ thứ trong tuần, giờ bắt đầu, giờ kết thúc; hoặc xóa khung giờ đó.

## "Wallet balance is insufficient"

Câu này nghĩa là số dư ví không đủ để trả phí bằng ví. Cách khắc phục: nạp thêm tiền vào ví, hoặc trả phí qua VNPay.
