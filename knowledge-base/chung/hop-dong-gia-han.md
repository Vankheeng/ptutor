---
id: hop-dong-gia-han
title: Gia hạn hợp đồng
type: policy
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts/:contractId, /student/contracts]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: ContractController POST /api/v1/users/me/contracts/{contractId}/renewals -> ContractService.renew"
  - "backend: ContractService.renew (chỉ ACTIVE, chưa tới ngày kết thúc, không có bản gia hạn khác chưa huỷ, ngày bắt đầu không trước ngày kết thúc cũ)"
  - "backend: ContractService.updateRenewalTerms"
  - "enum: NotificationEventType CONTRACT_RENEWAL_PROPOSED"
  - "frontend: components/student/StudentContractRenewalForm.tsx (Đề xuất gia hạn, Gửi đề xuất gia hạn)"
error_codes: [DUPLICATE_CONTRACT_RENEWAL, INVALID_CONTRACT_DATE_RANGE, CONTRACT_EXPIRED, INVALID_CONTRACT_STATUS_TRANSITION]
keywords: [gia hạn hợp đồng, học tiếp, kéo dài hợp đồng, đề xuất gia hạn, gia han hop dong, hoc tiep, keo dai hop dong, renew contract]
todo: []
---

# Gia hạn hợp đồng

## Gia hạn hợp đồng là gì

Gia hạn hợp đồng là tạo một hợp đồng mới nối tiếp hợp đồng đang hoạt động, giữ nguyên học viên, gia sư, môn học, lớp và hình thức dạy. Hợp đồng gia hạn có giá, chu kỳ thanh toán, lịch học và thời gian riêng.

Cả học viên và gia sư đều đề xuất gia hạn được. Người đề xuất là người soạn hợp đồng gia hạn; bên kia ký hoặc từ chối.

## Điều kiện gia hạn hợp đồng

Bạn đề xuất gia hạn được khi:

- Hợp đồng đang ở trạng thái "Đang hoạt động".
- Chưa tới ngày kết thúc của hợp đồng.
- Hợp đồng chưa có bản gia hạn nào đang chờ ký hoặc đã có hiệu lực. Bản gia hạn bị hủy hoặc bị từ chối thì không tính.
- Ngày bắt đầu của bản gia hạn không sớm hơn ngày kết thúc của hợp đồng hiện tại.

## Các bước đề xuất gia hạn (học viên)

1. Vào **Hợp đồng**, mở hợp đồng đang hoạt động bằng **Xem hợp đồng**.
2. Bấm **Đề xuất gia hạn**.
3. Điền **Giá**, **Chu kỳ thanh toán**, **Lịch học**, **Ngày bắt đầu**, **Ngày kết thúc**.
4. Bấm **Gửi đề xuất gia hạn**.

Trang báo "Đã tạo đề xuất gia hạn." và gia sư nhận thông báo "Contract renewal proposed".

## Sau khi đề xuất gia hạn

Hợp đồng gia hạn ở trạng thái "Đang chờ" cho tới khi bên kia ký. Trong lúc chờ, người đề xuất sửa được điều khoản hoặc hủy bản gia hạn. Khi được ký, hợp đồng gia hạn chuyển sang "Đang hoạt động" và có các kỳ thanh toán riêng.
