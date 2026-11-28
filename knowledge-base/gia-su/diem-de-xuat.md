---
id: diem-de-xuat
title: Điểm đề xuất của gia sư là gì
type: faq
audience: [TUTOR]
features: [tutor-profile]
routes: [/tutor/profile, /tutor/certificates]
status: draft
version: 1
updated: 2026-11-02
source:
  - "backend: TutorRecommendationScoreService.calculateAndSave (thang 0–100; đánh giá, hợp đồng hoàn tất, tỉ lệ chấp nhận, thời gian phản hồi, số học viên đã dạy, hồ sơ và chứng chỉ đã xác minh, trừ tối đa 15 điểm theo số lần bị khóa)"
  - "backend: AuthService.register (điểm khởi đầu 50)"
  - "backend: TutorRecommendationScoreScheduler (cron app.tutor-recommendation.recalculation-cron mặc định 0 30 2 * * * theo UTC); event/TutorRecommendationScoreEventListener"
  - "frontend: gia sư không xem được điểm của mình; trang quản trị sắp xếp gia sư theo điểm"
error_codes: []
keywords: [điểm đề xuất, điểm gia sư, xếp hạng gia sư, tăng điểm, được gợi ý, diem de xuat, diem gia su, xep hang, tang diem, recommendation score]
todo:
  - "Điểm đề xuất hiện được dùng ở những chỗ nào mà học viên thấy (gợi ý gia sư, thứ tự kết quả tìm kiếm)?"
  - "Gia sư có được xem điểm của mình không? Hiện giao diện gia sư không hiển thị."
---

# Điểm đề xuất của gia sư là gì

## Điểm đề xuất là gì?

Điểm đề xuất là điểm PTutor dùng để xếp hạng gia sư khi gợi ý cho học viên. Điểm nằm trong khoảng từ 0 đến 100. Gia sư mới đăng ký bắt đầu với 50 điểm. Hiện gia sư chưa xem được điểm của mình trên giao diện.

## Điểm đề xuất dựa vào những gì?

Điểm đề xuất tăng khi bạn:

- Có nhiều đánh giá tốt từ học viên.
- Có nhiều hợp đồng đã hoàn tất.
- Chấp nhận phần lớn các yêu cầu phù hợp gửi tới bạn.
- Phản hồi nhanh.
- Đã dạy nhiều học viên.
- Có hồ sơ gia sư đã được xác minh và có chứng chỉ đã được duyệt.

Điểm bị trừ khi tài khoản từng bị khóa, mỗi lần bị khóa trừ một phần, tối đa 15 điểm.

## Điểm đề xuất được cập nhật khi nào?

PTutor tính lại điểm cho mọi gia sư mỗi ngày lúc 9 giờ 30 sáng (giờ Việt Nam). Điểm cũng được tính lại ngay khi có sự kiện liên quan, gồm: chứng chỉ được duyệt hoặc bị từ chối, tài khoản bị khóa hoặc được mở lại, hợp đồng được ký, bị từ chối hoặc bị hủy.

## Làm sao để tăng điểm đề xuất?

Hoàn thiện hồ sơ và chờ được xác minh, nộp chứng chỉ để được duyệt, phản hồi học viên nhanh, dạy đầy đủ để hợp đồng hoàn tất và nhận đánh giá tốt, và tránh vi phạm dẫn tới bị khóa tài khoản.
