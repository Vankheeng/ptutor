---
id: tra-cuu-loi-theo-buoc
title: Tra lỗi theo bước đang thực hiện và cách thử lại an toàn
type: error
audience: [STUDENT, TUTOR]
features: [account, payment, wallet, matching, contract, lesson]
routes: [/login, /student/wallet, /tutor/wallet, /student/contracts, /tutor/sections/contracts]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: services/errorCodes.ts, pages/common-user/WalletPage.tsx"
  - "backend: AuthService, PaymentService, WithdrawalService, LessonService"
error_codes: [INVALID_CREDENTIALS, INSUFFICIENT_WALLET_BALANCE, WITHDRAWAL_OTP_REQUIRED, TUTOR_PROFILE_NOT_VERIFIED, CONTRACT_NOT_ACTIVE, LESSON_COMPLETION_ON_HOLD]
keywords: [báo lỗi, không thao tác được, thử lại, mã lỗi, tiền bị trừ, không đăng nhập, không rút được, bao loi, khong thao tac duoc]
todo: []
---

# Tra lỗi theo bước đang thực hiện và cách thử lại an toàn

## Đăng nhập hoặc xác thực

**Email or password is incorrect**: kiểm tra email, mật khẩu hoặc dùng **Quên mật khẩu?**. **OTP không hợp lệ hoặc đã hết hạn**: dùng mã mới nhất trong vòng 5 phút; không chia sẻ mã. Tài khoản bị khóa không thể tự mở bằng cách đăng ký tài khoản mới với cùng CCCD.

## Thanh toán và ví

**Wallet balance is insufficient**: số dư khả dụng không đủ; kiểm tra tiền đang tạm giữ cho lệnh rút, nạp thêm hoặc chọn VNPay. **This lesson installment is not scheduled yet**: gia sư cần tạo buổi học tương ứng trước khi học viên trả kỳ **Mỗi buổi**. Nếu ngân hàng đã trừ tiền nhưng PTutor chưa ghi nhận, hãy đối soát trước khi trả lần nữa.

## Gia sư đăng tin hoặc tìm học viên

**TUTOR_PROFILE_NOT_VERIFIED**: hồ sơ gia sư chưa được duyệt; hoàn thiện hồ sơ và chờ kết quả trước khi công bố tin nhận dạy hoặc dùng gợi ý học viên. Nếu tin đang chờ duyệt, không tạo thêm tin giống hệt chỉ vì chưa thấy trên trang công khai.

## Hợp đồng và buổi học

**CONTRACT_NOT_ACTIVE**: chỉ tạo và quản lý buổi học hoặc đóng kỳ theo quy tắc của hợp đồng đang hoạt động. **LESSON_COMPLETION_ON_HOLD**: buổi có yêu cầu hoàn tiền hoặc hợp đồng có khiếu nại chưa kết luận; chờ nhân viên xử lý rồi tải lại. Khi trạng thái đã thay đổi do bên kia thao tác, tải lại trang trước khi thử lần nữa để tránh gửi trùng.

## Khi không xác định được lỗi

Ghi lại tên trang, thao tác vừa làm, thời gian, mã lỗi hoặc ảnh chụp đã che thông tin nhạy cảm; gửi **Yêu cầu hỗ trợ** nếu đã đăng nhập. Không gửi OTP, mật khẩu, số thẻ hay toàn bộ CCCD trong câu hỏi cho chatbot.
