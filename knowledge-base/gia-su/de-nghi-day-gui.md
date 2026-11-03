---
id: de-nghi-day-gui
title: Cách gửi, sửa, hủy đề xuất giảng dạy
type: guide
audience: [TUTOR]
features: [matching]
routes: [/tutor/dashboard, /tutor/studying-requests/:requestId, /tutor/proposals]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/tutor/TutorDashboardPage.tsx (danh sách Yêu cầu tìm gia sư)"
  - "frontend: pages/tutor/TutorStudyingRequestPage.tsx (Ứng tuyển yêu cầu này, Sửa đề xuất, Gửi lại đề xuất)"
  - "frontend: pages/tutor/components/TutorApplicationForm.tsx (Học phí đề xuất (VNĐ), Hình thức dạy, Thời gian có thể dạy, Địa điểm dạy, Lời nhắn cho học viên, Gửi đề xuất, Lưu thay đổi)"
  - "frontend: pages/tutor/TutorProposalsPage.tsx, components/TutorProposalCard.tsx (Hủy đề xuất)"
  - "backend: TutorStudentRequestController POST /studying-requests/{id}/tutor-student-requests, PUT /tutor-student-requests/{id}, POST .../cancel"
  - "backend: TutorStudentRequestService.create/update/cancel (chỉ gửi vào tin OPEN; một đề xuất PENDING mỗi tin; dạy trực tiếp cần tỉnh và quận)"
error_codes: [STUDYING_REQUEST_NOT_OPEN, DUPLICATE_TUTOR_STUDENT_REQUEST, INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION, INVALID_PREFERRED_LOCATION, INVALID_GRADE]
keywords: [gửi đề xuất, ứng tuyển dạy, đề xuất giảng dạy, nhận lớp, tìm học viên, gui de xuat, ung tuyen, de xuat giang day, nhan lop]
todo:
  - "Gia sư chưa được duyệt hồ sơ có được gửi đề xuất không? (code hiện không chặn)"
---

# Cách gửi, sửa, hủy đề xuất giảng dạy

## Tìm tin tìm gia sư của học viên

1. Vào **Tổng quan**. Mục **Yêu cầu tìm gia sư** liệt kê các tin đang mở của học viên.
2. Dùng ô tìm kiếm "Tìm môn học, lớp, khu vực...", lọc theo **Trực tuyến** hoặc **Trực tiếp**, sắp xếp theo **Mới nhất** hoặc **Ngân sách cao nhất**.
3. Mở một tin để xem ngân sách, lớp học, hình thức học, địa chỉ, lịch học mong muốn và mục tiêu của học viên.

Gia sư chỉ thấy tin đang mở.

## Gửi đề xuất giảng dạy

1. Trong trang tin, bấm **Ứng tuyển yêu cầu này**.
2. Nhập **Học phí đề xuất (VNĐ)** và chọn **Hình thức dạy**.
3. Ở **Thời gian có thể dạy**, bấm **+ Thêm ngày** và chọn thứ, giờ **Từ**, giờ **Đến**.
4. Nếu dạy trực tiếp, chọn **Tỉnh/thành phố** và **Quận/huyện** ở mục **Địa điểm dạy**.
5. Viết **Lời nhắn cho học viên**.
6. Bấm **Gửi đề xuất**.

Gửi thành công, trang báo "Đề xuất đã được gửi đến học viên." và học viên nhận thông báo.

## Quy định khi gửi đề xuất giảng dạy

- Chỉ gửi được vào tin đang mở.
- Với mỗi tin, bạn chỉ có một đề xuất đang chờ. Gửi thêm sẽ báo "Bạn đã có đề xuất đang chờ xử lý cho yêu cầu này."
- Giờ kết thúc của mỗi khung phải sau giờ bắt đầu.

## Sửa đề xuất giảng dạy

1. Mở trang tin đã gửi đề xuất, bấm **Sửa đề xuất**.
2. Sửa thông tin, bấm **Lưu thay đổi**.

Chỉ sửa được đề xuất đang chờ.

## Hủy đề xuất giảng dạy

1. Vào **Đề xuất giảng dạy** trong menu.
2. Ở đề xuất đang chờ, bấm **Hủy đề xuất**.

Chỉ hủy được đề xuất đang chờ.

## Gửi lại đề xuất giảng dạy

Khi đề xuất trước đã bị từ chối hoặc đã hủy và tin vẫn đang mở, trang tin hiện nút **Gửi lại đề xuất** để bạn gửi đề xuất mới.
