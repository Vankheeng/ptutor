---
id: ho-so-gia-su-duyet
title: "Duyệt hồ sơ gia sư và trạng thái hồ sơ"
type: policy
audience: [TUTOR]
features: [tutor-profile]
routes: [/tutor/profile, /tutor/notifications]
status: published
version: 1
updated: 2026-11-27
source:
  - "frontend: ProfileWorkspacePage/adminFormat.profileStatusMeta; backend: PaymentService.requireVerifiedTutorProfile, StudentRecommendationService"
  - "backend: AdminTutorProfileReviewController; AdminTutorService (duyệt chỉ khi PENDING; lý do từ chối 10–500 ký tự)"
  - "backend: TutorProfileService.updateMine (sửa hồ sơ giảng dạy -> PENDING)"
  - "backend: TutorRecommendationScoreService.verificationScore (hồ sơ đã duyệt được cộng điểm)"
  - "enum: NotificationEventType TUTOR_PROFILE_APPROVED, TUTOR_PROFILE_REJECTED"
  - "frontend: pages/tutor/TutorProfilePage.tsx (thông báo tiếng Anh về trạng thái hồ sơ)"
  - "enum: TutorProfileStatus (PENDING, VERIFIED, REJECTED)"
  - "frontend: ProfileWorkspacePage, adminFormat.profileStatusMeta (Chờ duyệt, Đã duyệt, Đã từ chối)"
  - "backend: AuthService.register (PENDING), TutorProfileService.updateMine, AdminTutorService"
error_codes: [INVALID_PROFILE_REJECTION_REASON]
keywords: [duyệt hồ sơ gia sư, hồ sơ bị từ chối, xác minh gia sư, hồ sơ chờ duyệt, duyet ho so, ho so bi tu choi, xac minh gia su, trạng thái hồ sơ, chờ duyệt, đã xác minh, không được duyệt, waiting for review, verified profile, not approved, trang thai ho so, cho duyet, da xac minh]
todo: []
---

# Duyệt hồ sơ gia sư và trạng thái hồ sơ

## Ai duyệt hồ sơ gia sư

Nhân viên chăm sóc khách hàng xét duyệt hồ sơ gia sư. Hồ sơ mới đăng ký và hồ sơ vừa sửa thông tin giảng dạy đều ở trạng thái chờ duyệt.

## Khi hồ sơ gia sư được duyệt

Khi được duyệt, hồ sơ chuyển sang đã xác minh. Bạn nhận thông báo "Tutor profile approved" với nội dung học viên đã có thể tìm thấy bạn trong phần gợi ý. Hồ sơ đã xác minh được cộng điểm trong điểm đề xuất của gia sư.

## Khi hồ sơ gia sư bị từ chối

Khi bị từ chối, bạn nhận thông báo "Tutor profile rejected" kèm lý do (từ 10 đến 500 ký tự). Trang **Hồ sơ của tôi** hiện trạng thái **Đã từ chối**; sửa thông tin giảng dạy theo lý do rồi lưu để gửi duyệt lại.

Cách gửi duyệt lại:

1. Vào **Hồ sơ của tôi**.
2. Sửa mục **Hồ sơ giảng dạy** theo lý do bị từ chối.
3. Bấm **Lưu hồ sơ**. Hồ sơ tự chuyển về chờ duyệt.

## Sửa hồ sơ sau khi đã được duyệt

Nếu sửa mục **Hồ sơ giảng dạy** sau khi đã được duyệt, hồ sơ quay về chờ duyệt và mất phần điểm cộng cho hồ sơ đã xác minh cho tới khi được duyệt lại.

## Chờ duyệt

Trang hồ sơ hiện nhãn trạng thái tiếng Việt. Nhãn hiện tại là **Chờ duyệt**, **Đã duyệt** và **Đã từ chối**.

Hồ sơ "Chờ duyệt" đang chờ nhân viên chăm sóc khách hàng xét duyệt. Đây là trạng thái của hồ sơ mới đăng ký và hồ sơ vừa sửa thông tin giảng dạy. Bạn có thể hoàn thiện thông tin và chờ kết quả; gợi ý học viên chỉ mở khi hồ sơ được duyệt.

## Đã duyệt

Hồ sơ "Đã duyệt" đã được nhân viên chăm sóc khách hàng duyệt. Học viên tìm thấy bạn trong phần gợi ý, và bạn được cộng điểm trong điểm đề xuất. Không cần làm gì thêm. Nếu sửa thông tin giảng dạy, hồ sơ quay về "Chờ duyệt".

## Đã từ chối

Hồ sơ "Đã từ chối" bị nhân viên chăm sóc khách hàng từ chối, kèm lý do trong thông báo. Bước tiếp theo: bạn sửa mục **Hồ sơ giảng dạy** theo lý do và bấm **Lưu hồ sơ** để gửi duyệt lại.

## Tiêu chí và thời gian xét duyệt (đề xuất)

Nhân viên nên kiểm tra thông tin giảng dạy rõ môn, lớp, kinh nghiệm và địa bàn; tài liệu chứng minh đọc được, không giả mạo, không chứa nội dung liên hệ ngoài nền tảng. Từ chối cần nêu lý do để gia sư sửa. Đề xuất xử lý trong 2 ngày làm việc; mã nguồn chưa tự đặt thời hạn này.

## Khi hồ sơ chưa được duyệt

Gia sư chưa được đưa vào kết quả gợi ý học viên và không thể trả phí để công bố tin nhận dạy; backend trả lỗi **TUTOR_PROFILE_NOT_VERIFIED**. Gia sư vẫn có thể hoàn thiện hồ sơ, nộp chứng chỉ và chuẩn bị bản nháp. Một số luồng đề nghị dạy không kiểm tra trạng thái hồ sơ ở cùng điểm, vì vậy không nên suy ra hồ sơ chờ duyệt có đầy đủ quyền như hồ sơ đã duyệt.
