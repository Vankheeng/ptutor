---
id: hoan-tien-quy-dinh
title: "Quy định hoàn tiền: điều kiện, gia sư phản hồi trong 48 giờ, mức hoàn"
type: policy
audience: [STUDENT]
features: [refund]
routes: [/student/contracts/:contractId/lessons, /student/contracts/:contractId/refund-request, /student/refund-requests]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: RefundRequestService.create (hợp đồng đã ký và đã đóng học phí; chỉ buổi PENDING_CONFIRMATION; một yêu cầu mở mỗi hợp đồng; tối đa 50 buổi, 5 minh chứng, mô tả 2000 ký tự)"
  - "backend: RefundPolicy (app.refund.rates.tutor-absent mặc định 100, app.refund.rates.student-absent mặc định 80, app.refund.tutor-response-hours mặc định 48)"
  - "backend: RefundCalculator.calculate (đơn giá = tổng các kỳ không bị huỷ / tổng số buổi, làm tròn đồng; hoàn tối đa = học phí đã đóng trừ phần đã hoàn trước)"
  - "backend: RefundAssessor; AdminRefundRequestService.approve/reject (không duyệt khi còn trong hạn gia sư phản hồi hoặc hợp đồng có khiếu nại mở)"
  - "backend: LessonService (buổi trong yêu cầu mở bị giữ lại)"
error_codes: [CONTRACT_NOT_REFUNDABLE, REFUND_REQUEST_ALREADY_OPEN, LESSON_NOT_DISPUTABLE, INVALID_CLAIMED_OUTCOME, DUPLICATE_DISPUTED_LESSON]
keywords: [hoàn tiền, hoàn học phí, gia sư vắng, học viên nghỉ, mức hoàn, 100%, 80%, hoan tien, hoan hoc phi, gia su vang, hoc vien nghi, muc hoan, refund]
todo:
  - "Vì sao học viên tự nghỉ vẫn được hoàn 80% và gia sư nhận 20%? (để giải thích khi người dùng hỏi)"
  - "Muốn hoàn học phí cho buổi đã qua hạn xác nhận 3 ngày hoặc đã xác nhận nhầm thì làm thế nào?"
  - "Tiền hoàn vào ví có rút về ngân hàng được không, khi nào?"
---

# Quy định hoàn tiền: điều kiện, gia sư phản hồi trong 48 giờ, mức hoàn

## Khi nào học viên được yêu cầu hoàn tiền

Học viên yêu cầu hoàn tiền cho những buổi học gia sư đã đánh dấu đã dạy nhưng thực tế không diễn ra. Điều kiện:

- Hợp đồng đã ký và đã có ít nhất một khoản học phí được đóng.
- Buổi học đang ở trạng thái "Chờ xác nhận", tức trong vòng 3 ngày kể từ lúc gia sư đánh dấu đã dạy. Buổi đã xác nhận, đã tự hoàn tất hoặc đã hủy thì không yêu cầu được.
- Hợp đồng chưa có yêu cầu hoàn tiền nào đang chờ hoặc đang xử lý.

Mỗi yêu cầu chọn tối đa 50 buổi, mỗi buổi chọn một nguyên nhân: **Gia sư không dạy** hoặc **Tôi nghỉ buổi này**. Mô tả tối đa 2.000 ký tự, kèm tối đa 5 đường dẫn minh chứng.

## Gia sư phản hồi trong 48 giờ

Khi học viên gửi yêu cầu, gia sư nhận thông báo và có 48 giờ kể từ lúc học viên gửi để phản hồi. Gia sư trả lời từng buổi: đã dạy, không dạy, hoặc học viên nghỉ. Gia sư chỉ phản hồi được một lần. Trong 48 giờ này, kế toán chưa duyệt được yêu cầu trừ khi gia sư đã phản hồi.

## Kế toán kết luận yêu cầu hoàn tiền

Kế toán PTutor tiếp nhận yêu cầu, xem mô tả, minh chứng và phản hồi của gia sư, rồi kết luận từng buổi: gia sư đã dạy, gia sư vắng, hoặc học viên nghỉ. Kế toán chưa kết luận được khi hợp đồng đang có khiếu nại chưa xong. Kế toán cũng có thể từ chối cả yêu cầu, kèm lý do.

## Mức hoàn tiền theo từng buổi

Mức hoàn tính theo đơn giá một buổi. Đơn giá bằng tổng tiền các kỳ học phí của hợp đồng chia cho tổng số buổi, làm tròn tới đồng.

- Buổi kết luận **gia sư vắng**: học viên được hoàn 100% đơn giá.
- Buổi kết luận **học viên nghỉ**: học viên được hoàn 80% đơn giá, gia sư nhận 20% còn lại vào ví.
- Buổi kết luận **gia sư đã dạy**: không hoàn, buổi chuyển sang "Đã học".

Tổng tiền hoàn không vượt quá số học phí học viên đã đóng của hợp đồng, trừ đi các khoản đã hoàn trước đó. Tiền hoàn được cộng vào ví PTutor của học viên.

## Ví dụ tính tiền hoàn

Hợp đồng **Mỗi buổi**, giá 200.000đ một buổi, 10 buổi. Đơn giá là 200.000đ.

- Một buổi gia sư vắng: học viên được hoàn 200.000đ.
- Một buổi học viên nghỉ: học viên được hoàn 160.000đ, gia sư nhận 40.000đ.
- Yêu cầu có cả hai buổi trên: học viên được hoàn 360.000đ, gia sư nhận 40.000đ.

Hợp đồng **Hàng tháng**, hai kỳ mỗi kỳ 1.600.000đ, tổng 16 buổi. Đơn giá là 3.200.000đ chia 16, bằng 200.000đ. Mức hoàn tính như ví dụ trên. Nếu học viên mới đóng 100.000đ thì tiền hoàn tối đa là 100.000đ.

## Buổi học trong lúc chờ kết luận

Trong lúc yêu cầu hoàn tiền chưa có kết luận, các buổi trong yêu cầu bị giữ lại: học viên không xác nhận được và buổi không tự hoàn tất sau 3 ngày.
