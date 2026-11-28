---
id: buoi-hoc-gia-su
title: Tạo buổi học và đánh dấu đã dạy
type: policy
audience: [TUTOR]
features: [lesson]
routes: [/tutor/dashboard, /tutor/sections/:section]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: LessonController POST /api/v1/tutors/me/contracts/{contractId}/lessons, PUT /lessons/{lessonId}, PATCH /lessons/{lessonId}/status"
  - "backend: LessonService.create/update/updateStatus/validateSchedule (giờ bắt đầu trước giờ kết thúc; ngày trong thời hạn hợp đồng; chỉ đánh dấu đã dạy sau giờ kết thúc)"
  - "backend: ContractPaymentInstallmentService.canCreateLesson, assignNextInstallment"
  - "frontend: TutorLayout mục Lịch dạy -> /tutor/sections/calendar (Sắp ra mắt); TutorDashboardPage (Buổi học sắp tới, 7 ngày tới)"
error_codes: [LESSON_NOT_FINISHED, INVALID_LESSON_TIME, LESSON_OUTSIDE_CONTRACT_PERIOD, CONTRACT_LESSON_LIMIT_REACHED, CONTRACT_NOT_ACTIVE, INVALID_LESSON_STATUS, INVALID_LESSON_STATUS_TRANSITION]
keywords: [tạo buổi học, đánh dấu đã dạy, lịch dạy, hủy buổi học, tao buoi hoc, danh dau da day, lich day, huy buoi hoc]
todo:
  - "Khi nào có giao diện để gia sư tạo buổi học và đánh dấu đã dạy? Tên trang và tên nút là gì?"
---

# Tạo buổi học và đánh dấu đã dạy

## Giao diện buổi học của gia sư

Hiện mục **Lịch dạy** trong menu gia sư đang hiện "Sắp ra mắt". Trang **Tổng quan** có mục **Buổi học sắp tới** cho 7 ngày tới. Các quy định dưới đây là quy định của hệ thống.

## Tạo buổi học

Gia sư tạo buổi học cho hợp đồng đang hoạt động, gồm tên buổi, ngày, giờ bắt đầu, giờ kết thúc và ghi chú. Quy định:

- Giờ bắt đầu phải trước giờ kết thúc.
- Ngày học phải nằm trong khoảng ngày bắt đầu và ngày kết thúc của hợp đồng.
- Với hợp đồng **Mỗi buổi**, số buổi không vượt quá tổng số buổi của hợp đồng. Mỗi buổi tạo ra gắn với một kỳ học phí của học viên.

Buổi tạo cho thời gian đã qua chuyển ngay sang "Chờ xác nhận".

## Đánh dấu đã dạy

Sau giờ kết thúc buổi học, gia sư đánh dấu buổi là đã dạy. Buổi chuyển sang "Chờ xác nhận" và học viên nhận thông báo. Gia sư không đánh dấu được trước giờ kết thúc.

Từ lúc đánh dấu, học viên có 3 ngày để xác nhận hoặc yêu cầu hoàn tiền. Quá 3 ngày mà không có yêu cầu hoàn tiền hay khiếu nại đang mở, buổi tự chuyển sang "Đã học".

## Sửa và hủy buổi học

Gia sư chỉ sửa được buổi "Sắp diễn ra". Gia sư hủy được buổi "Sắp diễn ra" hoặc "Chờ xác nhận"; học viên nhận thông báo buổi bị hủy.
