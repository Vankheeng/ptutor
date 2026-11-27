---
id: vi-giao-dich
title: Xem số dư và lịch sử giao dịch ví
type: guide
audience: [STUDENT, TUTOR]
features: [wallet]
routes: [/student/wallet, /student/wallet/transactions, /tutor/wallet, /tutor/wallet/transactions]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: WalletTransactionsPage và TransactionTable (bộ lọc, phân trang 10/20/50)"
  - "frontend: pages/common-user/WalletPage.tsx (20 giao dịch gần nhất), components/WalletBalanceCard.tsx (Available balance), components/FinancialTransactionList.tsx (mô tả, nguồn, phương thức, thời gian, số tiền)"
  - "backend: WalletController GET /api/v1/users/me/wallet; PaymentController GET /api/v1/users/me/transactions -> PaymentService.findMyTransactions"
  - "backend: Wallet.pendingBalance (tiền tạm giữ khi rút)"
error_codes: [WALLET_NOT_FOUND, INVALID_TRANSACTION_DATE_RANGE]
keywords: [số dư ví, lịch sử giao dịch, xem giao dịch, tiền trong ví, so du vi, lich su giao dich, xem giao dich, transactions]
todo: []
---

# Xem số dư và lịch sử giao dịch ví

## Xem số dư ví

1. Mở trang ví: học viên chọn **Wallet & Payments**, gia sư chọn **Ví tiền**.
2. Mục **Available balance** là số dư bạn đang dùng được.

Khi bạn có yêu cầu rút tiền đang chờ, số tiền rút được tạm giữ và không nằm trong số dư dùng được.

## Xem lịch sử giao dịch ví

Trang ví có liên kết tới **Lịch sử giao dịch**. Trang lịch sử có bộ lọc ngày, loại giao dịch, tìm kiếm và phân trang với 10, 20 hoặc 50 dòng mỗi trang; bạn có thể chuyển trang để xem giao dịch cũ hơn. Mỗi dòng ghi mô tả, nguồn, phương thức, thời gian và số tiền. Khi chưa có giao dịch, trang hiện "No financial activity".

## Mô tả giao dịch thường gặp

Mô tả giao dịch hiện bằng tiếng Anh:

- "Wallet top-up via VNPay": nạp tiền vào ví qua VNPay.
- "Studying request publication fee" hoặc "Teaching request publication fee": phí đăng tin trả bằng ví.
- "Contract tuition installment payment": đóng kỳ học phí bằng ví.
- "Refund for rejected studying request" hoặc "… teaching request": hoàn phí đăng tin bị từ chối.
- "Refund for lessons that did not take place": hoàn học phí cho buổi không diễn ra.
- "Share of lessons the student skipped": phần tiền gia sư nhận cho buổi học viên nghỉ.
- "Withdrawal request": yêu cầu rút tiền.
- "Withdrawal request cancelled": hủy yêu cầu rút, tiền được trả lại ví.
