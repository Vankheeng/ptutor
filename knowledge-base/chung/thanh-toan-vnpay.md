---
id: thanh-toan-vnpay
title: Thanh toán qua VNPay
type: policy
audience: [STUDENT, TUTOR]
features: [payment]
routes: [/student/wallet, /tutor/wallet, /student/contracts/:contractId, /student/studying-requests]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: VnPayService (app.payment.vnpay.expiry-minutes mặc định 15; time-zone Asia/Ho_Chi_Minh)"
  - "backend: PaymentService.createVnPayPayment (dùng lại link còn hạn; quá hạn -> đánh dấu FAILED/EXPIRED và tạo mới), processIpn, inspectReturn, activatePaidPayment"
  - "backend: PaymentController GET /payments/vnpay/ipn, /payments/vnpay/return"
  - "config: VNPAY_EXPIRY_MINUTES"
  - "enum: PaymentType (WALLET_TOP_UP, TUITION_PAYMENT, STUDYING_REQUEST_FEE, TEACHING_REQUEST_FEE); NotificationEventType PAYMENT_SUCCEEDED"
error_codes: [INVALID_VNPAY_SIGNATURE, INVALID_VNPAY_AMOUNT, PAYMENT_NOT_FOUND]
keywords: [vnpay, thanh toán online, thanh toán thất bại, bị trừ tiền nhưng chưa cập nhật, thanh toan vnpay, thanh toan that bai, tru tien chua cap nhat]
todo:
  - "Bị trừ tiền ở ngân hàng nhưng PTutor chưa ghi nhận thanh toán thì người dùng liên hệ ai, cần cung cấp thông tin gì, xử lý trong bao lâu?"
  - "PTutor có thu phí giao dịch VNPay của người dùng không?"
---

# Thanh toán qua VNPay

## Những khoản thanh toán qua VNPay

Trên PTutor, bạn thanh toán qua VNPay cho bốn loại khoản: nạp tiền vào ví, đóng kỳ học phí, phí đăng tin tìm gia sư và phí đăng tin nhận dạy. Khi bấm nút thanh toán, PTutor chuyển bạn sang trang của VNPay để trả bằng thẻ hoặc ứng dụng ngân hàng.

## Link VNPay có hiệu lực 15 phút

Mỗi link thanh toán VNPay có hiệu lực 15 phút kể từ khi tạo. Nếu bạn bấm thanh toán lại cho cùng khoản trong 15 phút, PTutor mở lại link cũ. Quá 15 phút, link cũ được ghi là thất bại và PTutor tạo link mới.

## Khi nào thanh toán được ghi nhận

PTutor chỉ ghi nhận thanh toán khi VNPay xác nhận giao dịch thành công và đúng số tiền. Khi đó:

- Nạp ví: số dư ví tăng đúng số tiền nạp.
- Kỳ học phí: kỳ chuyển sang "Đã thanh toán".
- Phí đăng tin: tin được đăng hoặc chuyển sang chờ duyệt.

Bạn nhận thông báo "Payment successful". Mỗi giao dịch chỉ được ghi nhận một lần, kể cả khi VNPay gửi xác nhận nhiều lần.

## Khi thanh toán không thành công

Khi bạn hủy giao dịch trên VNPay, nhập sai thông tin, hoặc ngân hàng từ chối, thanh toán được ghi là thất bại. Khoản cần trả (kỳ học phí, tin đăng) giữ nguyên trạng thái chờ. Bạn bấm thanh toán lại để tạo giao dịch mới.
