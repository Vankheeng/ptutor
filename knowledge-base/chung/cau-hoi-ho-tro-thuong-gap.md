---
id: cau-hoi-ho-tro-thuong-gap
title: Các câu hỏi hỗ trợ thường gặp về tài khoản, tiền và tranh chấp
type: faq
audience: [STUDENT, TUTOR]
features: [account, wallet, withdrawal, complaint, refund]
routes: [/student/support-requests, /tutor/support-requests, /student/wallet, /tutor/wallet]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: SupportRequestService, WithdrawalService, AdminWithdrawalService, ComplaintService"
  - "frontend: SupportRequestsPage, WalletPage, ComplaintsPage"
error_codes: []
keywords: [hỏi nhân viên, tài khoản bị khóa, rút tiền chậm, hoàn tiền chưa về, khiếu nại, hỗ trợ PTutor, hoi nhan vien]
todo: []
---

# Các câu hỏi hỗ trợ thường gặp về tài khoản, tiền và tranh chấp

## Gửi câu hỏi cho nhân viên ở đâu?

Khi đã đăng nhập, mở **Yêu cầu hỗ trợ của tôi** hoặc menu **⋯** trong Trợ lý PTutor → **Hỏi nhân viên hỗ trợ**. Bạn tự kiểm tra nội dung rồi bấm gửi. Trang yêu cầu hỗ trợ cho biết nhân viên đã trả lời hay chưa.

## Rút tiền đã được duyệt nhưng chưa thấy ở ngân hàng?

**Đang xử lý** nghĩa là nhân viên đã duyệt, chưa xác nhận chuyển khoản hoàn tất. **Hoàn tất** chỉ được ghi sau khi nhân viên có mã giao dịch chuyển tiền. Kiểm tra trạng thái ở ví và gửi yêu cầu hỗ trợ kèm mã lệnh rút nếu cần đối soát; mã nguồn chưa bảo đảm thời hạn chuyển khoản cố định.

## Hoàn tiền được duyệt nhưng số dư ví không đúng?

Kiểm tra **Lịch sử giao dịch** và chi tiết yêu cầu hoàn tiền. Tiền hoàn được ghi vào ví PTutor, không tự về thẻ hoặc tài khoản ngân hàng đã thanh toán. Nếu số tiền không khớp kết luận, gửi yêu cầu hỗ trợ kèm mã hợp đồng và mã yêu cầu hoàn tiền; đừng gửi thông tin thẻ đầy đủ.

## Bất đồng về một buổi học thì hỏi nhân viên hay khiếu nại?

Nếu buổi đang **Chờ xác nhận** và không diễn ra, học viên dùng **Yêu cầu hoàn tiền**. Nếu tranh chấp về hành vi, chất lượng, buổi đã xác nhận nhầm hoặc điều khoản hợp đồng, dùng **Khiếu nại** khi còn trong thời hạn. Yêu cầu hỗ trợ phù hợp cho câu hỏi cách dùng hoặc đối soát kỹ thuật; nó không tự thay thế quyết định hoàn tiền hay kết luận khiếu nại.

## Tài khoản bị khóa thì liên hệ bằng cách nào?

Người bị khóa không đăng nhập để mở yêu cầu hỗ trợ trong ứng dụng. PTutor chưa công bố kênh ngoài ứng dụng được xác minh trong sản phẩm hiện tại. Không gửi CCCD, mật khẩu hoặc OTP cho tài khoản tự xưng là nhân viên; chatbot không được bịa số điện thoại hoặc email hỗ trợ.
