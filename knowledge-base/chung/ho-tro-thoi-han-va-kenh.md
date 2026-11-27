---
id: ho-tro-thoi-han-va-kenh
title: Kênh hỗ trợ và các thời hạn hiện được hệ thống áp dụng
type: policy
audience: [STUDENT, TUTOR]
features: [account, refund, complaint, withdrawal]
routes: [/student/support-requests, /tutor/support-requests, /student/complaints, /tutor/complaints]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: SupportRequestService, RefundPolicy, ComplaintService, WithdrawalService"
  - "frontend: AssistantPanel, SupportRequestsPage"
error_codes: []
keywords: [liên hệ hỗ trợ, bao lâu xử lý, thời hạn phản hồi, giờ hỗ trợ, khiếu nại bao lâu, hoàn tiền bao lâu, lien he, thoi han]
todo: []
---

# Kênh hỗ trợ và các thời hạn hiện được hệ thống áp dụng

## Kênh liên hệ có trên PTutor

Học viên và gia sư đã đăng nhập có thể mở **Yêu cầu hỗ trợ của tôi** hoặc chọn **Hỏi nhân viên hỗ trợ** từ trợ lý. Đây là kênh trong ứng dụng để gửi câu hỏi và theo dõi phản hồi. Nếu tài khoản không đăng nhập được, sản phẩm hiện chưa công bố kênh ngoài ứng dụng đã được xác minh; chatbot không nên tự đưa ra số điện thoại, email hoặc tài khoản mạng xã hội.

## Thời hạn hệ thống thực sự kiểm tra

- Mã OTP đặt lại mật khẩu có hiệu lực **5 phút**.
- Link thanh toán VNPay thường có hiệu lực **15 phút**.
- Gia sư có **48 giờ** để phản hồi yêu cầu hoàn tiền.
- Buổi chờ xác nhận có thể tự hoàn tất sau **3 ngày**, nếu không bị giữ vì hoàn tiền hoặc khiếu nại.
- Khiếu nại hợp đồng gửi được khi hợp đồng còn hiệu lực hoặc trong **7 ngày sau ngày kết thúc**.
- Yêu cầu hỗ trợ đã được nhân viên trả lời tự đóng sau **7 ngày** nếu người gửi không xác nhận giải quyết.

## Thời hạn chưa được bảo đảm

Mã nguồn hiện không tự bảo đảm số giờ làm việc của nhân viên, hạn duyệt tin/hồ sơ/chứng chỉ, hạn đối soát thanh toán, hạn kế toán kết luận hoàn tiền, hạn giải quyết khiếu nại hoặc hạn chuyển khoản rút tiền. Các mốc trong bản đề xuất vận hành nội bộ chưa phải cam kết với người dùng. Hãy theo dõi trạng thái của từng yêu cầu và phản hồi của nhân viên trong ứng dụng.

## Nhân viên trả lời khiếu nại trong bao lâu?

PTutor hiện chưa công bố thời hạn cố định để nhân viên trả lời hoặc giải quyết khiếu nại. Mốc **7 ngày sau khi hợp đồng kết thúc** là hạn **gửi** khiếu nại hợp đồng, không phải hạn nhân viên xử lý. Người gửi theo dõi trạng thái và phản hồi trong mục **Khiếu nại**; nếu cần hỏi tiến độ, gửi **Yêu cầu hỗ trợ** kèm mã khiếu nại.
