---
id: khieu-nai-gui
title: "Gửi khiếu nại: các loại khiếu nại, ai được gửi, sửa, hủy"
type: policy
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/complaints, /student/complaints/new, /tutor/complaints, /tutor/complaints/new]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: ComplaintsPage/ComplaintCreatePage/ComplaintEvidenceField"
  - "backend: ComplaintService.create (hợp đồng còn hiệu lực hoặc tối đa 7 ngày sau ngày kết thúc)"
  - "backend: ComplaintController POST/GET/PUT /api/v1/users/me/complaints, POST /{id}/cancel"
  - "backend: ComplaintService.create (người gửi phải là một bên của hợp đồng), update/cancel (chỉ PENDING)"
  - "backend: dto/request/ComplaintCreateRequest (tiêu đề tối đa 255 ký tự, nội dung tối đa 10000 ký tự, minh chứng là đường dẫn)"
  - "enum: ComplaintCategory (ABSENCE, LATE_OR_SHORT, TEACHING_QUALITY, MISCONDUCT, SERIOUS_MISCONDUCT, OTHER)"
  - "frontend: ComplaintsPage, ComplaintCreatePage, ComplaintDetailPage, ComplaintEvidenceField"
error_codes: [CONTRACT_NOT_FOUND, INVALID_COMPLAINT_STATUS_TRANSITION, COMPLAINT_NOT_FOUND]
keywords: [khiếu nại, gửi khiếu nại, tố cáo gia sư, phản ánh, loại khiếu nại, khieu nai, gui khieu nai, to cao, phan anh, complaint]
todo: []
---

# Gửi khiếu nại: các loại khiếu nại, ai được gửi, sửa, hủy

## Giao diện khiếu nại

Học viên mở **Khiếu nại**, gia sư mở **Khiếu nại** trong menu, sau đó chọn **Tạo khiếu nại**. Trang **Tạo khiếu nại mới** cho phép chọn hợp đồng, người bị khiếu nại, nội dung và minh chứng. Bạn theo dõi khiếu nại đã gửi và khiếu nại nhận được ở các tab tương ứng.

## Ai được gửi khiếu nại

Khiếu nại luôn gắn với một hợp đồng. Chỉ học viên hoặc gia sư của hợp đồng đó mới gửi được khiếu nại. Bên còn lại của hợp đồng là bên bị khiếu nại.

## Các loại khiếu nại

Khi gửi khiếu nại, bạn chọn một loại:

- **Vắng mặt**: một bên không có mặt ở buổi học.
- **Muộn / thiếu giờ**: tới muộn hoặc dạy, học không đủ giờ.
- **Chất lượng giảng dạy**.
- **Vi phạm ứng xử**.
- **Vi phạm nghiêm trọng**.
- **Khác**.

## Nội dung khiếu nại

Mỗi khiếu nại gồm:

- Tiêu đề: bắt buộc, tối đa 255 ký tự.
- Nội dung: bắt buộc, tối đa 10.000 ký tự.
- Minh chứng: không bắt buộc; có thể tải tệp trực tiếp lên PTutor.

## Sửa và hủy khiếu nại

Bạn sửa hoặc hủy được khiếu nại khi khiếu nại còn "Chờ tiếp nhận", tức nhân viên chăm sóc khách hàng chưa nhận xử lý. Khi đã được tiếp nhận, bạn không sửa hay hủy được nữa.

## Khiếu nại và hoàn tiền khác nhau thế nào

Yêu cầu hoàn tiền dùng khi buổi học gia sư đánh dấu đã dạy nhưng không diễn ra, và kế toán xử lý. Khiếu nại dùng cho các vấn đề khác của hợp đồng, và nhân viên chăm sóc khách hàng xử lý. Trong lúc khiếu nại chưa có kết luận, các buổi học chờ xác nhận của hợp đồng bị giữ lại và yêu cầu hoàn tiền của hợp đồng chưa được kết luận.

## Thời hạn gửi

Bạn có thể gửi khiếu nại khi hợp đồng còn hiệu lực hoặc trong **7 ngày sau ngày kết thúc hợp đồng**. Hệ thống từ chối yêu cầu ngoài thời hạn này. Khi cần bổ sung thông tin cho vụ việc đã gửi, mở trang chi tiết khiếu nại và thêm minh chứng.
