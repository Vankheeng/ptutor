---
id: thu-nhap-gia-su
title: Gia sư nhận tiền dạy như thế nào
type: faq
audience: [TUTOR]
features: [wallet]
routes: [/tutor/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: WalletTransactionPurpose.TUTOR_EARNING"
  - "backend: AdminRefundRequestService.approve (chỉ nơi duy nhất cộng tiền cho gia sư: phần của buổi học viên nghỉ)"
  - "backend: LessonService.confirmByStudent, completeExpiredPendingLessons (không cộng tiền cho gia sư)"
  - "backend: WithdrawalService (chưa có quy trình xử lý rút tiền)"
error_codes: []
keywords: [thu nhập gia sư, nhận tiền dạy, lương gia sư, khi nào được trả tiền, thu nhap gia su, nhan tien day, luong gia su, khi nao duoc tra tien]
todo:
  - "Gia sư nhận học phí của các buổi đã dạy khi nào, bằng cách nào (vào ví hay chuyển khoản), PTutor giữ lại bao nhiêu phần trăm?"
---

# Gia sư nhận tiền dạy như thế nào

## Học phí của buổi đã dạy có tự vào ví gia sư không?

Hiện chưa. Khi học viên xác nhận buổi học, hoặc buổi tự hoàn tất sau 3 ngày, hệ thống chưa tự cộng tiền dạy vào ví gia sư. Cách PTutor trả tiền dạy cho gia sư do PTutor quy định riêng và chưa được thực hiện trên hệ thống.

## Trường hợp nào gia sư được cộng tiền vào ví?

Hiện chỉ có một trường hợp: trong yêu cầu hoàn tiền, kế toán kết luận một buổi là học viên nghỉ. Khi đó học viên được hoàn 80% đơn giá buổi đó, còn gia sư nhận 20% vào ví. Giao dịch hiện trong ví với mô tả "Share of lessons the student skipped".

## Gia sư có rút tiền trong ví về ngân hàng được không?

Hệ thống có quy định gửi yêu cầu rút tiền về tài khoản ngân hàng đã khai báo, nhưng hiện chưa có giao diện rút tiền và chưa có quy trình xử lý yêu cầu rút. Xem tài liệu "Rút tiền và hủy yêu cầu rút tiền".

## Buổi học bị giữ lại có ảnh hưởng tới tiền dạy không?

Buổi học nằm trong yêu cầu hoàn tiền hoặc thuộc hợp đồng có khiếu nại chưa xong bị giữ lại, không được tính là đã học cho tới khi có kết luận. Buổi được kết luận gia sư vắng sẽ bị hủy và học viên được hoàn tiền buổi đó.
