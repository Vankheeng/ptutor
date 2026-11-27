---
id: trang-thai-rut-tien
title: Giải nghĩa trạng thái yêu cầu rút tiền
type: glossary
audience: [STUDENT, TUTOR]
features: [withdrawal]
routes: [/student/wallet, /tutor/wallet]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: AdminWithdrawalService.approve/reject/complete; WithdrawalService.cancel"
  - "enum: WithdrawalRequestStatus (PENDING, PROCESSING, CANCELLED, REJECTED, COMPLETED)"
  - "backend: WithdrawalService (chỉ đặt PENDING khi tạo và CANCELLED khi người dùng hủy; không có thao tác đặt PROCESSING, REJECTED, COMPLETED)"
  - "frontend: WalletPage, WalletTransactionsPage, transactionStatusLabels"
error_codes: [WITHDRAWAL_NOT_CANCELLABLE]
keywords: [trạng thái rút tiền, chờ xử lý, đang xử lý, đã chuyển tiền, bị từ chối, trang thai rut tien, cho xu ly, dang xu ly, da chuyen tien]
todo: []
---

# Giải nghĩa trạng thái yêu cầu rút tiền

## Chờ xử lý

Yêu cầu rút tiền "Chờ xử lý" vừa được gửi. Số tiền rút đã bị tạm giữ, không dùng được. Bước tiếp theo: PTutor xử lý yêu cầu. Trong lúc chờ, bạn có thể hủy để nhận lại số tiền tạm giữ.

## Đang xử lý

Yêu cầu rút tiền "Đang xử lý" đã được PTutor tiếp nhận. Bạn không hủy được nữa. Bạn chờ PTutor chuyển tiền.

## Hoàn tất

Yêu cầu rút tiền "Hoàn tất" nghĩa là PTutor đã chuyển tiền tới tài khoản ngân hàng của bạn.

## Bị từ chối

Yêu cầu rút tiền "Bị từ chối" không được PTutor chấp nhận, kèm lý do. Số tiền tạm giữ được trả lại ví ngay khi nhân viên từ chối.

## Đã hủy

Yêu cầu rút tiền "Đã hủy" do bạn hủy khi còn chờ xử lý. Số tiền tạm giữ đã được trả lại vào số dư dùng được.

## Lưu ý về các trạng thái rút tiền

Cả năm trạng thái trên đều được sử dụng trong luồng rút tiền hiện tại. Khi nhân viên từ chối, số tiền tạm giữ được trả lại ví ngay trong cùng giao dịch; khi duyệt, yêu cầu chuyển sang **Đang xử lý** nhưng tiền vẫn chưa được coi là đã chuyển cho tới khi ghi nhận **Hoàn tất**.

Nhãn trạng thái hiển thị trong giao diện rút tiền và lịch sử giao dịch ví; người dùng xem lý do nếu yêu cầu bị từ chối.
