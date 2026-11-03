---
id: hop-dong-ky-tu-choi
title: "Ký, từ chối hoặc hủy hợp đồng"
type: policy
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: ContractController PATCH /{contractId}/sign, /{contractId}/reject"
  - "backend: ContractService.sign (ensurePending, ensureCounterparty, ensureNotExpired), reject"
  - "backend: ContractPaymentInstallmentService.ensureForActiveContract (tạo kỳ thanh toán khi hợp đồng có hiệu lực)"
  - "enum: NotificationEventType CONTRACT_SIGNATURE_REQUIRED, CONTRACT_SIGNED, CONTRACT_REJECTED"
  - "frontend: components/student/StudentContractActionBar.tsx"
  - "backend: ContractController PATCH /api/v1/users/me/contracts/{contractId}/cancel -> ContractService.cancel (chỉ PENDING, chỉ người tạo)"
  - "enum: CancelContractRequestStatus (PENDING, APPROVED, REJECTED) — không có endpoint nào dùng"
  - "enum: NotificationEventType CONTRACT_CANCELLED"
  - "frontend: components/student/StudentContractActionBar.tsx (Hủy hợp đồng khi PENDING)"
error_codes: [CONTRACT_COUNTERPARTY_REQUIRED, CONTRACT_EXPIRED, INVALID_CONTRACT_STATUS_TRANSITION, CONTRACT_CREATOR_REQUIRED]
keywords: [ký hợp đồng, từ chối hợp đồng, xác nhận hợp đồng, ky hop dong, tu choi hop dong, sign contract, hủy hợp đồng, chấm dứt hợp đồng, dừng học, ngừng học giữa chừng, huy hop dong, cham dut hop dong, dung hoc, cancel contract]
todo:
  - "Hợp đồng chờ ký có thời hạn ký riêng không, hay chỉ tự hủy khi tới ngày kết thúc như code hiện tại?"
  - "Học viên hoặc gia sư muốn chấm dứt hợp đồng đang hoạt động thì làm thế nào, liên hệ ai?"
  - "Khi chấm dứt hợp đồng đang hoạt động, học phí đã đóng cho các buổi chưa học được xử lý thế nào?"
---

# Ký, từ chối hoặc hủy hợp đồng

## Ai ký hợp đồng

Bên nhận hợp đồng, tức bên không soạn hợp đồng, là người ký. Người soạn không tự ký hợp đồng của mình. Khi có hợp đồng chờ bạn ký, bạn nhận thông báo "Contract signature required".

## Khi bạn ký hợp đồng

Khi ký hợp đồng:

- Hợp đồng chuyển từ "Đang chờ" sang "Đang hoạt động".
- PTutor tạo các kỳ thanh toán học phí theo chu kỳ thanh toán của hợp đồng.
- Người soạn nhận thông báo "Contract signed".

Hợp đồng đã ký không sửa và không tự hủy được trên PTutor.

## Khi bạn từ chối hợp đồng

Khi từ chối, hợp đồng chuyển sang "Đã hủy" và người soạn nhận thông báo "Contract rejected". Người soạn có thể soạn hợp đồng mới từ cùng đề nghị hoặc đơn đã chấp nhận.

## Khi nào không ký được hợp đồng

Bạn không ký được hợp đồng khi:

- Hợp đồng không còn ở trạng thái "Đang chờ".
- Bạn là người soạn hợp đồng.
- Đã tới hoặc đã qua ngày kết thúc của hợp đồng. Lúc 0 giờ ngày kết thúc, hợp đồng chưa ký tự chuyển sang "Đã hủy".

## Hủy hợp đồng chưa ký

Bạn chỉ hủy được hợp đồng khi hợp đồng đang ở trạng thái "Đang chờ" và bạn là người soạn hợp đồng. Các bước:

1. Vào **Hợp đồng**, bấm **Xem hợp đồng** ở hợp đồng cần hủy.
2. Bấm **Hủy hợp đồng**.

Hợp đồng chuyển sang "Đã hủy", trang báo "Đã hủy hợp đồng." và bên kia nhận thông báo "Contract cancelled".

Nếu bạn là bên nhận và không đồng ý với hợp đồng, bạn dùng **Từ chối hợp đồng** thay vì hủy.

## Hủy hợp đồng đang hoạt động

Hiện PTutor chưa hỗ trợ hủy hay chấm dứt hợp đồng đã ký (đang hoạt động). Không bên nào tự hủy được trên giao diện. Hợp đồng đang hoạt động kết thúc khi tới ngày kết thúc.

Nếu một buổi học không diễn ra, học viên có thể gửi yêu cầu hoàn tiền cho buổi đó. Nếu có vi phạm, bạn có thể gửi khiếu nại về hợp đồng.
