---
id: loi-de-nghi-don-dang-ky
title: Lỗi thường gặp với đề nghị dạy và đơn đăng ký học
type: error
audience: [STUDENT, TUTOR]
features: [matching]
routes: [/tutor/proposals, /student/applications, /student/teaching-requests, /student/studying-requests/:requestId]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: TutorStudentRequestService (create, update, cancel, accept, reject)"
  - "backend: StudentTutorRequestService (createForStudent, cancelForStudent, updateStatus)"
  - "frontend: pages/tutor/TutorStudyingRequestPage.tsx (thông báo tiếng Việt cho đề xuất trùng); các trang học viên hiển thị câu của máy chủ"
error_codes: [STUDYING_REQUEST_NOT_OPEN, DUPLICATE_TUTOR_STUDENT_REQUEST, INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION, STUDYING_REQUEST_ALREADY_MATCHED, INVALID_PREFERRED_LOCATION, INVALID_TEACHING_REQUEST_STATUS, DUPLICATE_STUDENT_TUTOR_REQUEST, INVALID_GRADE, INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION]
keywords: [lỗi gửi đề xuất, lỗi đăng ký học, không chấp nhận được, loi gui de xuat, loi dang ky hoc, khong chap nhan duoc]
todo: []
---

# Lỗi thường gặp với đề nghị dạy và đơn đăng ký học

## "Bạn đã có đề xuất đang chờ xử lý cho yêu cầu này"

Câu này hiện với gia sư, nghĩa là bạn đã có một đề xuất đang chờ cho tin này. Cách khắc phục: sửa đề xuất cũ bằng **Sửa đề xuất**, hoặc hủy đề xuất cũ rồi gửi đề xuất mới.

## "Teaching proposals can only be sent to OPEN studying requests"

Câu này nghĩa là tin tìm gia sư không còn mở, có thể đã tạm đóng, đã ghép hoặc đã hủy. Cách khắc phục: chọn tin khác đang mở. Trang tin cũng có thể báo "Yêu cầu học này không còn khả dụng."

## "Province and district are required for in-person proposals"

Câu này nghĩa là đề xuất dạy trực tiếp chưa có địa điểm. Cách khắc phục: chọn **Tỉnh/thành phố** và **Quận/huyện** ở mục **Địa điểm dạy**.

## "Only PENDING teaching proposals can be cancelled" hoặc "… updated"

Câu này nghĩa là đề xuất không còn đang chờ nên không sửa hay hủy được. Đề xuất đã được chấp nhận, bị từ chối hoặc đã hủy thì giữ nguyên.

## "Studying request already has an accepted tutor"

Câu này hiện với học viên, nghĩa là tin đã có một gia sư được chấp nhận. Mỗi tin chỉ chấp nhận một gia sư. Cách khắc phục: tạo hợp đồng với gia sư đã chấp nhận, hoặc đăng tin mới nếu cần thêm gia sư.

## "Only OPEN studying requests can accept tutor requests"

Câu này nghĩa là tin của bạn không còn mở nên không chấp nhận được đề nghị. Cách khắc phục: nếu tin đang tạm đóng, mở lại tin bằng **Reopen request** rồi chấp nhận.

## "Only OPEN teaching requests can receive student applications"

Câu này hiện với học viên, nghĩa là tin nhận dạy không còn mở. Cách khắc phục: chọn tin nhận dạy khác.

## "Grade is not associated with the teaching request"

Câu này nghĩa là lớp bạn nhập không thuộc các lớp mà tin nhận dạy đó dạy. Cách khắc phục: nhập đúng lớp mà tin nhận dạy có.

## "A pending student tutor request already exists"

Câu này hiện với học viên, nghĩa là bạn đã có một đơn đăng ký học đang chờ cho cùng tin nhận dạy. Cách khắc phục: chờ gia sư trả lời đơn cũ, hoặc hủy đơn cũ ở trang **My requests to studying** rồi gửi lại.
