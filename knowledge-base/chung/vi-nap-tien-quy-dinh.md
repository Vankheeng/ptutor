---
id: vi-nap-tien-quy-dinh
title: "Nạp ví: quy định và cách nạp"
type: policy
audience: [STUDENT, TUTOR]
features: [wallet]
routes: [/student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: PaymentController POST /api/v1/users/me/wallet/top-ups/vnpay -> PaymentService.createWalletTopUp"
  - "backend: PaymentService.MIN_WALLET_TOP_UP = 10000, MAX_WALLET_TOP_UP = 100000000; requireWholeVnd"
  - "backend: PaymentService.activatePaidPayment -> WalletService.credit (TOP_UP)"
  - "backend: AuthService.register (tạo ví số dư 0)"
  - "frontend: pages/common-user/WalletPage.tsx (Wallet & Payments; mở VNPay ở thẻ mới)"
  - "frontend: pages/common-user/components/WalletTopUpForm.tsx (Top up wallet, Amount (VND), Open VNPay — nút khóa khi ô trống)"
  - "frontend: StudentLayout menu Wallet & Payments; TutorLayout menu Ví tiền"
  - "backend: PaymentController POST /api/v1/users/me/wallet/top-ups/vnpay"
error_codes: [INVALID_WALLET_TOP_UP_AMOUNT]
keywords: [nạp ví, nạp tiền, nạp tối thiểu, nạp tối đa, số dư ví, ví ptutor, nap vi, nap tien, toi thieu, toi da, so du vi, top up, cách nạp ví, nạp tiền vào ví, open vnpay, cach nap vi, nap tien vao vi, top up wallet]
todo:
  - "Số dư ví có rút ra được không, và khi nào kế toán xử lý yêu cầu rút? (hiện chưa có quy trình xử lý)"
  - "Tiền nạp vào ví có hết hạn sử dụng không?"
---

# Nạp ví: quy định và cách nạp

## Ví PTutor dùng để làm gì

Mỗi tài khoản học viên và gia sư có một ví PTutor, tạo sẵn khi đăng ký với số dư 0đ. Số dư ví dùng để trả phí đăng tin và đóng học phí (khi giao diện hỗ trợ), và là nơi nhận tiền hoàn: hoàn phí đăng tin bị từ chối, hoàn học phí khi yêu cầu hoàn tiền được duyệt.

## Mức nạp ví

Khi nạp ví, số tiền mỗi lần nạp phải:

- Từ 10.000đ đến 100.000.000đ.
- Là số tiền chẵn đồng, không có phần lẻ.

Không giới hạn số lần nạp trong ngày.

## Nạp ví bằng cách nào

Bạn nạp ví qua VNPay ở trang ví. Link thanh toán VNPay có hiệu lực 15 phút. PTutor chỉ cộng tiền vào ví khi VNPay xác nhận giao dịch thành công và đúng số tiền.

## Khi nào tiền vào ví

Tiền được cộng vào số dư ví ngay khi PTutor nhận xác nhận thành công từ VNPay. Bạn nhận thông báo "Payment successful", và giao dịch nạp tiền hiện trong mục **Transactions** của trang ví. Nếu giao dịch VNPay thất bại, số dư ví không đổi.

## Các bước nạp tiền vào ví

1. Mở trang ví: học viên chọn **Wallet & Payments**, gia sư chọn **Ví tiền** trong menu.
2. Ở khung **Top up wallet**, nhập số tiền vào ô **Amount (VND)**, ví dụ 100000.
3. Bấm **Open VNPay**. Trang VNPay mở ra ở một thẻ mới của trình duyệt.
4. Hoàn tất thanh toán trên VNPay trong vòng 15 phút.
5. Quay lại trang ví và tải lại trang để xem số dư mới ở mục **Available balance**.

Nút **Open VNPay** bị khóa khi ô số tiền còn trống.

## Số tiền nạp hợp lệ

Mỗi lần nạp từ 10.000đ đến 100.000.000đ, nhập số chẵn đồng, không dấu chấm, không phần lẻ. Nhập ngoài khoảng này, trang báo câu tiếng Anh "Top-up amount must be between 10000 and 100000000 VND".

## Khi trang VNPay không mở ra

Trang VNPay mở ở thẻ mới. Nếu không thấy thẻ mới, trình duyệt có thể đang chặn cửa sổ bật lên. Bạn cho phép cửa sổ bật lên cho PTutor rồi bấm **Open VNPay** lại.
