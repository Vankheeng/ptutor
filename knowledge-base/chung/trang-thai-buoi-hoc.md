---
id: trang-thai-buoi-hoc
title: Giải nghĩa trạng thái buổi học
type: glossary
audience: [STUDENT, TUTOR]
features: [lesson]
routes: [/student/contracts/:contractId/lessons]
status: draft
version: 1
updated: 2026-11-03
source:
  - "enum: LessonStatus (SCHEDULED, PENDING_CONFIRMATION, CONFIRMED, COMPLETED, CANCELLED)"
  - "frontend: components/refund/refundFormat.ts lessonStatusLabels (Sắp diễn ra, Chờ xác nhận, Đã học, Đã học, Đã huỷ); pages/student/StudentContractLessonsPage.tsx (nhãn Đang yêu cầu hoàn tiền)"
  - "backend: LessonService; AdminRefundRequestService (kết luận buổi bị phản đối: Đã dạy -> COMPLETED, vắng -> CANCELLED)"
error_codes: [LESSON_COMPLETION_ON_HOLD]
keywords: [trạng thái buổi học, sắp diễn ra, chờ xác nhận, đã học, đã hủy, trang thai buoi hoc, sap dien ra, cho xac nhan, da hoc]
todo: []
---

# Giải nghĩa trạng thái buổi học

## Sắp diễn ra

Buổi học "Sắp diễn ra" đã được gia sư lên lịch và chưa tới giờ kết thúc. Bước tiếp theo: sau giờ học, gia sư đánh dấu đã dạy. Học viên chưa cần làm gì.

## Chờ xác nhận

Buổi học "Chờ xác nhận" đã được gia sư đánh dấu đã dạy. Bước tiếp theo: học viên bấm **Xác nhận đã học** nếu buổi học đã diễn ra, hoặc gửi yêu cầu hoàn tiền nếu buổi học không diễn ra. Học viên có 3 ngày kể từ lúc gia sư đánh dấu; quá hạn, buổi tự chuyển sang "Đã học".

## Đang yêu cầu hoàn tiền

Nhãn "Đang yêu cầu hoàn tiền" hiện ở buổi học chờ xác nhận nằm trong một yêu cầu hoàn tiền chưa có kết luận. Buổi bị giữ lại: không xác nhận được và không tự hoàn tất. Bước tiếp theo: gia sư phản hồi, kế toán PTutor kết luận.

## Đã học

Buổi học "Đã học" đã được học viên xác nhận, tự hoàn tất sau 3 ngày, hoặc được kế toán kết luận là gia sư đã dạy. Đây là trạng thái cuối, không ai cần làm gì thêm.

## Đã hủy

Buổi học "Đã hủy" không được tính là đã dạy. Buổi rơi vào trạng thái này khi gia sư hủy buổi, hoặc khi kế toán kết luận buổi đó gia sư vắng hoặc học viên nghỉ trong yêu cầu hoàn tiền.
