---
id: hop-dong-xem-ky-hoc-vien
title: Cách xem, ký, từ chối hợp đồng
type: guide
audience: [STUDENT]
features: [contract]
routes: [/student/contracts, /student/contracts/:contractId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/student/StudentContractsPage.tsx (Hợp đồng; lọc Tất cả, Đang chờ, Đang hoạt động, Đã hoàn tất, Đã hủy; Xem hợp đồng)"
  - "frontend: pages/student/StudentContractDetailPage.tsx; components/student/StudentContractActionBar.tsx (Ký hợp đồng, Từ chối hợp đồng chỉ khi PENDING và là bên nhận; Hủy hợp đồng khi PENDING và là người tạo)"
  - "frontend: components/student/StudentContractOverview.tsx (Giá, Chu kỳ thanh toán, Tổng số buổi, Lịch học, Người tạo, Thời gian ký)"
  - "backend: ContractController GET, PATCH /sign, /reject"
error_codes: [CONTRACT_COUNTERPARTY_REQUIRED, CONTRACT_EXPIRED]
keywords: [xem hợp đồng, ký hợp đồng, từ chối hợp đồng, danh sách hợp đồng, xem hop dong, ky hop dong, tu choi hop dong]
todo: []
---

# Cách xem, ký, từ chối hợp đồng

## Xem danh sách hợp đồng

1. Trong menu bên trái, chọn **Hợp đồng**.
2. Lọc theo trạng thái: **Tất cả**, **Đang chờ**, **Đang hoạt động**, **Đã hoàn tất**, **Đã hủy**.
3. Bấm **Xem hợp đồng** để mở chi tiết.

Trang chi tiết hợp đồng ghi **Môn học**, **Lớp**, **Hình thức dạy**, **Giá**, **Chu kỳ thanh toán**, **Tổng số buổi**, **Lịch học**, **Ngày bắt đầu**, **Ngày kết thúc**, **Người tạo** và **Thời gian ký**.

## Ký hợp đồng do gia sư soạn

1. Mở hợp đồng ở trạng thái "Đang chờ".
2. Đọc kỹ điều khoản.
3. Bấm **Ký hợp đồng**.

Trang báo "Đã ký hợp đồng." và hợp đồng chuyển sang "Đang hoạt động". Nút **Ký hợp đồng** chỉ hiện khi hợp đồng đang chờ và bạn là bên nhận.

## Từ chối hợp đồng do gia sư soạn

1. Mở hợp đồng ở trạng thái "Đang chờ".
2. Bấm **Từ chối hợp đồng**.

Trang báo "Đã từ chối hợp đồng." và hợp đồng chuyển sang "Đã hủy".

## Các mục khác trong trang hợp đồng

Khi hợp đồng đang hoạt động, trang chi tiết có thêm:

- **Các kỳ thanh toán**: danh sách kỳ học phí và nút **Thanh toán**.
- **Buổi học và hoàn tiền**: mở danh sách buổi học để xác nhận hoặc yêu cầu hoàn tiền.
- **Đề xuất gia hạn**: để gia hạn hợp đồng.
