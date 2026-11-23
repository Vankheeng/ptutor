---
id: loi-rut-tien-tai-khoan-ngan-hang
title: Lỗi thường gặp khi rút tiền và khai báo tài khoản ngân hàng
type: error
audience: [STUDENT, TUTOR]
features: [withdrawal]
routes: [/student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: WithdrawalController POST /api/v1/users/me/wallet/withdrawals, PATCH /{withdrawalId}/cancel -> WithdrawalService.create/cancel"
  - "backend: WalletService.reserveForWithdrawal -> applyDebit (INSUFFICIENT_WALLET_BALANCE)"
  - "backend: BankAccountController PUT /api/v1/users/me/bank-account -> BankAccountService.updateCurrentBankAccount"
  - "backend: dto/request/CreateWithdrawalRequest (số tiền > 0, tối đa 13 chữ số phần nguyên và 2 chữ số thập phân; ghi chú tối đa 500 ký tự)"
  - "backend: dto/request/UpdateBankAccountRequest (mã ngân hàng tối đa 20 ký tự chữ, số, gạch; tên ngân hàng tối đa 100; số tài khoản 6–34 chữ số; tên chủ tài khoản tối đa 150)"
  - "backend: exception/GlobalExceptionHandler (lỗi kiểm tra dữ liệu trả câu chung Request validation failed kèm lỗi từng ô)"
  - "frontend: chưa có giao diện rút tiền và tài khoản ngân hàng; câu lỗi là câu tiếng Anh của máy chủ"
error_codes: [BANK_ACCOUNT_REQUIRED, INSUFFICIENT_WALLET_BALANCE, WITHDRAWAL_NOT_CANCELLABLE, WITHDRAWAL_NOT_FOUND, INVALID_WALLET_AMOUNT, USER_INACTIVE, WALLET_NOT_FOUND, VALIDATION_ERROR]
keywords: [lỗi rút tiền, không rút được tiền, không hủy được lệnh rút, lỗi tài khoản ngân hàng, số tài khoản không hợp lệ, loi rut tien, khong rut duoc tien, khong huy duoc lenh rut, loi tai khoan ngan hang]
todo:
  - "Khi có giao diện rút tiền và tài khoản ngân hàng, cập nhật các câu báo lỗi theo câu hiển thị thật."
  - "Có mức rút tối thiểu hoặc tối đa mỗi lần không? (code chỉ yêu cầu lớn hơn 0)"
---

# Lỗi thường gặp khi rút tiền và khai báo tài khoản ngân hàng

Hiện chưa có giao diện rút tiền và khai báo tài khoản ngân hàng. Các câu báo lỗi dưới đây là câu tiếng Anh hệ thống trả về, sẽ áp dụng khi có giao diện.

## "A bank account must be configured before requesting a withdrawal"

Câu này nghĩa là bạn chưa khai báo tài khoản ngân hàng nhận tiền. Bạn phải khai báo tài khoản ngân hàng trước khi gửi yêu cầu rút tiền. Cách khắc phục: khai báo tài khoản ngân hàng rồi gửi lại yêu cầu rút.

## "Wallet balance is insufficient"

Câu này hiện khi gửi yêu cầu rút tiền, nghĩa là số tiền muốn rút lớn hơn số dư dùng được trong ví. Tiền đang tạm giữ cho các yêu cầu rút khác không tính vào số dư dùng được. Cách khắc phục: rút số tiền nhỏ hơn hoặc bằng số dư dùng được, hoặc hủy yêu cầu rút cũ đang chờ để nhận lại tiền tạm giữ.

## "Amount must be positive with at most two decimal places"

Câu này nghĩa là số tiền rút không hợp lệ: bằng 0, âm, hoặc có quá hai chữ số sau dấu thập phân. Cách khắc phục: nhập số tiền lớn hơn 0đ, nên nhập số chẵn đồng.

## "Only a pending withdrawal request can be cancelled"

Câu này nghĩa là yêu cầu rút tiền không còn ở trạng thái chờ xử lý nên không hủy được. Yêu cầu đã hủy rồi cũng không hủy lại được. Cách khắc phục: xem lại trạng thái yêu cầu rút trong danh sách yêu cầu rút tiền.

## "Withdrawal request not found"

Câu này nghĩa là không tìm thấy yêu cầu rút tiền, hoặc yêu cầu đó không phải của bạn. Cách khắc phục: tải lại danh sách và chọn đúng yêu cầu rút của mình.

## "User account is not active"

Câu này nghĩa là tài khoản đang bị khóa hoặc ngừng hoạt động. Tài khoản như vậy không gửi được yêu cầu rút tiền và không khai báo hay sửa được tài khoản ngân hàng. Cách khắc phục: xem tài liệu "Tài khoản bị tạm khóa hoặc khóa vĩnh viễn".

## "Request validation failed" khi khai báo tài khoản ngân hàng

Câu này nghĩa là một ô thông tin ngân hàng chưa đúng yêu cầu. Kiểm tra lại:

- Mã ngân hàng: không để trống, tối đa 20 ký tự, chỉ gồm chữ, số, gạch ngang hoặc gạch dưới.
- Tên ngân hàng: không để trống, tối đa 100 ký tự.
- Số tài khoản: từ 6 đến 34 chữ số, không có dấu cách, dấu chấm hay chữ cái.
- Tên chủ tài khoản: không để trống, tối đa 150 ký tự.

## "Request validation failed" khi gửi yêu cầu rút tiền

Câu này nghĩa là số tiền hoặc ghi chú chưa đúng yêu cầu. Số tiền phải lớn hơn 0đ. Ghi chú (nếu có) tối đa 500 ký tự. Cách khắc phục: sửa lại ô báo lỗi rồi gửi lại.
