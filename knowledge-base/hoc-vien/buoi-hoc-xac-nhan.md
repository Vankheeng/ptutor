---
id: buoi-hoc-xac-nhan
title: Cách xác nhận buổi học
type: guide
audience: [STUDENT]
features: [lesson]
routes: [/student/contracts/:contractId, /student/contracts/:contractId/lessons]
status: draft
version: 1
updated: 2026-11-03
source:
  - "frontend: pages/student/StudentContractDetailPage.tsx (liên kết Buổi học và hoàn tiền)"
  - "frontend: pages/student/StudentContractLessonsPage.tsx (Buổi học, Hạn xác nhận, Xác nhận đã học, Yêu cầu hoàn tiền, Xem yêu cầu hoàn tiền)"
  - "backend: StudentContractLessonController GET; StudentLessonController POST /{lessonId}/confirm -> LessonService.confirmByStudent"
error_codes: [LESSON_COMPLETION_ON_HOLD, INVALID_LESSON_STATUS_TRANSITION]
keywords: [xác nhận buổi học, xác nhận đã học, xem buổi học, xac nhan buoi hoc, xac nhan da hoc, xem buoi hoc]
todo: []
---

# Cách xác nhận buổi học

## Xem danh sách buổi học của hợp đồng

1. Vào **Hợp đồng**, bấm **Xem hợp đồng** ở hợp đồng đang hoạt động.
2. Bấm **Buổi học và hoàn tiền**. Trang **Buổi học** mở ra.
3. Mỗi buổi ghi ngày, giờ học, tên buổi và trạng thái.

Với buổi đang chờ xác nhận, trang ghi "Hạn xác nhận" kèm thời gian còn lại, ví dụ "Còn 2 ngày".

## Xác nhận buổi học đã diễn ra

1. Trong trang **Buổi học**, tìm buổi đang chờ xác nhận.
2. Bấm **Xác nhận đã học**.

Trang báo "Đã xác nhận buổi …" và buổi chuyển sang "Đã học". Nút **Xác nhận đã học** chỉ hiện ở buổi đang chờ xác nhận và không bị giữ lại.

## Khi buổi học không diễn ra

Không bấm **Xác nhận đã học**. Thay vào đó, bấm **Yêu cầu hoàn tiền** ở đầu trang **Buổi học** trong vòng 3 ngày kể từ lúc gia sư đánh dấu đã dạy. Xem tài liệu "Cách gửi, theo dõi và hủy yêu cầu hoàn tiền".

## Khi không xác nhận được buổi học

Nếu trang báo "Buổi học đang bị tạm giữ, chưa thể xác nhận.", buổi đó đang nằm trong yêu cầu hoàn tiền chưa có kết luận, hoặc hợp đồng đang có khiếu nại chưa xử lý xong. Khi có kết luận, bạn xác nhận lại bình thường.
