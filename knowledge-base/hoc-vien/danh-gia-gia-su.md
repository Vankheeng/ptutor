---
id: danh-gia-gia-su
title: Đánh giá gia sư sau hợp đồng
type: faq
audience: [STUDENT, TUTOR]
features: [review, contract]
routes: [/student/contracts/:contractId]
status: published
version: 1
updated: 2026-11-27
source:
  - "backend: StudentReviewController, ReviewService, ReviewCreateRequest"
  - "frontend: App.tsx và StudentContractDetailPage (chưa có biểu mẫu gửi đánh giá)"
error_codes: [CONTRACT_NOT_COMPLETED, REVIEW_ALREADY_EXISTS]
keywords: [đánh giá gia sư, viết nhận xét, chấm sao, hợp đồng hoàn tất, danh gia gia su, cham sao, review]
todo: []
---

# Đánh giá gia sư sau hợp đồng

## Điều kiện đánh giá

Hệ thống có API để **học viên của hợp đồng đã hoàn tất** gửi một đánh giá cho gia sư: từ 1 đến 5 sao, nhận xét tùy chọn tối đa 2.000 ký tự. Mỗi hợp đồng chỉ có một đánh giá; buổi học riêng lẻ không có đánh giá. Đánh giá góp phần vào điểm đề xuất của gia sư. Trang chủ hiện một số đánh giá 5 sao mới nhất.

## Giao diện hiện tại

Giao diện học viên hiện **chưa có nút gửi đánh giá**, nên không có bước thao tác tự phục vụ để hướng dẫn. Chưa có ngày phát hành giao diện được công bố. Backend cũng chưa cung cấp API cho học viên sửa hoặc xóa đánh giá đã gửi. Gia sư chưa có tính năng phản hồi công khai; nếu cần phản ánh đánh giá không đúng sự thật, hãy dùng **Yêu cầu hỗ trợ** và nêu mã hợp đồng.
