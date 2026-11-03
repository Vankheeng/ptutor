---
id: ky-hoc-phi-thanh-toan
title: Cách đóng học phí bằng VNPay hoặc ví
type: guide
audience: [STUDENT]
features: [payment]
routes: [/student/contracts, /student/contracts/:contractId, /student/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/student/StudentContractDetailPage.tsx (pay -> services/studentApi payInstallment dùng đường VNPay); components/student/StudentPaymentInstallments.tsx (Các kỳ thanh toán, Ngày đến hạn, Số tiền, Trạng thái, Thao tác, Thanh toán)"
  - "backend: PaymentController POST /students/me/contracts/{contractId}/payment-installments/{installmentId}/payments/vnpay | /wallet"
  - "backend: PaymentService.createTuitionPayment, payTuitionWithWallet (hủy link VNPay đang chờ khi trả bằng ví)"
error_codes: [CONTRACT_NOT_ACTIVE, PAYMENT_INSTALLMENT_NOT_PAYABLE, PAYMENT_INSTALLMENT_NOT_READY, INSUFFICIENT_WALLET_BALANCE]
keywords: [đóng học phí, thanh toán học phí, trả tiền học, thanh toán bằng ví, dong hoc phi, thanh toan hoc phi, tra tien hoc, vnpay]
todo:
  - "Khi nào giao diện có lựa chọn đóng học phí bằng số dư ví?"
---

# Cách đóng học phí bằng VNPay hoặc ví

## Đóng học phí qua VNPay

1. Vào **Hợp đồng**, bấm **Xem hợp đồng** ở hợp đồng đang hoạt động.
2. Kéo xuống mục **Các kỳ thanh toán**. Bảng ghi số thứ tự kỳ, **Ngày đến hạn**, **Số tiền**, **Trạng thái**.
3. Ở kỳ đang chờ, bấm **Thanh toán**.
4. PTutor chuyển bạn sang trang VNPay. Hoàn tất thanh toán trong vòng 15 phút.

Khi VNPay báo thanh toán thành công, kỳ đó chuyển sang "Đã thanh toán" và bạn nhận thông báo "Payment successful".

## Nếu chưa thanh toán xong trong 15 phút

Link VNPay có hiệu lực 15 phút. Nếu bạn bấm **Thanh toán** lại trong thời gian này, PTutor mở lại đúng link cũ. Quá 15 phút, PTutor tạo link mới. Kỳ học phí vẫn ở trạng thái chờ cho tới khi thanh toán thành công.

## Đóng học phí bằng số dư ví

Hệ thống hỗ trợ đóng học phí bằng số dư ví: tiền được trừ ngay khỏi ví và kỳ chuyển sang "Đã thanh toán". Nếu bạn đã mở link VNPay cho kỳ đó, link đó bị hủy để không thu hai lần. Hiện giao diện chưa có nút đóng học phí bằng ví; bạn đóng qua VNPay.

## Khi nút Thanh toán báo lỗi

- Hợp đồng không còn "Đang hoạt động" thì không đóng được.
- Với hợp đồng **Mỗi buổi**, kỳ chưa gắn buổi học thì chưa đóng được. Trang báo câu tiếng Anh "This lesson installment is not scheduled yet". Bạn chờ gia sư lên lịch buổi học.
- Kỳ đã thanh toán thì không đóng lại được.
