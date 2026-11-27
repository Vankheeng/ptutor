---
id: goi-y-hoc-vien-thong-minh
title: Tìm và nhận gợi ý học viên phù hợp cho gia sư
type: guide
audience: [TUTOR]
features: [matching, studying-request]
routes: [/tutor/students, /tutor/profile, /tutor/studying-requests/:requestId]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: TutorStudentRecommendationsPage, TutorStudentSearchFilters, SavedSearchControls"
  - "backend: RecommendationService, StudentRecommendationService"
error_codes: [TUTOR_PROFILE_NOT_VERIFIED]
keywords: [gợi ý học viên, tìm học viên, điểm phù hợp, hồ sơ chưa duyệt, lưu học viên, goi y hoc vien, tim hoc vien]
todo: []
---

# Tìm và nhận gợi ý học viên phù hợp cho gia sư

## Dùng trang Tìm kiếm học viên

Gia sư mở **Tìm kiếm học viên**. Chọn môn, lớp, hình thức dạy, khu vực, mức học phí và lịch mong muốn; bấm tìm kiếm hoặc **Gợi ý thông minh** để xếp các tin tìm gia sư theo mức phù hợp. Có thể thêm lớp, đối tượng muốn dạy hoặc chọn **Dùng tiêu chí từ tin dạy của tôi**. Ô **Mô tả nhanh bằng một câu** chỉ hỗ trợ điền bộ lọc; hãy kiểm tra lại trước khi tìm.

Gợi ý thông minh yêu cầu **hồ sơ gia sư đã được duyệt**. Nếu trang báo hồ sơ chưa được duyệt, bấm **Xem hồ sơ**, hoàn thiện thông tin giảng dạy và chờ kết quả. Khi thiếu môn hoặc lớp, trang sẽ nêu ô cần bổ sung.

## Đọc và xử lý gợi ý

Thẻ kết quả có **điểm phù hợp** 0–100, giá, hình thức học, lý do phù hợp và đôi khi có cảnh báo về điều kiện chưa khớp. Điểm tính cho bộ lọc hiện tại và không bảo đảm học viên chấp nhận đề nghị. Nhãn **Gợi ý mới** là lựa chọn bổ sung để khám phá. Nếu ít kết quả, xem đề xuất nới bộ lọc và bấm **Áp dụng** khi phù hợp.

Gia sư có thể **Lưu học viên**, chọn **Không quan tâm**, **Xem chi tiết tin** hoặc **Xem và gửi đề nghị**. Đọc kỹ nội dung tin và mức giá trước khi gửi đề nghị. Tin tìm gia sư phải còn mở để nhận đề nghị mới.
