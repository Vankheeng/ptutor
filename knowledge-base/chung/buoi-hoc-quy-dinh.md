---
id: buoi-hoc-quy-dinh
title: "Buổi học: đánh dấu đã dạy, xác nhận, tự hoàn tất sau 3 ngày, buổi bị giữ lại"
type: policy
audience: [STUDENT, TUTOR]
features: [lesson]
routes: [/student/contracts/:contractId/lessons, /student/contracts/:contractId]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: LessonController (tutor) POST /contracts/{id}/lessons, PUT /lessons/{id}, PATCH /lessons/{id}/status"
  - "backend: StudentLessonController POST /api/v1/students/me/lessons/{lessonId}/confirm"
  - "backend: LessonService.create/updateStatus/confirmByStudent/completeExpiredPendingLessons/initialStatus/hasBlockingComplaint"
  - "backend: LessonAutoCompletionScheduler (app.lesson.auto-completion-grace-days mặc định 3; app.lesson.auto-completion-interval-ms mặc định 3600000)"
  - "backend: ContractPaymentInstallmentService.assignNextInstallment, canCreateLesson (hợp đồng Mỗi buổi)"
  - "enum: LessonStatus; NotificationEventType LESSON_MARKED_TAUGHT, LESSON_CANCELLED"
error_codes: [LESSON_COMPLETION_ON_HOLD, LESSON_NOT_FINISHED, CONTRACT_LESSON_LIMIT_REACHED, LESSON_OUTSIDE_CONTRACT_PERIOD, CONTRACT_NOT_ACTIVE]
keywords: [buổi học, xác nhận buổi học, đánh dấu đã dạy, tự hoàn tất, 3 ngày, buổi bị giữ, buoi hoc, xac nhan buoi hoc, danh dau da day, tu hoan tat]
todo:
  - "Sau khi buổi học được xác nhận hoặc tự hoàn tất, gia sư nhận tiền dạy của buổi đó khi nào và bằng cách nào?"
---

# Buổi học: đánh dấu đã dạy, xác nhận, tự hoàn tất sau 3 ngày, buổi bị giữ lại

## Buổi học dùng để làm gì

Trên PTutor, buổi học dùng để ghi nhận việc dạy phục vụ thanh toán. Với hợp đồng có chu kỳ thanh toán **Mỗi buổi**, mỗi buổi gia sư tạo sẽ gắn với một kỳ học phí, hạn đóng là ngày của buổi học đó. Số buổi tạo được không vượt quá tổng số buổi của hợp đồng.

Chỉ tạo được buổi học khi hợp đồng đang hoạt động, và ngày học phải nằm trong thời gian hợp đồng.

## Gia sư đánh dấu đã dạy

Gia sư tạo buổi học rồi đánh dấu "đã dạy" sau khi buổi học kết thúc. Gia sư không đánh dấu được khi chưa tới giờ kết thúc buổi học. Buổi được tạo cho thời gian đã qua thì chuyển ngay sang chờ xác nhận.

Khi gia sư đánh dấu đã dạy, buổi học chuyển sang "Chờ xác nhận" và học viên nhận thông báo "Lesson awaiting confirmation". Gia sư cũng hủy được buổi sắp diễn ra hoặc đang chờ xác nhận; học viên nhận thông báo "Lesson cancelled".

## Học viên xác nhận buổi học

Khi buổi học "Chờ xác nhận", học viên bấm **Xác nhận đã học** nếu buổi học đã diễn ra. Buổi chuyển sang "Đã học". Nếu buổi học không diễn ra, học viên không xác nhận mà gửi yêu cầu hoàn tiền cho buổi đó.

## Tự hoàn tất sau 3 ngày

Nếu học viên không xác nhận và không yêu cầu hoàn tiền, buổi học tự chuyển sang "Đã học" sau 3 ngày kể từ lúc gia sư đánh dấu đã dạy. PTutor kiểm tra mỗi giờ một lần. Trang buổi học ghi "Hạn xác nhận" và thời gian còn lại cho từng buổi.

Ví dụ: gia sư đánh dấu đã dạy lúc 20 giờ ngày 01/11. Nếu học viên không làm gì, buổi tự hoàn tất trong vòng một giờ sau 20 giờ ngày 04/11.

## Buổi học bị giữ lại

Buổi học "Chờ xác nhận" bị giữ lại, không xác nhận được và không tự hoàn tất, khi:

- Buổi đó nằm trong một yêu cầu hoàn tiền chưa có kết luận. Trang hiện nhãn "Đang yêu cầu hoàn tiền".
- Hợp đồng có khiếu nại chưa có kết luận (đang chờ tiếp nhận, đang xử lý hoặc chờ bằng chứng).

Khi yêu cầu hoàn tiền hoặc khiếu nại có kết luận, buổi học được xử lý tiếp bình thường.
