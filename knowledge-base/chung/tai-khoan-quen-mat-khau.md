---
id: tai-khoan-quen-mat-khau
title: "Quên mật khẩu và đổi mật khẩu"
type: guide
audience: [PUBLIC]
features: [account]
routes: [/forgot-password, /login, /student/profile, /tutor/profile]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: PasswordResetController POST /api/v1/auth/password-reset/otp, /verify, /reset"
  - "backend: ForgotPasswordService (OTP_TTL = 5 phút; mã 6 chữ số; huỷ mã cũ khi gửi mã mới)"
  - "backend: EmailService (tiêu đề email: PTutor password reset OTP)"
  - "frontend: pages/ForgotPasswordPage.tsx"
  - "backend: AccountController PUT /api/v1/users/me/password -> AccountService.changePassword"
  - "frontend: không có trang hay nút đổi mật khẩu khi đã đăng nhập (đã rà StudentProfilePage, TutorProfilePage, các service)"
error_codes: [EMAIL_NOT_FOUND, INVALID_OR_EXPIRED_OTP, PASSWORD_CONFIRMATION_MISMATCH, NEW_PASSWORD_MUST_BE_DIFFERENT, CURRENT_PASSWORD_INCORRECT]
keywords: [quên mật khẩu, đặt lại mật khẩu, mã OTP, không nhận được OTP, quen mat khau, dat lai mat khau, ma otp, reset password, đổi mật khẩu, thay mật khẩu, mật khẩu cũ, doi mat khau, thay mat khau, change password]
todo: []
---

# Quên mật khẩu và đổi mật khẩu

## Các bước đặt lại mật khẩu

1. Ở trang đăng nhập, mở trang **Quên mật khẩu?**.
2. Nhập **Email** đã đăng ký, bấm **Gửi mã OTP**.
3. Mở hộp thư, tìm email có tiêu đề "PTutor password reset OTP" và lấy mã gồm 6 chữ số.
4. Nhập **Mã OTP**, bấm **Xác thực OTP**.
5. Nhập **Mật khẩu mới** và **Xác nhận mật khẩu mới**, bấm **Đặt lại mật khẩu**.

Khi đặt lại thành công, bạn thấy thông báo "Đổi mật khẩu thành công. Vui lòng đăng nhập lại." và đăng nhập bằng mật khẩu mới.

## Thời hạn của mã OTP

Mã OTP có hiệu lực trong 5 phút kể từ lúc gửi. Trang hiện đồng hồ "Mã có hiệu lực trong …". Khi hết giờ, trang báo "Mã OTP đã hết hạn" và nút **Xác thực OTP** bị khóa.

Mỗi mã OTP chỉ dùng được một lần. Khi bạn bấm **Gửi lại OTP**, mã cũ mất hiệu lực ngay, chỉ mã mới nhất dùng được.

Muốn nhận mã ở email khác, bấm **Đổi email** để quay lại bước nhập email.

## Yêu cầu với mật khẩu mới

Mật khẩu mới dài từ 8 đến 72 ký tự, phải trùng với ô xác nhận, và phải khác mật khẩu đang dùng. Sau khi đặt lại thành công, mọi mã OTP chưa dùng của tài khoản đều mất hiệu lực.

## Đổi mật khẩu khi đang đăng nhập

Hiện giao diện PTutor chưa có nút đổi mật khẩu trong trang hồ sơ. Nếu muốn đổi mật khẩu, bạn dùng cách đặt lại mật khẩu bằng mã OTP:

1. Đăng xuất.
2. Ở trang đăng nhập, mở trang **Quên mật khẩu?**.
3. Làm theo các bước nhận mã OTP qua email và đặt mật khẩu mới.

## Quy định khi đổi mật khẩu

Khi đổi mật khẩu, PTutor áp dụng các quy định sau:

- Bạn phải nhập đúng mật khẩu hiện tại.
- Mật khẩu mới phải khác mật khẩu hiện tại.
- Mật khẩu mới phải trùng với ô xác nhận.
- Mật khẩu dài từ 8 đến 72 ký tự.

## Gửi lại mã và đổi mật khẩu

Mã OTP đặt lại mật khẩu chỉ có hiệu lực 5 phút và mã mới làm mã cũ mất hiệu lực. Mã nguồn hiện chưa đặt giới hạn riêng cho số lần yêu cầu lại hoặc số lần nhập sai; đừng chia sẻ mã với người khác. Giao diện hiện chưa có nút **Đổi mật khẩu** khi đã đăng nhập, vì vậy hãy đăng xuất và dùng trang **Quên mật khẩu?**.

Nếu không thấy OTP sau khi kiểm tra hộp thư rác, kiểm tra lại địa chỉ email và bấm **Gửi lại OTP**; chỉ mã mới nhất còn hiệu lực. PTutor hiện chưa công bố kênh hỗ trợ ngoài ứng dụng cho người không đăng nhập được. Không đưa OTP cho chatbot hoặc người tự xưng hỗ trợ.
