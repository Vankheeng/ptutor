---
id: rut-tien
title: Rút tiền và hủy yêu cầu rút tiền
type: policy
audience: [STUDENT, TUTOR]
features: [withdrawal]
routes: [/student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: WithdrawalController POST/GET /api/v1/users/me/wallet/withdrawals, PATCH /{withdrawalId}/cancel"
  - "backend: WithdrawalService.create (cần tài khoản ngân hàng; tạm giữ tiền), cancel (chỉ PENDING; trả lại ví)"
  - "backend: WalletService.reserveForWithdrawal, reverseWithdrawal"
  - "backend: dto/request/CreateWithdrawalRequest (số tiền > 0, tối đa 2 chữ số thập phân; ghi chú tối đa 500 ký tự)"
  - "backend: không có endpoint nào cho nhân viên duyệt, chuyển khoản hay từ chối yêu cầu rút"
  - "frontend: chưa có giao diện rút tiền"
error_codes: [BANK_ACCOUNT_REQUIRED, INSUFFICIENT_WALLET_BALANCE, WITHDRAWAL_NOT_CANCELLABLE, WITHDRAWAL_NOT_FOUND, INVALID_WALLET_AMOUNT, USER_INACTIVE]
keywords: [rút tiền, rút tiền về ngân hàng, hủy rút tiền, lệnh rút, rut tien, huy rut tien, lenh rut, withdraw]
todo:
  - "Ai xử lý yêu cầu rút tiền, trong bao lâu, có phí rút không, có mức rút tối thiểu hay tối đa không? (hệ thống chưa có quy trình xử lý)"
  - "Khi nào có giao diện rút tiền? Tên trang và tên nút là gì?"
---

# Rút tiền và hủy yêu cầu rút tiền

## Gửi yêu cầu rút tiền

Bạn gửi yêu cầu rút một phần hoặc toàn bộ số dư ví về tài khoản ngân hàng đã khai báo. Điều kiện:

- Tài khoản PTutor đang hoạt động.
- Đã khai báo tài khoản ngân hàng nhận tiền.
- Số tiền rút lớn hơn 0đ và không vượt quá số dư dùng được.
- Ghi chú (nếu có) tối đa 500 ký tự.

Khi gửi yêu cầu, số tiền rút được trừ khỏi số dư dùng được và tạm giữ. Yêu cầu rút ở trạng thái chờ xử lý.

## Hủy yêu cầu rút tiền

Bạn hủy được yêu cầu rút tiền khi yêu cầu còn đang chờ xử lý. Khi hủy, số tiền tạm giữ được trả lại vào số dư dùng được ngay. Yêu cầu đã chuyển sang bước khác thì không hủy được.

## Xử lý yêu cầu rút tiền

Hiện PTutor chưa có quy trình để nhân viên xử lý yêu cầu rút tiền trên hệ thống. Yêu cầu rút sẽ ở trạng thái chờ xử lý cho tới khi PTutor có hướng dẫn. Nếu cần nhận tiền gấp, bạn liên hệ PTutor.

## Giao diện rút tiền

Hiện trang ví chưa có nút rút tiền. Các quy định trên là quy định của hệ thống và sẽ áp dụng khi có giao diện.
