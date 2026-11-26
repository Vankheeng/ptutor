---
id: trang-thai-hop-dong
title: Giải nghĩa trạng thái hợp đồng
type: glossary
audience: [STUDENT, TUTOR]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: ContractStatus (PENDING, ACTIVE, COMPLETED, CANCELLED)"
  - "backend: ContractService.sign/reject/cancel/renew; ContractLifecycleService.processExpiredContracts"
  - "frontend: components/student/StudentContractStatusBadge.tsx, pages/student/StudentContractsPage.tsx (Đang chờ, Đang hoạt động, Đã hoàn tất, Đã hủy)"
error_codes: [INVALID_CONTRACT_STATUS_TRANSITION, CONTRACT_NOT_ACTIVE]
keywords: [trạng thái hợp đồng, đang chờ, đang hoạt động, đã hoàn tất, đã hủy, trang thai hop dong, dang cho, dang hoat dong, da hoan tat, da huy]
todo: []
---

# Giải nghĩa trạng thái hợp đồng

## Đang chờ

Hợp đồng "Đang chờ" đã được soạn và đang chờ bên nhận ký. Bước tiếp theo: bên nhận bấm **Ký hợp đồng** hoặc **Từ chối hợp đồng**. Trong lúc chờ, người soạn có thể sửa một số điều khoản hoặc bấm **Hủy hợp đồng**. Hợp đồng đang chờ chưa có kỳ thanh toán nào. Nếu tới ngày kết thúc mà chưa ký, hợp đồng tự chuyển sang "Đã hủy".

## Đang hoạt động

Hợp đồng "Đang hoạt động" đã được ký và đang có hiệu lực. Bước tiếp theo: học viên đóng học phí theo các kỳ thanh toán, gia sư dạy và ghi nhận buổi học, học viên xác nhận buổi học. Hai bên có thể đề xuất gia hạn. Hợp đồng đang hoạt động không hủy được trên PTutor.

## Đã hoàn tất

Hợp đồng "Đã hoàn tất" đã tới ngày kết thúc khi đang hoạt động. PTutor tự chuyển trạng thái lúc 0 giờ ngày kết thúc. Không ai cần làm gì thêm. Muốn học tiếp, hai bên tạo hợp đồng mới.

## Đã hủy

Hợp đồng "Đã hủy" không có hiệu lực. Hợp đồng rơi vào trạng thái này khi người soạn hủy, bên nhận từ chối, hoặc tới ngày kết thúc mà chưa được ký. Người soạn có thể soạn hợp đồng mới từ cùng đề nghị hoặc đơn đã chấp nhận.
