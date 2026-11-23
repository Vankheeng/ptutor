---
id: loi-vi-thanh-toan
title: Lỗi thường gặp khi nạp ví và thanh toán
type: error
audience: [STUDENT, TUTOR]
features: [wallet, payment]
routes: [/student/wallet, /tutor/wallet, /student/contracts/:contractId, /student/studying-requests]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: PaymentService (requireWholeVnd, top-up range, createTuitionPayment, payTuitionWithWallet, createStudyingRequestPayment)"
  - "backend: WalletService.debit (INSUFFICIENT_WALLET_BALANCE), validateAmount"
  - "frontend: pages/common-user/WalletPage.tsx (Could not create the payment.), StudentContractDetailPage.tsx (Không thể tạo thanh toán.), StudentStudyingRequestCreatePage.tsx (Payment could not be started.)"
error_codes: [INVALID_WALLET_TOP_UP_AMOUNT, INSUFFICIENT_WALLET_BALANCE, CONTRACT_NOT_ACTIVE, PAYMENT_INSTALLMENT_NOT_PAYABLE, PAYMENT_INSTALLMENT_NOT_READY, INVALID_STUDYING_REQUEST_PAYMENT_STATUS, USER_INACTIVE]
keywords: [lỗi nạp tiền, lỗi thanh toán, không thanh toán được, số dư không đủ, loi nap tien, loi thanh toan, khong thanh toan duoc, so du khong du]
todo: []
---

# Lỗi thường gặp khi nạp ví và thanh toán

## "Top-up amount must be between 10000 and 100000000 VND"

Câu này nghĩa là số tiền nạp ngoài khoảng cho phép. Mỗi lần nạp phải từ 10.000đ đến 100.000.000đ. Cách khắc phục: nhập lại số tiền trong khoảng này.

## "Top-up amount must be a whole VND amount"

Câu này nghĩa là số tiền nạp có phần lẻ. Cách khắc phục: nhập số chẵn đồng, ví dụ 150000.

## "Wallet balance is insufficient"

Câu này nghĩa là số dư ví không đủ để trả khoản này bằng ví. Cách khắc phục: nạp thêm tiền vào ví, hoặc trả qua VNPay.

## "Payments are available only for ACTIVE contracts"

Câu này nghĩa là chỉ đóng học phí được cho hợp đồng đang hoạt động. Lỗi xảy ra khi hợp đồng còn chờ ký, đã hoàn tất hoặc đã hủy. Cách khắc phục: nếu hợp đồng đang chờ, chờ hai bên ký xong.

## "This lesson installment is not scheduled yet"

Câu này nghĩa là kỳ học phí của hợp đồng **Mỗi buổi** chưa gắn với buổi học nào, nên chưa đóng được. Cách khắc phục: chờ gia sư lên lịch buổi học; kỳ sẽ có ngày đến hạn và đóng được.

## "Only pending installments can be paid"

Câu này nghĩa là kỳ học phí đã được thanh toán rồi. Cách khắc phục: tải lại trang để xem trạng thái mới; không cần đóng lại.

## "Không thể tạo thanh toán." hoặc "Could not create the payment."

Câu này nghĩa là PTutor chưa tạo được link VNPay. Cách khắc phục: thử lại sau ít phút. Nếu vẫn lỗi, liên hệ PTutor.

## "Payment could not be started. Your draft has been saved."

Câu này hiện khi đăng tin tìm gia sư, nghĩa là bản nháp đã lưu nhưng chưa mở được trang thanh toán. Cách khắc phục: bấm **Retry payment**, hoặc vào **My Studying Requests** và chọn **Pay to publish**.

## "User account is not active"

Câu này nghĩa là tài khoản đang bị khóa hoặc ngừng hoạt động, nên không thao tác được với ví. Cách khắc phục: xem tài liệu "Tài khoản bị tạm khóa hoặc khóa vĩnh viễn".
