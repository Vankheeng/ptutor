---
id: hop-dong-tao-hoc-vien
title: Cách soạn hợp đồng sau khi chấp nhận đề nghị dạy
type: guide
audience: [STUDENT]
features: [contract]
routes: [/student/studying-requests/:requestId, /student/studying-requests/:studyingRequestId/tutor-requests/:tutorRequestId/contracts/new, /student/contracts]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: components/student/StudentTutorOffers.tsx (nút Tạo hợp đồng)"
  - "frontend: pages/student/StudentContractCreatePage.tsx (Tạo hợp đồng; điền sẵn giá đề nghị và khung giờ chung)"
  - "frontend: components/student/StudentContractTermsForm.tsx (Giá, Chu kỳ thanh toán, Lịch học, Thêm khung giờ, Ngày bắt đầu, Ngày kết thúc, Hủy)"
  - "backend: StudentContractController POST .../studying-requests/{id}/tutor-requests/{id}/contracts -> ContractService.createFromTutorStudentRequest"
error_codes: [DUPLICATE_CONTRACT, INVALID_CONTRACT_SOURCE_STATUS, INVALID_CONTRACT_APPLICATION_STATUS, INVALID_CONTRACT_SCHEDULE, INVALID_CONTRACT_DATE_RANGE]
keywords: [tạo hợp đồng, soạn hợp đồng, lập hợp đồng với gia sư, tao hop dong, soan hop dong, lap hop dong]
todo: []
---

# Cách soạn hợp đồng sau khi chấp nhận đề nghị dạy

## Các bước soạn hợp đồng

1. Vào **My Studying Requests**, mở tin đã chấp nhận gia sư.
2. Ở mục **Đề nghị từ gia sư**, bấm **Tạo hợp đồng** tại đề nghị đã chấp nhận.
3. Trang **Tạo hợp đồng** hiện **Thông tin học viên**, **Thông tin gia sư** và các thông tin cố định: **Môn học**, **Lớp**, **Hình thức dạy**.
4. Kiểm tra hoặc sửa **Giá**. Giá được điền sẵn bằng giá gia sư đề nghị.
5. Chọn **Chu kỳ thanh toán**: **Mỗi buổi**, **Hàng tuần**, **Hàng tháng** hoặc **Theo gói**.
6. Ở **Lịch học**, chọn **Ngày**, giờ **Từ** và giờ **Đến**. Bấm **Thêm khung giờ** để thêm buổi khác trong tuần.
7. Chọn **Ngày bắt đầu** và **Ngày kết thúc**.
8. Bấm **Tạo hợp đồng**.

Khi tạo thành công, trang báo "Đã tạo hợp đồng và gửi cho gia sư." Gia sư nhận thông báo cần ký hợp đồng.

## Lịch học được điền sẵn khi soạn hợp đồng

Khi mở trang **Tạo hợp đồng**, PTutor điền sẵn những khung giờ trùng nhau giữa lịch rảnh của bạn và thời gian gia sư có thể dạy. Bạn có thể sửa hoặc xóa các khung giờ này.

## Sau khi soạn hợp đồng

Hợp đồng ở trạng thái "Đang chờ" cho tới khi gia sư ký. Trong lúc chờ, bạn sửa được **Giá** và **Lịch học** trong trang chi tiết hợp đồng, hoặc bấm **Hủy hợp đồng**. Chu kỳ thanh toán và ngày bắt đầu, ngày kết thúc không sửa được sau khi gửi.
