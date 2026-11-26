---
id: trang-thai-yeu-cau-ho-tro
title: Gửi câu hỏi cho nhân viên và trạng thái yêu cầu hỗ trợ
type: glossary
audience: [STUDENT, TUTOR]
features: [account]
routes: [/student/support-requests, /tutor/support-requests]
status: draft
version: 1
updated: 2026-11-04
source:
  - "enum: SupportRequestStatus (OPEN, IN_PROGRESS, ANSWERED, CLOSED, CANCELLED)"
  - "backend: SupportRequestService (giới hạn 5 yêu cầu chờ xử lý, 10 yêu cầu mỗi ngày, tự đóng sau 7 ngày, xoá đoạn hội thoại 365 ngày sau khi đóng)"
  - "frontend: components/support/supportFormat.ts supportStatusMeta (Chờ xử lý, Đang xử lý, Đã trả lời, Đã đóng, Đã hủy)"
  - "thiết kế: docs/support-chatbot/14-yeu-cau-ho-tro.md"
error_codes: [SUPPORT_REQUEST_OPEN_LIMIT, SUPPORT_REQUEST_DAILY_LIMIT, INVALID_SUPPORT_REQUEST_STATUS_TRANSITION]
keywords: [yêu cầu hỗ trợ, gửi câu hỏi cho nhân viên, hỏi nhân viên, chờ xử lý, đã trả lời, đã giải quyết, yeu cau ho tro, hoi nhan vien, gui cau hoi cho nhan vien, HT-]
todo: []
---

# Gửi câu hỏi cho nhân viên và trạng thái yêu cầu hỗ trợ

## Cách gửi câu hỏi cho nhân viên

Khi trợ lý chưa trả lời được hoặc chỉ trả lời một phần, bấm "Gửi câu hỏi cho nhân viên" ngay dưới câu trả lời. Có thể mở cùng hộp thoại từ menu "⋯" của trợ lý, mục "Hỏi nhân viên hỗ trợ". Câu hỏi được điền sẵn và sửa được, tối đa 2.000 ký tự. Bấm "Gửi" để tạo yêu cầu; mỗi yêu cầu có một mã dạng HT-000123.

Trợ lý không tự gửi yêu cầu thay bạn. Bạn phải tự bấm "Gửi" trong hộp thoại.

## Đính kèm đoạn hội thoại

Ô "Đính kèm đoạn hội thoại" mặc định không được chọn. Khi chọn, nhân viên thấy tối đa 6 lượt hỏi đáp gần nhất với trợ lý, đã che số điện thoại, email và số tài khoản. Đoạn đính kèm được lưu cùng yêu cầu, kể cả khi bạn xóa cuộc trò chuyện, và được xóa sau 365 ngày kể từ khi yêu cầu đóng.

## Giới hạn gửi

Mỗi người có tối đa 5 yêu cầu đang "Chờ xử lý" cùng lúc và gửi tối đa 10 yêu cầu trong 24 giờ. Khi chạm giới hạn, chờ nhân viên xử lý bớt hoặc hủy yêu cầu không còn cần.

## Chờ xử lý

Yêu cầu "Chờ xử lý" vừa được gửi, chưa có nhân viên nhận. Người gửi còn hủy được. Bước tiếp theo: nhân viên chăm sóc khách hàng nhận xử lý.

## Đang xử lý

Yêu cầu "Đang xử lý" đã có nhân viên chăm sóc khách hàng nhận. Người gửi không hủy được nữa. Nhân viên có thể trả yêu cầu về "Chờ xử lý" để người khác nhận.

## Đã trả lời

Yêu cầu "Đã trả lời" đã có câu trả lời của nhân viên; bạn nhận thông báo khi có câu trả lời. Xem câu trả lời ở trang "Yêu cầu hỗ trợ", rồi bấm "Đã giải quyết" nếu đã ổn. Nếu bạn không bấm, yêu cầu tự đóng sau 7 ngày.

## Đã đóng

Yêu cầu "Đã đóng" đã được người gửi xác nhận giải quyết hoặc tự đóng sau 7 ngày kể từ khi trả lời. Đây là trạng thái cuối. Cần hỏi thêm thì gửi yêu cầu mới.

## Đã hủy

Yêu cầu "Đã hủy" do người gửi hủy khi còn "Chờ xử lý". Đây là trạng thái cuối.

## Khác với khiếu nại

Yêu cầu hỗ trợ dùng cho câu hỏi về cách dùng nền tảng. Tranh chấp với gia sư hoặc học viên về một hợp đồng thì gửi khiếu nại, vì khiếu nại có quy trình bằng chứng và kết luận riêng.
