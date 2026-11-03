---
id: trang-thai-rut-tien
title: Giải nghĩa trạng thái yêu cầu rút tiền
type: glossary
audience: [STUDENT, TUTOR]
features: [withdrawal]
routes: [/student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: WithdrawalRequestStatus (PENDING, PROCESSING, CANCELLED, REJECTED, COMPLETED)"
  - "backend: WithdrawalService (chỉ đặt PENDING khi tạo và CANCELLED khi người dùng hủy; không có thao tác đặt PROCESSING, REJECTED, COMPLETED)"
  - "frontend: chưa có giao diện rút tiền nên chưa có nhãn"
error_codes: [WITHDRAWAL_NOT_CANCELLABLE]
keywords: [trạng thái rút tiền, chờ xử lý, đang xử lý, đã chuyển tiền, bị từ chối, trang thai rut tien, cho xu ly, dang xu ly, da chuyen tien]
todo:
  - "Nhãn trạng thái rút tiền do người soạn đặt vì chưa có giao diện: Chờ xử lý, Đang xử lý, Đã hủy, Bị từ chối, Hoàn tất. Admin xác nhận."
  - "Khi yêu cầu rút bị từ chối, tiền tạm giữ có được trả lại ví không và trong bao lâu?"
---

# Giải nghĩa trạng thái yêu cầu rút tiền

## Chờ xử lý

Yêu cầu rút tiền "Chờ xử lý" vừa được gửi. Số tiền rút đã bị tạm giữ, không dùng được. Bước tiếp theo: PTutor xử lý yêu cầu. Trong lúc chờ, bạn có thể hủy để nhận lại số tiền tạm giữ.

## Đang xử lý

Yêu cầu rút tiền "Đang xử lý" đã được PTutor tiếp nhận. Bạn không hủy được nữa. Bạn chờ PTutor chuyển tiền.

## Hoàn tất

Yêu cầu rút tiền "Hoàn tất" nghĩa là PTutor đã chuyển tiền tới tài khoản ngân hàng của bạn.

## Bị từ chối

Yêu cầu rút tiền "Bị từ chối" không được PTutor chấp nhận.

## Đã hủy

Yêu cầu rút tiền "Đã hủy" do bạn hủy khi còn chờ xử lý. Số tiền tạm giữ đã được trả lại vào số dư dùng được.

## Lưu ý về các trạng thái rút tiền

Hiện hệ thống mới dùng hai trạng thái "Chờ xử lý" và "Đã hủy". Các trạng thái "Đang xử lý", "Hoàn tất" và "Bị từ chối" chưa được dùng vì PTutor chưa có quy trình xử lý yêu cầu rút tiền trên hệ thống.
