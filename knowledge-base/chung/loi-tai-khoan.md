---
id: loi-tai-khoan
title: Lỗi thường gặp khi đăng ký, đăng nhập, đặt lại mật khẩu
type: error
audience: [PUBLIC]
features: [account]
routes: [/register, /login, /forgot-password]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: AuthService.register/login; ForgotPasswordService; AccountService.changePassword"
  - "frontend: pages/RegisterPage.tsx, LoginPage.tsx, ForgotPasswordPage.tsx (hiển thị nguyên câu của máy chủ trong ô báo lỗi)"
error_codes: [EMAIL_ALREADY_EXISTS, CITIZEN_ID_ALREADY_EXISTS, DISTRICT_PROVINCE_MISMATCH, INVALID_CREDENTIALS, ACCOUNT_INACTIVE, ACCOUNT_TEMPORARILY_SUSPENDED, ACCOUNT_PERMANENTLY_SUSPENDED, EMAIL_NOT_FOUND, INVALID_OR_EXPIRED_OTP, NEW_PASSWORD_MUST_BE_DIFFERENT, PASSWORD_CONFIRMATION_MISMATCH]
keywords: [lỗi đăng ký, lỗi đăng nhập, email đã tồn tại, sai mật khẩu, OTP hết hạn, loi dang ky, loi dang nhap, sai mat khau, otp het han]
todo:
  - "Người dùng bị người khác dùng mất email hoặc số CCCD để đăng ký thì xử lý thế nào, liên hệ ai?"
---

# Lỗi thường gặp khi đăng ký, đăng nhập, đặt lại mật khẩu

## "Email is already registered"

Câu này nghĩa là email đã được dùng cho một tài khoản khác. Lỗi xảy ra khi bạn đăng ký bằng email đã có tài khoản. Cách khắc phục: đăng nhập bằng email đó, hoặc dùng trang **Quên mật khẩu?** nếu không nhớ mật khẩu. Muốn tạo tài khoản mới thì dùng email khác.

## "Citizen ID is already registered"

Câu này nghĩa là số CCCD đã được dùng để đăng ký một tài khoản khác. Mỗi số CCCD chỉ đăng ký được một tài khoản. Cách khắc phục: kiểm tra lại 12 chữ số; nếu đúng là CCCD của bạn, hãy đăng nhập tài khoản cũ.

## "District does not belong to the selected province"

Câu này nghĩa là quận/huyện không thuộc tỉnh/thành phố đã chọn. Cách khắc phục: chọn lại **Tỉnh/thành phố** trước, rồi chọn **Quận/huyện** trong danh sách mới hiện ra.

## "Email or password is incorrect"

Câu này nghĩa là email hoặc mật khẩu không đúng. Cách khắc phục: kiểm tra email, kiểm tra phím viết hoa, rồi thử lại. Nếu vẫn sai, đặt lại mật khẩu ở trang **Quên mật khẩu?**.

## "Account is suspended…" hoặc "Account is permanently suspended…"

Câu này nghĩa là tài khoản đang bị khóa, kèm lý do. Với tạm khóa, câu ghi thời điểm hết khóa theo giờ quốc tế (UTC, chậm hơn giờ Việt Nam 7 tiếng). Cách khắc phục: chờ hết thời hạn tạm khóa, hoặc liên hệ PTutor nếu bị khóa vĩnh viễn.

## "Account is inactive"

Câu này nghĩa là tài khoản đang ngừng hoạt động. Cách khắc phục: liên hệ PTutor.

## "No account is registered with this email"

Câu này hiện ở bước gửi mã OTP, nghĩa là chưa có tài khoản nào dùng email này. Cách khắc phục: kiểm tra lỗi gõ email, hoặc đăng ký tài khoản mới.

## "OTP không hợp lệ hoặc đã hết hạn"

Câu này nghĩa là mã OTP sai, đã quá 5 phút, đã dùng rồi, hoặc đã có mã mới hơn thay thế. Ở bước đặt mật khẩu mới, cùng lỗi này hiện bằng câu tiếng Anh "OTP is invalid, expired, or has already been used". Cách khắc phục: bấm **Gửi lại OTP** và dùng mã mới nhất trong vòng 5 phút.

## "New password must be different from the current password"

Câu này nghĩa là mật khẩu mới trùng mật khẩu đang dùng. Cách khắc phục: chọn một mật khẩu khác.

## "Mật khẩu xác nhận không khớp"

Câu này nghĩa là hai ô mật khẩu không giống nhau. Cách khắc phục: gõ lại cả hai ô cho trùng nhau.
