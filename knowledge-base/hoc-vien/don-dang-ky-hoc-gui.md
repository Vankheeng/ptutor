---
id: don-dang-ky-hoc-gui
title: Xem tin nhận dạy và gửi đơn đăng ký học
type: guide
audience: [STUDENT]
features: [matching]
routes: [/student/teaching-requests, /student/applications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/student/StudentTeachingRequestsPage.tsx (Browse Teaching Requests, Apply to study, Send application; ô Grade ID nhập mã lớp)"
  - "frontend: pages/student/StudentApplicationsPage.tsx (My requests to studying, Cancel)"
  - "backend: StudentTutorRequestStudentController POST /teaching-requests/{id}/student-tutor-requests, PATCH /student-tutor-requests/{id}/status"
  - "backend: StudentTutorRequestService.createForStudent/cancelForStudent"
error_codes: [INVALID_TEACHING_REQUEST_STATUS, INVALID_GRADE, DUPLICATE_STUDENT_TUTOR_REQUEST, INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION]
keywords: [đơn đăng ký học, đăng ký học với gia sư, tin nhận dạy, ứng tuyển học, apply to study, don dang ky hoc, dang ky hoc, tin nhan day]
todo:
  - "Ô Grade ID trên form đăng ký học yêu cầu nhập mã lớp dạng chuỗi ký tự, người dùng không biết mã này. Khi giao diện đổi sang chọn lớp từ danh sách, cần cập nhật bước 3."
---

# Xem tin nhận dạy và gửi đơn đăng ký học

## Xem tin nhận dạy của gia sư

1. Trong menu bên trái, chọn **Browse Teaching Requests**.
2. Trang liệt kê các tin nhận dạy đang mở của gia sư, gồm môn, hình thức dạy (**Online** hoặc **In person**) và tên gia sư.

Chỉ tin nhận dạy đang mở mới hiện ở đây.

## Gửi đơn đăng ký học

1. Ở tin nhận dạy bạn quan tâm, bấm **Apply to study**.
2. Điền **Proposed price** (mức giá bạn đề nghị), **Learning mode** (hình thức học).
3. Điền **Grade ID** (lớp của bạn). Lớp phải là một trong các lớp mà tin nhận dạy đó dạy.
4. Nếu cần, điền **Preferred schedule** (lịch mong muốn) và **Message** (lời nhắn cho gia sư).
5. Bấm **Send application**.

Khi gửi thành công, trang báo "Your application was sent." (đã gửi đơn). Gia sư nhận thông báo có đơn đăng ký mới.

## Quy định khi gửi đơn đăng ký học

- Chỉ gửi được đơn vào tin nhận dạy đang mở.
- Với mỗi tin nhận dạy, bạn chỉ có một đơn đang chờ tại một thời điểm.

## Theo dõi và hủy đơn đăng ký học

1. Trong menu, chọn **My requests to studying**.
2. Dùng các thẻ lọc để xem đơn theo trạng thái.
3. Muốn hủy một đơn đang chờ, bấm **Cancel** ở đơn đó.

Chỉ hủy được đơn đang chờ. Đơn đã được gia sư chấp nhận hay từ chối thì không hủy được.

## Sau khi gia sư chấp nhận đơn đăng ký học

Khi gia sư chấp nhận đơn, bạn nhận thông báo. Gia sư là người soạn hợp đồng và gửi bạn ký. Bạn ký hợp đồng ở trang **Hợp đồng**.
