---
id: trang-thai-giao-dich-vi
title: Giải nghĩa loại và trạng thái giao dịch ví
type: glossary
audience: [STUDENT, TUTOR]
features: [wallet]
routes: [/student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: WalletTransactionType (CREDIT, DEBIT); WalletTransactionStatus (PENDING, COMPLETED, FAILED, CANCELLED)"
  - "enum: WalletTransactionPurpose (TOP_UP, TUTOR_EARNING, CONTRACT_REFUND, TEACHING_REQUEST_REFUND, STUDYING_REQUEST_REFUND, WITHDRAWAL_REVERSAL, TUITION_PAYMENT, STUDYING_REQUEST_FEE, TEACHING_REQUEST_FEE, WITHDRAWAL)"
  - "backend: WalletService.credit/debit/reserveForWithdrawal/reverseWithdrawal"
  - "frontend: giao diện không hiển thị nhãn loại và trạng thái giao dịch ví"
error_codes: []
keywords: [loại giao dịch, tiền vào, tiền ra, giao dịch đang chờ, hoàn tất, loai giao dich, tien vao, tien ra, giao dich dang cho]
todo:
  - "Giao diện chưa có nhãn cho loại và trạng thái giao dịch ví. Admin xác nhận các nhãn trong tài liệu: Tiền vào, Tiền ra, Đang chờ, Hoàn tất, Thất bại, Đã hủy."
---

# Giải nghĩa loại và trạng thái giao dịch ví

## Tiền vào và tiền ra

Mỗi giao dịch ví thuộc một trong hai loại:

- **Tiền vào**: số dư ví tăng. Gồm nạp tiền, hoàn phí đăng tin bị từ chối, hoàn học phí, phần tiền gia sư nhận cho buổi học viên nghỉ, và tiền rút được trả lại khi hủy yêu cầu rút.
- **Tiền ra**: số dư ví giảm. Gồm trả phí đăng tin bằng ví, đóng học phí bằng ví và rút tiền.

## Đang chờ

Giao dịch ví "Đang chờ" chưa hoàn tất. Hiện chỉ giao dịch rút tiền có trạng thái này: số tiền rút đã bị trừ khỏi số dư dùng được và được tạm giữ, chờ PTutor xử lý. Bạn có thể hủy yêu cầu rút khi còn đang chờ.

## Hoàn tất

Giao dịch ví "Hoàn tất" đã được ghi vào số dư. Các giao dịch nạp tiền, hoàn tiền, trả phí và đóng học phí bằng ví đều hoàn tất ngay.

## Thất bại và đã hủy

Giao dịch ví "Thất bại" hoặc "Đã hủy" không làm thay đổi số dư. Khi bạn hủy yêu cầu rút tiền, PTutor ghi thêm một giao dịch tiền vào để trả lại khoản đã tạm giữ.
