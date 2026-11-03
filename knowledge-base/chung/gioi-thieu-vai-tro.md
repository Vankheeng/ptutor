---
id: gioi-thieu-vai-tro
title: "PTutor, các vai trò và liên hệ hỗ trợ"
type: policy
audience: [PUBLIC]
features: [account]
routes: [/, /public, /register, /login]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: dto/enums/RegistrationRole (STUDENT, TUTOR)"
  - "backend: dto/enums/UserRole (STUDENT, TUTOR, EMPLOYEE, ADMIN)"
  - "backend: entity/enums/EmployeeJobFunction"
  - "frontend: App.tsx (RoleGuard theo vai trò), pages/RegisterPage.tsx"
  - "frontend: components/home/homeContent.ts, HomeLanding.tsx (Liên hệ Ptutor, Trung tâm trợ giúp, Chatbot hỗ trợ — các liên kết chưa trỏ tới trang thật)"
  - "backend: không có cấu hình kênh liên hệ, giờ hỗ trợ"
error_codes: [INVALID_REGISTRATION_ROLE]
keywords: [vai trò, học viên, gia sư, nhân viên, phụ huynh, ptutor là gì, vai tro, hoc vien, gia su, nhan vien, phu huynh, liên hệ, hỗ trợ, hotline, email hỗ trợ, gặp nhân viên, giờ làm việc, lien he, ho tro, gap nhan vien, gio lam viec, contact]
todo:
  - "Phụ huynh muốn tìm gia sư cho con thì đăng ký tài khoản với vai trò nào? Nền tảng có cho một người dùng một tài khoản cho nhiều con không?"
  - "Một người có thể vừa là học viên vừa là gia sư (hai tài khoản, hai email) không?"
  - "Kênh liên hệ chính thức của PTutor là gì (email, số điện thoại, Zalo, fanpage)?"
  - "Giờ hỗ trợ trong tuần là mấy giờ đến mấy giờ?"
  - "Thời gian phản hồi dự kiến cho từng loại yêu cầu là bao lâu?"
---

# PTutor, các vai trò và liên hệ hỗ trợ

## PTutor là gì

PTutor là nền tảng kết nối học viên với gia sư. Học viên đăng tin tìm gia sư hoặc đăng ký học theo tin nhận dạy của gia sư. Gia sư gửi đề xuất dạy cho học viên. Hai bên thống nhất bằng hợp đồng trên PTutor, sau đó học viên đóng học phí theo các kỳ thanh toán của hợp đồng.

## Các vai trò trên PTutor

PTutor có bốn vai trò:

- **Học viên**: người tìm gia sư, ký hợp đồng học và đóng học phí.
- **Gia sư**: người dạy, có hồ sơ gia sư và chứng chỉ được nhân viên chăm sóc khách hàng xét duyệt.
- **Nhân viên PTutor**: gồm nhân viên chăm sóc khách hàng và nhân viên kế toán. Nhân viên chăm sóc khách hàng duyệt tin đăng, hồ sơ gia sư, chứng chỉ, xem xét khiếu nại và hỗ trợ tài khoản. Kế toán xem xét yêu cầu hoàn tiền.
- **Quản trị viên**: người quản lý nền tảng.

PTutor không có vai trò phụ huynh riêng.

## Vai trò bạn được chọn khi đăng ký

Khi tự đăng ký tài khoản, bạn chỉ chọn được một trong hai vai trò: **Tôi là học viên** hoặc **Tôi là gia sư**. Tài khoản nhân viên và quản trị viên do PTutor tạo, không đăng ký được từ trang **Tạo tài khoản**.

Mỗi tài khoản gắn với đúng một vai trò. Sau khi đăng nhập, học viên được đưa tới khu vực học viên, gia sư được đưa tới khu vực gia sư. Mỗi khu vực chỉ hiện các trang dành cho vai trò đó.

## Trợ lý hỗ trợ trả lời cho ai

Trợ lý hỗ trợ trên PTutor chỉ phục vụ học viên và gia sư đã đăng nhập. Trợ lý giải thích quy định, hướng dẫn thao tác và tra cứu thông tin của chính bạn. Trợ lý không làm thay bạn các việc như ký, hủy hợp đồng, thanh toán hay rút tiền.

## Liên hệ PTutor bằng cách nào?

Kênh liên hệ chính thức của PTutor đang được cập nhật. Trang chủ có mục **Hỗ trợ** với các liên kết "Liên hệ Ptutor" và "Trung tâm trợ giúp", nhưng các liên kết này chưa dẫn tới trang liên hệ riêng.

## Bộ phận nào xử lý việc của tôi?

- Khiếu nại về hợp đồng, buổi học, hành vi: nhân viên chăm sóc khách hàng.
- Tài khoản bị khóa: nhân viên chăm sóc khách hàng.
- Yêu cầu hoàn tiền học phí: kế toán.
- Duyệt tin đăng, hồ sơ gia sư, chứng chỉ: nhân viên chăm sóc khách hàng.

## Trợ lý hỗ trợ giúp được gì?

Trợ lý hỗ trợ trả lời quy định, hướng dẫn thao tác và tra cứu thông tin của chính bạn. Trợ lý không làm thay bạn các thao tác như ký, hủy hợp đồng, thanh toán, rút tiền hay gửi khiếu nại, và không quyết định thay nhân viên về khiếu nại hay hoàn tiền.
