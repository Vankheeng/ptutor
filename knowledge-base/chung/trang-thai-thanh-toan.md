---
id: trang-thai-thanh-toan
title: Giải nghĩa trạng thái thanh toán
type: glossary
audience: [STUDENT, TUTOR]
features: [payment]
routes: [/student/wallet, /tutor/wallet]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: PaymentStatus (PENDING, PAID, FAILED, CANCELLED, REFUNDED); PaymentType"
  - "backend: PaymentService (PENDING khi tạo link; PAID khi VNPay xác nhận; FAILED khi VNPay báo lỗi hoặc link hết hạn; CANCELLED khi trả bằng ví thay cho link đang chờ); AdminRefundRequestService (REFUNDED khi đã hoàn đủ)"
  - "frontend: pages/admin/complaintFormat.ts paymentStatusMeta (Đang chờ, Đã thanh toán, Thất bại, Đã huỷ, Đã hoàn) — giao diện người dùng không hiển thị trạng thái thanh toán"
error_codes: []
keywords: [trạng thái thanh toán, thanh toán thất bại, đang chờ thanh toán, đã hoàn, trang thai thanh toan, that bai, dang cho, da hoan]
todo:
  - "Giao diện học viên, gia sư chưa hiển thị trạng thái của từng giao dịch thanh toán; nhãn dùng trong tài liệu lấy từ trang quản trị. Admin xác nhận."
---

# Giải nghĩa trạng thái thanh toán

Trạng thái thanh toán áp dụng cho từng lần trả tiền: nạp ví, đóng kỳ học phí, trả phí đăng tin.

## Đang chờ

Thanh toán "Đang chờ" đã tạo link VNPay nhưng VNPay chưa xác nhận kết quả. Bước tiếp theo: bạn hoàn tất trên trang VNPay trong vòng 15 phút. Quá 15 phút mà chưa xong, lần bấm thanh toán tiếp theo sẽ tạo link mới.

## Đã thanh toán

Thanh toán "Đã thanh toán" đã thành công. Khoản tương ứng đã được cập nhật: ví đã cộng tiền, kỳ học phí đã đóng, hoặc tin đã được đăng. Không ai cần làm gì thêm.

## Thất bại

Thanh toán "Thất bại" không thành công: bạn hủy trên VNPay, ngân hàng từ chối, hoặc link đã quá 15 phút. Không có tiền nào được ghi nhận. Bước tiếp theo: bạn thanh toán lại.

## Đã hủy

Thanh toán "Đã hủy" là link VNPay bị bỏ vì bạn đã trả khoản đó bằng số dư ví. Không cần làm gì thêm.

## Đã hoàn

Thanh toán "Đã hoàn" là khoản học phí đã được hoàn lại toàn bộ vào ví của học viên sau khi kế toán duyệt yêu cầu hoàn tiền.
