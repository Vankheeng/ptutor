---
id: khieu-nai-gui
title: "Gửi khiếu nại: các loại khiếu nại, ai được gửi, sửa, hủy"
type: policy
audience: [STUDENT, TUTOR]
features: [complaint]
routes: [/student/contracts/:contractId, /student/notifications, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: ComplaintController POST/GET/PUT /api/v1/users/me/complaints, POST /{id}/cancel"
  - "backend: ComplaintService.create (người gửi phải là một bên của hợp đồng), update/cancel (chỉ PENDING)"
  - "backend: dto/request/ComplaintCreateRequest (tiêu đề tối đa 255 ký tự, nội dung tối đa 10000 ký tự, minh chứng là đường dẫn)"
  - "enum: ComplaintCategory (ABSENCE, LATE_OR_SHORT, TEACHING_QUALITY, MISCONDUCT, SERIOUS_MISCONDUCT, OTHER)"
  - "frontend: pages/admin/complaintFormat.ts complaintCategoryLabels; chưa có trang khiếu nại cho học viên và gia sư"
error_codes: [CONTRACT_NOT_FOUND, INVALID_COMPLAINT_STATUS_TRANSITION, COMPLAINT_NOT_FOUND]
keywords: [khiếu nại, gửi khiếu nại, tố cáo gia sư, phản ánh, loại khiếu nại, khieu nai, gui khieu nai, to cao, phan anh, complaint]
todo:
  - "Khi nào có giao diện gửi và theo dõi khiếu nại cho học viên và gia sư? Tên trang và tên nút là gì?"
  - "Có thời hạn gửi khiếu nại sau khi sự việc xảy ra hoặc sau khi hợp đồng kết thúc không?"
---

# Gửi khiếu nại: các loại khiếu nại, ai được gửi, sửa, hủy

## Giao diện khiếu nại

Hiện giao diện học viên và gia sư chưa có trang gửi khiếu nại. Các quy định dưới đây là quy định của hệ thống và sẽ áp dụng khi có giao diện. Bạn vẫn nhận thông báo về khiếu nại trong mục thông báo.

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
- Minh chứng: không bắt buộc, là đường dẫn tới ảnh, video hoặc tài liệu.

## Sửa và hủy khiếu nại

Bạn sửa hoặc hủy được khiếu nại khi khiếu nại còn "Chờ tiếp nhận", tức nhân viên chăm sóc khách hàng chưa nhận xử lý. Khi đã được tiếp nhận, bạn không sửa hay hủy được nữa.

## Khiếu nại và hoàn tiền khác nhau thế nào

Yêu cầu hoàn tiền dùng khi buổi học gia sư đánh dấu đã dạy nhưng không diễn ra, và kế toán xử lý. Khiếu nại dùng cho các vấn đề khác của hợp đồng, và nhân viên chăm sóc khách hàng xử lý. Trong lúc khiếu nại chưa có kết luận, các buổi học chờ xác nhận của hợp đồng bị giữ lại và yêu cầu hoàn tiền của hợp đồng chưa được kết luận.
