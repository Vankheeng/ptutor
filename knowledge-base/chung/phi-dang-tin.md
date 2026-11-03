---
id: phi-dang-tin
title: Phí đăng tin và hoàn phí khi tin bị từ chối
type: policy
audience: [STUDENT, TUTOR]
features: [studying-request, teaching-request]
routes: [/student/studying-requests, /student/studying-requests/new, /student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: PaymentController POST .../studying-requests/{id}/payments/vnpay|wallet, .../teaching-requests/{id}/payments/vnpay|wallet"
  - "backend: PaymentService.createStudyingRequestPayment, payStudyingRequestWithWallet, createTeachingRequestPayment, payTeachingRequestWithWallet, activatePaidPayment"
  - "config: app.payment.fees.studying-request (PAYMENT_STUDYING_REQUEST_FEE), app.payment.fees.teaching-request (PAYMENT_TEACHING_REQUEST_FEE) — không có giá trị mặc định"
  - "backend: AdminStudyingRequestService.reject, AdminTeachingRequestService.reject (hoàn phí vào ví)"
  - "enum: PaymentType STUDYING_REQUEST_FEE, TEACHING_REQUEST_FEE; WalletTransactionPurpose STUDYING_REQUEST_REFUND, TEACHING_REQUEST_REFUND"
error_codes: [INVALID_STUDYING_REQUEST_PAYMENT_STATUS, INVALID_TEACHING_REQUEST_PAYMENT_STATUS, INSUFFICIENT_WALLET_BALANCE]
keywords: [phí đăng tin, phí đăng bài, hoàn phí, tin bị từ chối, phi dang tin, hoan phi, bao nhieu tien dang tin, posting fee]
todo:
  - "Mức phí đăng tin tìm gia sư và phí đăng tin nhận dạy hiện là bao nhiêu? (cấu hình không có giá trị mặc định)"
  - "Khi người dùng tự hủy tin đã trả phí thì có được hoàn phí không? (code không hoàn)"
  - "Khi tin hết hạn hoặc không có ai ứng tuyển thì có được hoàn phí không?"
---

# Phí đăng tin và hoàn phí khi tin bị từ chối

## Phí đăng tin là gì

Học viên muốn đăng tin tìm gia sư và gia sư muốn đăng tin nhận dạy đều phải trả phí đăng tin. Tin mới tạo chỉ là bản nháp, chưa ai thấy. Tin chỉ được đăng sau khi bạn trả phí.

Mức phí theo mức hiển thị khi bạn thanh toán, tức số tiền trên trang thanh toán VNPay. Mỗi tin chỉ trả phí một lần.

## Cách trả phí đăng tin

Bạn trả phí đăng tin bằng VNPay. Học viên bấm **Create & pay** khi tạo tin, hoặc **Pay to publish** ở tin đang là bản nháp. Link thanh toán VNPay có hiệu lực 15 phút.

Hệ thống còn cho phép trả phí đăng tin bằng số dư ví, nhưng hiện giao diện chưa có nút này.

Chỉ tin đang là bản nháp mới trả phí được. Tin đã trả phí rồi thì không bị thu thêm lần nữa.

## Sau khi trả phí đăng tin

Khi thanh toán thành công:

- Tin bình thường được đăng ngay, chuyển sang trạng thái đang mở.
- Tin cần xét duyệt chuyển sang trạng thái chờ duyệt. Với tin tìm gia sư, đó là tin có môn học tự nhập (không chọn trong danh sách môn). Với tin nhận dạy, đó là tin có môn học tự nhập hoặc có ghi chú.

## Hoàn phí khi tin bị từ chối

Khi nhân viên chăm sóc khách hàng từ chối một tin đang chờ duyệt, phí đăng tin được hoàn đủ vào số dư ví của bạn. Bạn nhận thông báo ghi lý do từ chối và số tiền đã hoàn. Khoản hoàn hiện trong lịch sử giao dịch của trang ví.

Tiền hoàn nằm trong ví PTutor, không tự chuyển về tài khoản ngân hàng hay thẻ đã thanh toán.

## Khi tự hủy tin đã trả phí

Hệ thống không tự hoàn phí khi bạn tự hủy tin đã trả phí. Trường hợp này do PTutor quy định riêng.
