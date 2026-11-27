---
id: tro-ly-ptutor-su-dung
title: Cách hỏi Trợ lý PTutor và chuyển câu hỏi cho nhân viên
type: guide
audience: [STUDENT, TUTOR]
features: [account]
routes: [/student/support-requests, /tutor/support-requests]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: AssistantWidget, AssistantPanel, useAssistant, supportSource, FeedbackButtons"
  - "backend: AssistantController POST /api/v1/assistant/messages và /messages/{id}/feedback"
error_codes: [INVALID_MESSAGE, CONVERSATION_BUSY]
keywords: [trợ lý PTutor, chatbot, hỏi trợ lý, câu hỏi, trích dẫn, đánh giá câu trả lời, hỏi nhân viên, tro ly, hoi tro ly, chatbot khong tra loi]
todo: []
---

# Cách hỏi Trợ lý PTutor và chuyển câu hỏi cho nhân viên

## Mở và hỏi trợ lý

Học viên hoặc gia sư đã đăng nhập bấm nút **Mở trợ lý PTutor** ở góc màn hình. Chọn một câu hỏi gợi ý hoặc nhập câu hỏi về quy định, thao tác và thông tin của chính tài khoản mình, tối đa **1.000 ký tự**, rồi bấm **Gửi**. Bấm **Cuộc trò chuyện mới** để đổi chủ đề. Tải lại trang trong cùng tab sẽ khôi phục các tin của cuộc trò chuyện còn mở.

Trợ lý có thể kèm trích dẫn tài liệu và nút mở đúng trang chức năng. Hãy kiểm tra trạng thái hiện trên trang trước khi thanh toán, ký hợp đồng hoặc gửi yêu cầu. Trợ lý không thực hiện các thao tác đó thay bạn.

## Khi câu trả lời chưa đủ

Nếu trợ lý không có thông tin đáng tin cậy, chỉ trả lời được một phần hoặc bạn bấm **👎**, có thể chọn **Gửi câu hỏi cho nhân viên** ngay dưới câu trả lời. Bạn cũng có thể mở menu **⋯** → **Hỏi nhân viên hỗ trợ**. Kiểm tra và sửa câu hỏi, chọn có đính kèm đoạn hội thoại hay không, rồi tự bấm **Gửi**. Theo dõi kết quả tại **Yêu cầu hỗ trợ của tôi**. Trợ lý không tự tạo yêu cầu thay bạn.

Nút chuyển nhân viên không hiện cho câu hỏi ngoài phạm vi PTutor. Các vấn đề tranh chấp hợp đồng cần dùng mục **Khiếu nại**; câu hỏi cách dùng nền tảng dùng **Yêu cầu hỗ trợ**.

## Lỗi thường gặp

- **Câu hỏi cần từ 1 đến 1.000 ký tự**: rút ngắn câu hỏi hoặc chia thành nhiều câu.
- **Trợ lý đang trả lời câu trước**: chờ phản hồi rồi gửi câu tiếp theo.
- **Bạn gửi hơi nhanh**: thử lại sau ít phút.
- **Trợ lý tạm thời không khả dụng** hoặc phản hồi quá lâu: thử lại; nếu vẫn cần giúp đỡ, mở **Hỏi nhân viên hỗ trợ**.

Không nhập mật khẩu, OTP, toàn bộ số CCCD hoặc số tài khoản ngân hàng vào ô trò chuyện.
