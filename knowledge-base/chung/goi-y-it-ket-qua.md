---
id: goi-y-it-ket-qua
title: Vì sao gợi ý gia sư hoặc học viên có ít kết quả
type: faq
audience: [STUDENT, TUTOR]
features: [matching, tutor-profile]
routes: [/student/teaching-requests, /tutor/students, /tutor/profile]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: StudentTutorRecommendations, TutorStudentRecommendationsPage"
  - "backend: RecommendationService, StudentRecommendationService"
error_codes: [TUTOR_PROFILE_NOT_VERIFIED]
keywords: [gợi ý ít kết quả, không có gia sư phù hợp, điểm phù hợp thấp, cần thêm môn lớp, nới bộ lọc, goi y it ket qua]
todo: []
---

# Vì sao gợi ý gia sư hoặc học viên có ít kết quả

## Thiếu thông tin bắt buộc

Gợi ý thông minh cần ít nhất môn học và lớp. Học viên điền **Môn học** và **Lớp**; gia sư chọn môn và lớp muốn dạy. Ô mô tả bằng câu tự nhiên chỉ giúp điền bộ lọc, nên hãy kiểm tra lại các ô sau khi bấm **Điền bộ lọc từ câu này**.

## Bộ lọc quá hẹp

Khu vực, học phí, lịch rảnh, phong cách và các tiêu chí bổ sung có thể khiến rất ít tin hoặc gia sư khớp. Thử nới từng điều kiện mà bạn có thể linh hoạt, rồi tìm lại. Nếu trang gợi ý hiện đề xuất nới bộ lọc, đọc nội dung và bấm **Áp dụng** khi phù hợp. Chọn học trực tuyến có thể thay đổi điều kiện khu vực so với học trực tiếp.

## Hồ sơ gia sư chưa được duyệt

Gia sư cần hồ sơ **Đã duyệt** để dùng gợi ý học viên; tin nhận dạy chưa được công bố cũng không hiện như tin đang mở cho học viên. Hãy kiểm tra trạng thái hồ sơ và tin trước khi kết luận hệ thống thiếu dữ liệu.

## Hiểu điểm phù hợp

Điểm phù hợp 0–100 được tính cho bộ tiêu chí hiện tại, không phải điểm chất lượng tuyệt đối hay lời bảo đảm hai bên sẽ ký hợp đồng. Nhãn **Gợi ý mới** là lựa chọn bổ sung để khám phá; hãy đọc lý do phù hợp, lưu ý và hồ sơ trước khi quyết định gửi đơn hoặc đề nghị.
