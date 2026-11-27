---
id: thu-nhap-gia-su
title: Gia sư nhận tiền dạy như thế nào
type: faq
audience: [TUTOR]
features: [wallet]
routes: [/tutor/wallet]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: LessonPayoutService.release; LessonService.confirmByStudent/completeExpiredPendingLessons"
  - "enum: WalletTransactionPurpose.TUTOR_EARNING"
  - "backend: AdminRefundRequestService.approve (chia tiền buổi học viên nghỉ)"
  - "backend: LessonService.confirmByStudent, completeExpiredPendingLessons (gọi LessonPayoutService.release)"
  - "backend: WithdrawalService, AdminWithdrawalService (duyệt, từ chối, hoàn tất yêu cầu rút)"
error_codes: []
keywords: [thu nhập gia sư, nhận tiền dạy, lương gia sư, khi nào được trả tiền, thu nhap gia su, nhan tien day, luong gia su, khi nao duoc tra tien]
todo: []
---

# Gia sư nhận tiền dạy như thế nào

## Học phí của buổi đã dạy vào ví khi nào?

Khi học viên xác nhận hoặc buổi tự hoàn tất sau 3 ngày, hệ thống ghi khoản **Thu nhập gia sư** vào ví gia sư trong cùng luồng xử lý, với điều kiện học phí tương ứng đã được thanh toán và khoản tiền không bị giữ vì yêu cầu hoàn tiền. Một buổi chỉ được cộng tiền một lần.

## Trường hợp nào gia sư được cộng tiền vào ví?

Ngoài tiền dạy cho buổi hoàn tất, khi kế toán kết luận một buổi là học viên nghỉ, học viên được hoàn 80% đơn giá buổi đó, còn gia sư nhận 20% vào ví. Giao dịch hiện trong ví với mô tả "Share of lessons the student skipped".

## Gia sư có rút tiền trong ví về ngân hàng được không?

Bạn có thể gửi yêu cầu rút tiền từ trang ví sau khi liên kết tài khoản ngân hàng và xác thực mã OTP. Nhân viên PTutor duyệt, chuyển khoản thủ công rồi xác nhận hoàn tất trên hệ thống. Xem tài liệu "Rút tiền và hủy yêu cầu rút tiền".

## Buổi học bị giữ lại có ảnh hưởng tới tiền dạy không?

Buổi học nằm trong yêu cầu hoàn tiền hoặc thuộc hợp đồng có khiếu nại chưa xong bị giữ lại để xem xét trước khi hoàn tất và giải ngân. Buổi được kết luận gia sư vắng sẽ bị hủy và học viên được hoàn tiền buổi đó.

## PTutor khấu trừ bao nhiêu?

Luồng trả tiền cho buổi hoàn tất hiện tính đơn giá buổi từ tổng các kỳ học phí không bị hủy chia cho số buổi trong hợp đồng; mã nguồn không khấu trừ tỷ lệ hoa hồng khi ghi khoản này vào ví gia sư. Nếu hợp đồng hoặc màn hình thanh toán hiển thị khoản phí khác, hãy kiểm tra điều khoản cụ thể trước khi ký. Không nên suy ra PTutor thu một tỷ lệ cố định từ tiền dạy.
