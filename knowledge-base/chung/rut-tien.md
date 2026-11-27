---
id: rut-tien
title: Rút tiền và hủy yêu cầu rút tiền
type: policy
audience: [STUDENT, TUTOR]
features: [withdrawal]
routes: [/student/wallet, /tutor/wallet]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: WithdrawalService.createVerified/cancel; AdminWithdrawalService.approve/reject/complete"
  - "backend: WithdrawalController POST/GET /api/v1/users/me/wallet/withdrawals, PATCH /{withdrawalId}/cancel"
  - "backend: WithdrawalService.create (cần tài khoản ngân hàng; tạm giữ tiền), cancel (chỉ PENDING; trả lại ví)"
  - "backend: WalletService.reserveForWithdrawal, reverseWithdrawal"
  - "backend: dto/request/CreateWithdrawalRequest (số tiền > 0, tối đa 2 chữ số thập phân; ghi chú tối đa 500 ký tự)"
  - "backend: không có endpoint nào cho nhân viên duyệt, chuyển khoản hay từ chối yêu cầu rút"
  - "frontend: WalletPage, WalletDialogs, WalletOverview"
error_codes: [BANK_ACCOUNT_REQUIRED, INSUFFICIENT_WALLET_BALANCE, WITHDRAWAL_NOT_CANCELLABLE, WITHDRAWAL_NOT_FOUND, INVALID_WALLET_AMOUNT, USER_INACTIVE]
keywords: [rút tiền, rút tiền về ngân hàng, hủy rút tiền, lệnh rút, rut tien, huy rut tien, lenh rut, withdraw]
todo: []
---

# Rút tiền và hủy yêu cầu rút tiền

## Gửi yêu cầu rút tiền

Bạn gửi yêu cầu rút một phần hoặc toàn bộ số dư ví về tài khoản ngân hàng đã khai báo. Điều kiện:

- Tài khoản PTutor đang hoạt động.
- Đã khai báo tài khoản ngân hàng nhận tiền.
- Số tiền rút từ **50.000đ** và không vượt quá số dư dùng được.
- Ghi chú (nếu có) tối đa 500 ký tự.

Khi gửi yêu cầu, số tiền rút được trừ khỏi số dư dùng được và tạm giữ. Yêu cầu rút ở trạng thái chờ xử lý.

## Hủy yêu cầu rút tiền

Bạn hủy được yêu cầu rút tiền khi yêu cầu còn đang chờ xử lý. Khi hủy, số tiền tạm giữ được trả lại vào số dư dùng được ngay. Yêu cầu đã chuyển sang bước khác thì không hủy được.

## Xử lý yêu cầu rút tiền

Nhân viên PTutor duyệt yêu cầu đang chờ, sau đó chuyển khoản ngân hàng và ghi mã giao dịch để hoàn tất. Nếu từ chối, hệ thống hoàn ngay tiền đang tạm giữ về số dư dùng được và thông báo lý do. Chưa có thời hạn xử lý cam kết hoặc phí rút được cấu hình trong mã nguồn.

## Giao diện rút tiền

Mở **Ví tiền** hoặc **Wallet & Payments**, chọn **↗ Rút tiền**. Nếu chưa liên kết tài khoản ngân hàng, chọn **Liên kết ngân hàng**. Nhập số tiền, xác thực OTP rồi gửi yêu cầu. Danh sách **Yêu cầu rút tiền** trên trang ví cho phép theo dõi và thu hồi yêu cầu khi còn chờ duyệt.

## Xác thực khi rút tiền

Yêu cầu rút cần mã OTP. PTutor tạm giữ tiền ngay khi xác thực và tạo yêu cầu thành công. Việc duyệt không đồng nghĩa tiền đã vào ngân hàng; trạng thái **Hoàn tất** chỉ được ghi sau khi nhân viên chuyển khoản thành công.
