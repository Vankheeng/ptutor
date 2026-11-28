---
id: tin-tim-gia-su-dang
title: Cách đăng tin tìm gia sư
type: guide
audience: [STUDENT]
features: [studying-request]
routes: [/student/studying-requests, /student/studying-requests/new]
status: draft
version: 1
updated: 2026-11-03
source:
  - "frontend: pages/student/StudentStudyingRequestsPage.tsx (nút Create request), StudentStudyingRequestCreatePage.tsx, StudentStudyingRequestForm.tsx, components/student/StudentAvailabilityEditor.tsx, StudentStudyingRequestMenu.tsx"
  - "backend: StudyingRequestController POST /api/v1/students/me/studying-requests"
  - "backend: PaymentController POST /api/v1/students/me/studying-requests/{requestId}/payments/vnpay"
error_codes: [INVALID_STUDYING_REQUEST, INVALID_PRICE_RANGE, INVALID_AVAILABILITY]
keywords: [đăng tin tìm gia sư, tạo yêu cầu học, tìm gia sư, create request, dang tin tim gia su, tao yeu cau hoc, tim gia su]
todo:
  - "Giao diện tạo tin tìm gia sư đang dùng nhãn tiếng Anh; khi Việt hóa cần cập nhật tên nút và tên ô trong tài liệu."
---

# Cách đăng tin tìm gia sư

## Các bước tạo tin tìm gia sư

1. Trong menu bên trái, chọn **My Studying Requests**.
2. Bấm **Create request**. Trang **Create a studying request** mở ra.
3. Điền **Title** (tiêu đề), chọn **Subject** (môn học) và **Grade** (lớp).
4. Chọn **Learning mode**: **Online** (trực tuyến) hoặc **In person** (trực tiếp).
5. Nếu cần, điền **Description**, **Province**, **District**, **Detail address**, **Minimum price (VND)**, **Maximum price (VND)**, **Learning goals** và **Preferred schedule**.
6. Ở mục **Availability (optional)**, thêm các khung giờ bạn rảnh trong tuần, gồm thứ, giờ bắt đầu và giờ kết thúc.
7. Bấm **Create & pay** để tạo tin và trả phí ngay, hoặc **Create draft** để lưu bản nháp.

## Khi môn học không có trong danh sách

Khi môn bạn cần học không có trong danh sách **Subject**, chọn môn khác (**Other / custom subject**) và ghi tên môn vào ô **Custom subject / note**. Tin có môn học tự nhập phải chờ nhân viên chăm sóc khách hàng duyệt sau khi trả phí.

## Trả phí để đăng tin tìm gia sư

Khi bấm **Create & pay**, PTutor lưu bản nháp rồi chuyển bạn sang trang thanh toán VNPay. Thanh toán xong, tin được đăng hoặc chuyển sang chờ duyệt.

Nếu không mở được trang thanh toán, bản nháp vẫn được lưu và trang hiện nút **Retry payment** để thử lại.

Với tin đang là bản nháp, bạn trả phí sau như sau:

1. Vào **My Studying Requests**.
2. Mở menu thao tác của tin.
3. Chọn **Pay to publish**.

## Sau khi đăng tin tìm gia sư

Khi tin đang mở, gia sư thấy tin và gửi đề xuất dạy. Bạn nhận thông báo mỗi khi có đề xuất mới, và xem đề xuất ở mục **Đề nghị từ gia sư** trong trang chi tiết tin.
