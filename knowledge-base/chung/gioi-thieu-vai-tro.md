---
id: gioi-thieu-vai-tro
title: "PTutor, các vai trò và liên hệ hỗ trợ"
type: policy
audience: [PUBLIC]
features: [account]
routes: [/, /public, /register, /login]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: AssistantPanel/SupportRequestsPage; backend: SupportRequestService"
  - "backend: dto/enums/RegistrationRole (STUDENT, TUTOR)"
  - "backend: dto/enums/UserRole (STUDENT, TUTOR, EMPLOYEE, ADMIN)"
  - "backend: entity/enums/EmployeeJobFunction"
  - "frontend: App.tsx (RoleGuard theo vai trò), pages/RegisterPage.tsx"
  - "frontend: components/home/homeContent.ts, HomeLanding.tsx (Liên hệ Ptutor, Trung tâm trợ giúp, Chatbot hỗ trợ — các liên kết chưa trỏ tới trang thật)"
  - "backend: không có cấu hình kênh liên hệ, giờ hỗ trợ"
error_codes: [INVALID_REGISTRATION_ROLE]
keywords: [vai trò, học viên, gia sư, nhân viên, phụ huynh, ptutor là gì, vai tro, hoc vien, gia su, nhan vien, phu huynh, liên hệ, hỗ trợ, hotline, email hỗ trợ, gặp nhân viên, giờ làm việc, lien he, ho tro, gap nhan vien, gio lam viec, contact]
todo: []
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

Học viên và gia sư đã đăng nhập dùng **Yêu cầu hỗ trợ của tôi** trong ứng dụng để hỏi nhân viên. Từ khung trợ lý cũng có nút **Hỏi nhân viên hỗ trợ**. PTutor chưa công bố email, số điện thoại, Zalo hoặc fanpage hỗ trợ đã xác minh; không gửi thông tin nhạy cảm tới tài khoản tự nhận là PTutor.

## Bộ phận nào xử lý việc của tôi?

- Khiếu nại về hợp đồng, buổi học, hành vi: nhân viên chăm sóc khách hàng.
- Tài khoản bị khóa: nhân viên chăm sóc khách hàng.
- Yêu cầu hoàn tiền học phí: kế toán.
- Duyệt tin đăng, hồ sơ gia sư, chứng chỉ: nhân viên chăm sóc khách hàng.

## Trợ lý hỗ trợ giúp được gì?

Trợ lý hỗ trợ trả lời quy định, hướng dẫn thao tác và tra cứu thông tin của chính bạn. Trợ lý không làm thay bạn các thao tác như ký, hủy hợp đồng, thanh toán, rút tiền hay gửi khiếu nại, và không quyết định thay nhân viên về khiếu nại hay hoàn tiền.

## Phụ huynh và nhiều vai trò

PTutor chưa có vai trò **phụ huynh** hoặc hồ sơ riêng cho nhiều con. Người giám hộ có thể dùng vai trò **Học viên** để đăng nhu cầu học cho con, nhưng phải đăng ký bằng email và CCCD của chính người đứng tên; mỗi tin cần ghi rõ lớp và mục tiêu học. Một tài khoản có thể tạo nhiều tin, còn hồ sơ cá nhân là một hồ sơ chung. Mỗi CCCD chỉ được dùng cho một tài khoản, vì vậy hiện không thể mở thêm tài khoản gia sư thứ hai bằng cùng CCCD dù dùng email khác.

## Thời gian hỗ trợ

Hệ thống nhận yêu cầu hỗ trợ trực tuyến mọi lúc. PTutor chưa cấu hình hoặc công bố giờ làm việc và thời hạn phản hồi bắt buộc. Dự thảo vận hành đề xuất nhân viên trực thứ Hai–thứ Sáu 08:00–17:00 và phản hồi đầu tiên trong 1 ngày làm việc; đây **chưa phải cam kết hiện hành**.
