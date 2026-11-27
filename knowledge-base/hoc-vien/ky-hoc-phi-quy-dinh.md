---
id: ky-hoc-phi-quy-dinh
title: "Kỳ học phí: chu kỳ thanh toán, nhắc đóng tiền, trạng thái"
type: policy
audience: [STUDENT]
features: [payment]
routes: [/student/contracts/:contractId, /student/notifications]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: LessonPayoutService.release; frontend: StudentPaymentMethodDialog"
  - "backend: ContractPaymentInstallmentService.ensureForActiveContract (PACKAGE: 1 kỳ vào ngày bắt đầu; PER_LESSON: số kỳ = tổng số buổi, gắn khi tạo buổi; WEEKLY/MONTHLY: mỗi tuần/tháng từ ngày bắt đầu tới trước ngày kết thúc; số tiền mỗi kỳ = giá hợp đồng)"
  - "backend: PaymentInstallmentReminderService (cron app.notification.payment-reminder-cron mặc định 0 0 9 * * *, nhắc kỳ đến hạn sau 3 ngày)"
  - "backend: PaymentService.findMyInstallments/createTuitionPayment (chỉ hợp đồng ACTIVE)"
  - "enum: PaymentPeriod; PaymentInstallmentStatus; NotificationEventType PAYMENT_INSTALLMENT_DUE"
  - "frontend: components/student/StudentPaymentInstallments.tsx (Các kỳ thanh toán)"
  - "enum: PaymentInstallmentStatus (PENDING, PAID, CANCELLED)"
  - "frontend: components/student/StudentPaymentInstallments.tsx dùng StudentContractStatusBadge (Đang chờ, Đã thanh toán, Đã hủy)"
  - "backend: PaymentService.activatePaidPayment (PAID)"
error_codes: [CONTRACT_NOT_ACTIVE, PAYMENT_INSTALLMENT_NOT_PAYABLE, PAYMENT_INSTALLMENT_NOT_READY, INVALID_CONTRACT_PAYMENT_AMOUNT]
keywords: [kỳ học phí, đóng học phí, chu kỳ thanh toán, hạn đóng tiền, nhắc đóng tiền, ky hoc phi, dong hoc phi, chu ky thanh toan, han dong tien, nhac dong tien, trạng thái kỳ học phí, chưa thanh toán, đã thanh toán, kỳ thanh toán, trang thai ky hoc phi, chua thanh toan, da thanh toan]
todo: []
---

# Kỳ học phí: chu kỳ thanh toán, nhắc đóng tiền, trạng thái

## Kỳ học phí là gì

Kỳ học phí là một lần đóng tiền theo hợp đồng. PTutor tạo các kỳ học phí khi hợp đồng chuyển sang "Đang hoạt động". Hợp đồng đang chờ ký chưa có kỳ học phí. Số tiền mỗi kỳ bằng **Giá** ghi trong hợp đồng.

## Các kỳ học phí theo từng chu kỳ thanh toán

Số kỳ và hạn đóng phụ thuộc **Chu kỳ thanh toán** của hợp đồng:

- **Theo gói**: một kỳ duy nhất, hạn đóng là ngày bắt đầu hợp đồng.
- **Hàng tuần**: mỗi tuần một kỳ, kỳ đầu vào ngày bắt đầu, các kỳ sau cách nhau 7 ngày, cho tới trước ngày kết thúc.
- **Hàng tháng**: mỗi tháng một kỳ, kỳ đầu vào ngày bắt đầu, các kỳ sau cách nhau một tháng, cho tới trước ngày kết thúc.
- **Mỗi buổi**: số kỳ bằng tổng số buổi. Mỗi kỳ chỉ có hạn đóng và đóng được khi gia sư đã tạo buổi học tương ứng; hạn đóng là ngày của buổi học.

Ví dụ: hợp đồng **Hàng tháng**, giá 1.600.000đ, từ 01/11/2026 đến 01/01/2027 có hai kỳ, mỗi kỳ 1.600.000đ, hạn đóng 01/11/2026 và 01/12/2026.

## Nhắc đóng học phí

Mỗi ngày lúc 9 giờ sáng (giờ Việt Nam), PTutor gửi thông báo nhắc cho học viên có kỳ học phí chưa đóng đến hạn sau đúng 3 ngày. Ví dụ, kỳ có hạn 10/11 được nhắc lúc 9 giờ sáng ngày 07/11. Thông báo có tiêu đề "Tuition payment reminder". Mỗi kỳ chỉ được nhắc một lần.

## Cách đóng học phí

Học viên đóng từng kỳ trong mục **Các kỳ thanh toán** của trang chi tiết hợp đồng, chọn **VNPay** hoặc **Ví PTutor** trong hộp thoại thanh toán. Chỉ đóng được kỳ đang chờ của hợp đồng đang hoạt động. Mỗi kỳ chỉ thu một lần.

## Đóng học phí trễ hạn

Hệ thống hiện không tự phạt, không tự khóa hay hủy hợp đồng khi học viên đóng trễ. Kỳ trễ hạn vẫn ở trạng thái chờ và vẫn đóng được.

## Đang chờ

Kỳ học phí "Đang chờ" chưa được thanh toán. Bước tiếp theo: học viên bấm **Thanh toán** ở kỳ đó trong mục **Các kỳ thanh toán**. Với hợp đồng **Mỗi buổi**, kỳ chưa có ngày đến hạn là kỳ chưa gắn buổi học; học viên chờ gia sư lên lịch buổi học rồi mới đóng được.

## Đã thanh toán

Kỳ học phí "Đã thanh toán" đã được thu thành công qua VNPay hoặc bằng số dư ví. Không ai cần làm gì thêm. Nút **Thanh toán** không còn hiện ở kỳ này.

## Đã hủy

Kỳ học phí "Đã hủy" không còn phải đóng và không được tính vào giá trị hợp đồng khi tính hoàn tiền.

## Học phí đã đóng và tiền dạy

Tiền học phí được ghi nhận khi kỳ thanh toán thành công. Phần của từng buổi chỉ được cộng vào ví gia sư sau khi buổi được xác nhận hoặc tự hoàn tất và không còn yêu cầu hoàn tiền đang xử lý. Kỳ **Đã hủy** tồn tại trong mô hình dữ liệu nhưng mã nguồn hiện không có thao tác nghiệp vụ thông thường để chuyển một kỳ đang chờ sang trạng thái này; chatbot không nên hướng dẫn người dùng tự hủy kỳ.
