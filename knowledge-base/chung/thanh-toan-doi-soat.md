---
id: thanh-toan-doi-soat
title: Đối soát khi đã bị trừ tiền nhưng PTutor chưa ghi nhận
type: guide
audience: [STUDENT, TUTOR]
features: [payment, wallet]
routes: [/student/wallet/transactions, /tutor/wallet/transactions, /student/support-requests, /tutor/support-requests]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: PaymentService, VnPayService (chỉ ghi nhận callback hợp lệ và đúng số tiền)"
  - "frontend: WalletTransactionsPage, SupportRequestsPage, AssistantPanel"
error_codes: []
keywords: [bị trừ tiền chưa ghi nhận, thanh toán treo, đối soát VNPay, chờ cộng ví, bi tru tien, doi soat, payment pending]
todo: []
---

# Đối soát khi đã bị trừ tiền nhưng PTutor chưa ghi nhận

## Kiểm tra trước khi gửi yêu cầu

Nếu ngân hàng hoặc VNPay đã báo trừ tiền nhưng PTutor chưa hiện kết quả, đừng thanh toán lại ngay. Mở **Ví → Lịch sử giao dịch** để xem khoản nạp hoặc thanh toán, rồi kiểm tra trạng thái kỳ học phí hoặc tin đăng tương ứng. Tải lại trang sau ít phút vì kết quả có thể đến sau thao tác chuyển trang.

PTutor chỉ ghi nhận thanh toán khi nhận xác nhận VNPay hợp lệ và số tiền khớp. Một thông báo trừ tiền từ ngân hàng chưa đủ để kết luận kỳ học phí đã thanh toán hoặc ví đã được cộng.

## Gửi thông tin để nhân viên đối soát

Nếu trạng thái vẫn chưa đúng, mở **Yêu cầu hỗ trợ của tôi** hoặc **Hỏi nhân viên hỗ trợ** trong trợ lý. Nêu loại giao dịch, thời gian, số tiền, mã giao dịch ngân hàng/VNPay và mã hợp đồng hoặc tin đăng nếu có. Có thể đính kèm biên nhận đã che thông tin nhạy cảm. Không gửi số thẻ đầy đủ, mật khẩu, mã OTP hoặc mã CVV.

Nhân viên cần kiểm tra giao dịch trước khi hướng dẫn thanh toán lại hoặc điều chỉnh trạng thái. Mã nguồn chưa bảo đảm thời hạn đối soát cố định; theo dõi câu trả lời trong **Yêu cầu hỗ trợ**.

## Giao dịch thất bại hoặc hết hạn

Link VNPay thường có hiệu lực 15 phút. Nếu link hết hạn hoặc VNPay xác nhận thất bại và ngân hàng không trừ tiền, người dùng có thể tạo lần thanh toán mới. Nếu ngân hàng đã trừ tiền, hãy đối soát trước để tránh trả trùng.
