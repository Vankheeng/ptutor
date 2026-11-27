---
id: tai-khoan-ngan-hang
title: Khai báo tài khoản ngân hàng nhận tiền
type: policy
audience: [STUDENT, TUTOR]
features: [withdrawal]
routes: [/student/wallet, /tutor/wallet]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: WalletOverview/WalletDialogs; backend: BankAccountController /otp và /confirm"
  - "backend: BankAccountController GET/PUT /api/v1/users/me/bank-account -> BankAccountService"
  - "backend: dto/request/UpdateBankAccountRequest (bankCode tối đa 20 ký tự chữ số, gạch; bankName tối đa 100; accountNumber 6–34 chữ số; accountHolderName tối đa 150)"
  - "backend: security/BankAccountCryptoService (mã hoá số tài khoản)"
  - "frontend: WalletDialogs, WalletOverview (Liên kết ngân hàng, Thay đổi ngân hàng)"
error_codes: [USER_INACTIVE, WALLET_NOT_FOUND, BANK_ACCOUNT_REQUIRED]
keywords: [tài khoản ngân hàng, số tài khoản, khai báo ngân hàng, nhận tiền, tai khoan ngan hang, so tai khoan, khai bao ngan hang, bank account]
todo: []
---

# Khai báo tài khoản ngân hàng nhận tiền

## Tài khoản ngân hàng dùng để làm gì

Tài khoản ngân hàng là nơi PTutor chuyển tiền khi bạn rút tiền từ ví. Bạn phải khai báo tài khoản ngân hàng trước khi gửi yêu cầu rút tiền. Mỗi tài khoản PTutor có một tài khoản ngân hàng nhận tiền; khai báo lại sẽ thay thông tin cũ.

## Thông tin cần khai báo

Khi khai báo tài khoản ngân hàng, bạn cung cấp:

- Mã ngân hàng: tối đa 20 ký tự, chỉ gồm chữ, số, gạch ngang hoặc gạch dưới.
- Tên ngân hàng: tối đa 100 ký tự.
- Số tài khoản: từ 6 đến 34 chữ số.
- Tên chủ tài khoản: tối đa 150 ký tự.

PTutor mã hóa số tài khoản khi lưu.

## Giao diện khai báo tài khoản ngân hàng

Vào **Ví tiền** hoặc **Wallet & Payments**, chọn **Liên kết ngân hàng**; nếu đã liên kết, chọn **Thay đổi ngân hàng**. Chọn ngân hàng, nhập số tài khoản và tên chủ tài khoản, rồi xác nhận bằng OTP. Bạn cũng có thể mở biểu mẫu ngân hàng từ luồng **Rút tiền**.

## Khi tài khoản đang bị khóa

Tài khoản đang bị khóa hoặc ngừng hoạt động không khai báo hay sửa được tài khoản ngân hàng.

## Tên chủ tài khoản

Biểu mẫu yêu cầu nhập tên chủ tài khoản. Mã nguồn kiểm tra định dạng và độ dài nhưng chưa có bước đối chiếu tự động tên này với họ tên đăng ký PTutor. Hãy nhập đúng tên tại ngân hàng để hạn chế lỗi chuyển khoản; nhân viên có thể xem thông tin khi xử lý yêu cầu rút.
