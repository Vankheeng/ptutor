---
id: don-dang-ky-hoc-xu-ly
title: Xử lý đơn đăng ký học của học viên
type: policy
audience: [TUTOR]
features: [matching]
routes: [/tutor/sections/:section, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: StudentTutorRequestController GET, PATCH /api/v1/tutors/me/teaching-requests/{id}/student-requests/{requestId}/status"
  - "backend: StudentTutorRequestService.updateStatus (chỉ ACCEPTED/REJECTED; đơn phải PENDING; tin phải OPEN khi chấp nhận; tin MATCHED khi đủ quantity)"
  - "frontend: không có trang cho gia sư xử lý đơn đăng ký (mục Yêu cầu dạy của tôi đang là trang Sắp ra mắt)"
error_codes: [INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION, TUTOR_PROFILE_REQUIRED]
keywords: [đơn đăng ký học, học viên đăng ký, chấp nhận học viên, từ chối học viên, don dang ky hoc, chap nhan hoc vien, tu choi hoc vien]
todo:
  - "Khi nào có giao diện để gia sư xem và xử lý đơn đăng ký học? Tên trang và tên nút là gì?"
---

# Xử lý đơn đăng ký học của học viên

## Giao diện xử lý đơn đăng ký học

Hiện giao diện gia sư chưa có trang xem và xử lý đơn đăng ký học. Mục **Yêu cầu dạy của tôi** trong menu đang hiện "Sắp ra mắt". Khi có học viên gửi đơn, bạn vẫn nhận thông báo "New student application" trong mục **Thông báo**.

## Quy định chấp nhận đơn đăng ký học

Khi xử lý đơn đăng ký học, gia sư có hai lựa chọn: chấp nhận hoặc từ chối. Quy định:

- Chỉ xử lý được đơn đang chờ.
- Chỉ chấp nhận được đơn khi tin nhận dạy đang mở.
- Mỗi tin nhận dạy có số học viên cần nhận. Khi số đơn đã chấp nhận đạt con số này, tin chuyển sang đã ghép và không nhận đơn mới.

Học viên nhận thông báo khi đơn được chấp nhận hoặc bị từ chối.

## Bước tiếp theo sau khi chấp nhận đơn

Sau khi chấp nhận đơn đăng ký học, gia sư là người soạn hợp đồng và gửi học viên ký. Xem tài liệu "Gia sư soạn hợp đồng và quản lý hợp đồng".
