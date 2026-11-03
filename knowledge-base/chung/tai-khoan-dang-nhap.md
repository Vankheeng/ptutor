---
id: tai-khoan-dang-nhap
title: Đăng nhập, đăng xuất và thời hạn phiên
type: guide
audience: [PUBLIC]
features: [account]
routes: [/login, /register, /forgot-password]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: AuthController POST /api/v1/auth/login, /refresh, /logout -> AuthService.login"
  - "backend: JwtService (app.security.jwt.access-token-ttl), RefreshTokenService (app.security.jwt.refresh-token-ttl)"
  - "config: application-prod.yaml JWT_ACCESS_TOKEN_TTL=36000, JWT_REFRESH_TOKEN_TTL=2592000"
  - "frontend: pages/LoginPage.tsx; services/httpClient.ts (tự làm mới phiên)"
error_codes: [INVALID_CREDENTIALS, ACCOUNT_TEMPORARILY_SUSPENDED, ACCOUNT_PERMANENTLY_SUSPENDED, ACCOUNT_INACTIVE]
keywords: [đăng nhập, đăng xuất, phiên đăng nhập, bị đăng xuất, login, dang nhap, dang xuat, het phien]
todo:
  - "Xác nhận thời hạn phiên đăng nhập trên môi trường thật (mặc định cấu hình: 10 giờ cho phiên làm việc, 30 ngày cho phiên ghi nhớ)."
  - "Nền tảng có giới hạn số lần đăng nhập sai không? (code hiện không giới hạn)"
---

# Đăng nhập, đăng xuất và thời hạn phiên

## Các bước đăng nhập

1. Mở trang **Đăng nhập Ptutor**.
2. Nhập **Email** và **Mật khẩu** đã đăng ký.
3. Bấm **Đăng nhập**.

Đăng nhập thành công, bạn thấy thông báo "Đăng nhập thành công." và được đưa vào khu vực của vai trò mình: học viên vào trang tổng quan học viên, gia sư vào trang **Tổng quan** của gia sư.

Email không phân biệt chữ hoa, chữ thường.

## Khi đăng nhập không thành công

Khi email hoặc mật khẩu không đúng, trang đăng nhập hiện câu tiếng Anh "Email or password is incorrect", nghĩa là email hoặc mật khẩu không đúng. Bạn kiểm tra lại email, bật phím viết hoa đúng, rồi thử lại. Nếu quên mật khẩu, bấm vào trang **Quên mật khẩu?** để đặt lại.

Khi tài khoản đang bị khóa, bạn không đăng nhập được dù nhập đúng mật khẩu. Xem tài liệu "Tài khoản bị tạm khóa hoặc khóa vĩnh viễn".

## Thời hạn phiên đăng nhập

Sau khi đăng nhập, PTutor giữ phiên làm việc cho bạn. Theo cấu hình mặc định, mỗi phiên làm việc dài 10 giờ và được tự động gia hạn trong tối đa 30 ngày mà bạn không phải đăng nhập lại. Quá thời hạn đó, hoặc khi bạn đăng xuất, bạn cần đăng nhập lại.

Khi tài khoản bị khóa, PTutor đăng xuất tài khoản khỏi mọi thiết bị ngay lập tức.

## Đăng xuất

Đăng xuất làm phiên trên thiết bị đó hết hiệu lực. Bạn nên đăng xuất khi dùng máy tính chung.
