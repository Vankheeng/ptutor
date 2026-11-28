---
id: hop-dong-tao-gia-su
title: Gia sư soạn hợp đồng và quản lý hợp đồng
type: policy
audience: [TUTOR]
features: [contract]
routes: [/tutor/sections/:section, /tutor/dashboard, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: TutorContractController POST /api/v1/tutors/me/teaching-requests/{id}/student-requests/{id}/contracts -> ContractService.createFromStudentTutorRequest"
  - "backend: ContractController (sign, reject, cancel, renew, update) dùng chung cho gia sư"
  - "frontend: TutorLayout mục Hợp đồng -> /tutor/sections/contracts (TutorComingSoonPage: Sắp ra mắt); TutorDashboardPage (Hợp đồng đang dạy, Hợp đồng chờ xử lý)"
error_codes: [CONTRACT_SUBJECT_REQUIRED, DUPLICATE_CONTRACT, INVALID_CONTRACT_SOURCE_STATUS, INVALID_CONTRACT_APPLICATION_STATUS]
keywords: [gia sư tạo hợp đồng, gia sư ký hợp đồng, hợp đồng của gia sư, gia su tao hop dong, gia su ky hop dong, hop dong gia su]
todo:
  - "Khi nào có trang Hợp đồng cho gia sư? Tên các nút ký, từ chối, hủy, gia hạn trên trang gia sư là gì?"
---

# Gia sư soạn hợp đồng và quản lý hợp đồng

## Giao diện hợp đồng của gia sư

Hiện mục **Hợp đồng** trong menu gia sư đang hiện "Sắp ra mắt". Trang **Tổng quan** của gia sư có số **Hợp đồng đang dạy**, **Hợp đồng chờ xử lý** và mục **Buổi học sắp tới** trong 7 ngày tới. Khi có hợp đồng chờ bạn ký, bạn nhận thông báo "Contract signature required".

## Khi nào gia sư soạn hợp đồng

Gia sư soạn hợp đồng khi đã chấp nhận đơn đăng ký học của học viên vào tin nhận dạy. Điều kiện:

- Đơn đăng ký học đã được chấp nhận.
- Tin nhận dạy đang mở hoặc đã ghép.
- Tin nhận dạy đã có môn học trong danh sách của PTutor. Tin có môn tự nhập phải được duyệt xong trước.
- Đơn đó chưa có hợp đồng nào chưa bị hủy.

Gia sư điền giá, chu kỳ thanh toán, lịch học, ngày bắt đầu và ngày kết thúc. Lớp lấy theo đơn của học viên, và gia sư có thể đổi sang lớp khác thuộc tin nhận dạy.

## Khi học viên soạn hợp đồng

Khi học viên chấp nhận đề xuất giảng dạy của bạn, học viên là người soạn hợp đồng. Bạn là bên nhận và có quyền ký hoặc từ chối. Bạn không sửa được điều khoản do học viên soạn.

## Quy định chung với hợp đồng của gia sư

Các quy định ký, từ chối, hủy, gia hạn và tự kết thúc áp dụng giống nhau cho học viên và gia sư. Xem các tài liệu "Ký, từ chối hoặc hủy hợp đồng", "Gia hạn hợp đồng" và "Hợp đồng tự kết thúc khi hết hạn".
