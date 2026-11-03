---
id: loi-buoi-hoc
title: Lỗi thường gặp với buổi học
type: error
audience: [STUDENT, TUTOR]
features: [lesson]
routes: [/student/contracts/:contractId/lessons, /tutor/dashboard]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: LessonController (tutor) POST /contracts/{contractId}/lessons, PUT /lessons/{lessonId}, PATCH /lessons/{lessonId}/status"
  - "backend: StudentLessonController POST /api/v1/students/me/lessons/{lessonId}/confirm"
  - "backend: LessonService.create, update, updateStatus, confirmByStudent, validateActiveContract, validateSchedule, ensureScheduled, ensurePendingConfirmation, ensureNoBlockingComplaint"
  - "backend: ContractPaymentInstallmentService.canCreateLesson, assignNextInstallment (hợp đồng Mỗi buổi)"
  - "frontend: pages/student/StudentContractLessonsPage.tsx; components/refund/refundFormat.ts errorMessages (LESSON_COMPLETION_ON_HOLD -> Buổi học đang bị tạm giữ, chưa thể xác nhận.)"
  - "frontend: chưa có giao diện buổi học cho gia sư (Lịch dạy là trang Sắp ra mắt); câu lỗi phía gia sư là câu tiếng Anh của máy chủ"
error_codes: [LESSON_NOT_FINISHED, LESSON_COMPLETION_ON_HOLD, CONTRACT_LESSON_LIMIT_REACHED, CONTRACT_NOT_ACTIVE, LESSON_OUTSIDE_CONTRACT_PERIOD, INVALID_LESSON_TIME, INVALID_LESSON_STATUS, INVALID_LESSON_STATUS_TRANSITION, LESSON_DATE_REQUIRED, LESSON_START_TIME_REQUIRED, LESSON_END_TIME_REQUIRED]
keywords: [lỗi buổi học, không đánh dấu đã dạy được, không xác nhận được buổi học, buổi học bị tạm giữ, hết số buổi, loi buoi hoc, khong danh dau da day, khong xac nhan duoc, bi tam giu, het so buoi]
todo:
  - "Khi có giao diện buổi học cho gia sư, cập nhật các câu báo lỗi phía gia sư theo câu hiển thị thật."
  - "Hợp đồng Mỗi buổi đã tạo đủ số buổi mà cần dạy thêm buổi bù thì gia sư làm thế nào?"
---

# Lỗi thường gặp với buổi học

## "A lesson can only be marked as taught after it ends"

Câu này hiện với gia sư, nghĩa là buổi học chưa kết thúc nên chưa đánh dấu "đã dạy" được. Gia sư chỉ đánh dấu đã dạy sau giờ kết thúc của buổi học. Cách khắc phục: chờ qua giờ kết thúc rồi đánh dấu lại. Nếu giờ học ghi sai, gia sư sửa giờ của buổi học khi buổi còn ở trạng thái "Sắp diễn ra".

## "Buổi học đang bị tạm giữ, chưa thể xác nhận."

Câu này hiện với học viên khi bấm **Xác nhận đã học**. Buổi học bị giữ lại vì một trong hai lý do:

- Buổi học nằm trong một yêu cầu hoàn tiền chưa có kết luận. Trang **Buổi học** hiện nhãn "Đang yêu cầu hoàn tiền" ở buổi đó.
- Hợp đồng đang có khiếu nại chưa có kết luận (chờ tiếp nhận, đang xử lý hoặc chờ bằng chứng).

Cách khắc phục: chờ yêu cầu hoàn tiền hoặc khiếu nại có kết luận, rồi xác nhận lại. Trong thời gian bị giữ, buổi học cũng không tự hoàn tất sau 3 ngày.

## Buổi học bị giữ vì khiếu nại (phía gia sư)

Khi hợp đồng có khiếu nại chưa có kết luận, gia sư thấy câu tiếng Anh "Lesson completion is on hold while a complaint is being processed". Câu này nghĩa là buổi học đang bị giữ lại vì khiếu nại. Cách khắc phục: chờ nhân viên chăm sóc khách hàng kết luận khiếu nại.

## "The contract already has its maximum number of lessons"

Câu này hiện với gia sư khi tạo buổi học, nghĩa là hợp đồng đã hết số buổi. Với hợp đồng có chu kỳ thanh toán **Mỗi buổi**, số buổi tạo được không vượt quá tổng số buổi ghi trong hợp đồng. Cách khắc phục: không tạo thêm buổi trong hợp đồng này. Muốn dạy thêm, hai bên đề xuất gia hạn hợp đồng.

## "Lessons can only be managed for an active contract"

Câu này nghĩa là hợp đồng không ở trạng thái "Đang hoạt động", có thể còn chờ ký, đã hoàn tất hoặc đã hủy. Chỉ tạo và quản lý buổi học được khi hợp đồng đang hoạt động. Cách khắc phục: chờ hợp đồng được ký; nếu hợp đồng đã kết thúc, gia hạn hoặc tạo hợp đồng mới.

## "Lesson date must be within the contract period"

Câu này nghĩa là ngày học nằm ngoài thời gian hợp đồng. Cách khắc phục: chọn ngày học từ ngày bắt đầu đến ngày kết thúc của hợp đồng.

## "Start time must be before end time"

Câu này nghĩa là giờ bắt đầu không trước giờ kết thúc. Cách khắc phục: nhập lại giờ học cho đúng. Ngày, giờ bắt đầu và giờ kết thúc đều bắt buộc.

## "Only SCHEDULED lessons can be …"

Câu tiếng Anh bắt đầu bằng "Only SCHEDULED lessons can be" nghĩa là buổi học không còn "Sắp diễn ra", nên không sửa được nữa. Cách khắc phục: tải lại để xem trạng thái mới của buổi học.

## Buổi học không còn chờ xác nhận

Khi xác nhận buổi học, bạn có thể thấy câu tiếng Anh bắt đầu bằng "Only" và kết thúc bằng "lessons can be confirmed". Câu này nghĩa là buổi học không còn "Chờ xác nhận". Lỗi xảy ra khi học viên xác nhận một buổi vừa tự hoàn tất, vừa bị gia sư hủy, hoặc đã được xác nhận trước đó. Cách khắc phục: tải lại trang **Buổi học** để xem trạng thái mới.

## Không hủy được buổi học

Khi hủy buổi học, gia sư có thể thấy câu tiếng Anh bắt đầu bằng "Only SCHEDULED or" và kết thúc bằng "lessons can be cancelled". Câu này nghĩa là chỉ hủy được buổi "Sắp diễn ra" hoặc "Chờ xác nhận". Buổi đã học hoặc đã hủy thì không hủy được nữa.

## "Tutor can only mark a lesson as taught or cancel it"

Câu này nghĩa là gia sư chỉ được đánh dấu buổi học là đã dạy hoặc hủy buổi học. Việc xác nhận "Đã học" là của học viên, hoặc hệ thống tự làm sau 3 ngày.
