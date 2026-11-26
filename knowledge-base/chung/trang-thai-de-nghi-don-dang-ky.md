---
id: trang-thai-de-nghi-don-dang-ky
title: Giải nghĩa trạng thái đề nghị dạy và đơn đăng ký học
type: glossary
audience: [STUDENT, TUTOR]
features: [matching]
routes: [/student/studying-requests/:requestId, /student/applications, /tutor/proposals]
status: draft
version: 1
updated: 2026-11-02
source:
  - "enum: ApplicationStatus (PENDING, ACCEPTED, REJECTED, CANCELLED)"
  - "backend: TutorStudentRequestService (đề xuất của gia sư); StudentTutorRequestService (đơn đăng ký của học viên); StudyingRequestService.cancel (huỷ đề xuất đang chờ khi huỷ tin)"
  - "frontend: components/student/StudentTutorOffers.tsx, pages/student/StudentTutorOfferDetailPage.tsx (Đang chờ, Đã chấp nhận, Đã từ chối, Đã hủy); pages/tutor/TutorProposalsPage.tsx (Đang chờ, Đã chấp nhận, Bị từ chối, Đã hủy); pages/student/StudentApplicationsPage.tsx (hiển thị mã tiếng Anh)"
error_codes: [INVALID_TUTOR_STUDENT_REQUEST_STATUS_TRANSITION, INVALID_STUDENT_TUTOR_REQUEST_STATUS_TRANSITION]
keywords: [đề xuất dạy, đề nghị dạy, đơn đăng ký học, đang chờ, đã chấp nhận, bị từ chối, de xuat day, de nghi day, don dang ky hoc, dang cho, da chap nhan, tu choi]
todo:
  - "Đề xuất hoặc đơn đăng ký đang chờ có tự hết hạn sau một thời gian không? (code không có)"
---

# Giải nghĩa trạng thái đề nghị dạy và đơn đăng ký học

**Đề nghị dạy** (gia sư gọi là "đề xuất giảng dạy") là lời đề nghị gia sư gửi vào tin tìm gia sư của học viên. **Đơn đăng ký học** là đơn học viên gửi vào tin nhận dạy của gia sư. Hai loại này dùng chung các trạng thái sau.

## Đang chờ

Đề nghị dạy hoặc đơn đăng ký học "Đang chờ" đã được gửi và chưa được bên kia trả lời. Với đề nghị dạy, học viên chấp nhận hoặc từ chối. Với đơn đăng ký học, gia sư chấp nhận hoặc từ chối. Trong lúc đang chờ, người gửi vẫn sửa (với đề nghị dạy) hoặc hủy được.

## Đã chấp nhận

Đề nghị dạy hoặc đơn đăng ký học "Đã chấp nhận" được bên kia đồng ý. Bước tiếp theo là tạo hợp đồng. Với đề nghị dạy, học viên tạo hợp đồng bằng nút **Tạo hợp đồng**. Với đơn đăng ký học, gia sư tạo hợp đồng.

## Đã từ chối

Đề nghị dạy hoặc đơn đăng ký học bị bên kia từ chối hiện là "Đã từ chối" ở trang học viên và "Bị từ chối" ở trang gia sư. Người gửi nhận thông báo. Gia sư có thể gửi lại đề xuất mới cho tin đó nếu tin vẫn đang mở.

## Đã hủy

Đề nghị dạy hoặc đơn đăng ký học "Đã hủy" do chính người gửi hủy khi còn đang chờ. Đề nghị dạy cũng tự chuyển sang "Đã hủy" khi học viên hủy tin tìm gia sư.

## Trạng thái trên trang My requests to studying

Trang **My requests to studying** của học viên hiện trạng thái đơn đăng ký học bằng chữ tiếng Anh: pending (đang chờ), accepted (đã chấp nhận), rejected (đã từ chối), cancelled (đã hủy).
