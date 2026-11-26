---
id: ho-so-gia-su-duyet
title: "Duyệt hồ sơ gia sư và trạng thái hồ sơ"
type: policy
audience: [TUTOR]
features: [tutor-profile]
routes: [/tutor/profile, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: AdminTutorProfileReviewController; AdminTutorService (duyệt chỉ khi PENDING; lý do từ chối 10–500 ký tự)"
  - "backend: TutorProfileService.updateMine (sửa hồ sơ giảng dạy -> PENDING)"
  - "backend: TutorRecommendationScoreService.verificationScore (hồ sơ đã duyệt được cộng điểm)"
  - "enum: NotificationEventType TUTOR_PROFILE_APPROVED, TUTOR_PROFILE_REJECTED"
  - "frontend: pages/tutor/TutorProfilePage.tsx (thông báo tiếng Anh về trạng thái hồ sơ)"
  - "enum: TutorProfileStatus (PENDING, VERIFIED, REJECTED)"
  - "frontend: pages/tutor/TutorProfilePage.tsx (Waiting for review, Verified profile, Not approved)"
  - "backend: AuthService.register (PENDING), TutorProfileService.updateMine, AdminTutorService"
error_codes: [INVALID_PROFILE_REJECTION_REASON]
keywords: [duyệt hồ sơ gia sư, hồ sơ bị từ chối, xác minh gia sư, hồ sơ chờ duyệt, duyet ho so, ho so bi tu choi, xac minh gia su, trạng thái hồ sơ, chờ duyệt, đã xác minh, không được duyệt, waiting for review, verified profile, not approved, trang thai ho so, cho duyet, da xac minh]
todo:
  - "Tiêu chí duyệt hồ sơ gia sư là gì? Hồ sơ được duyệt trong bao lâu?"
  - "Gia sư chưa được duyệt hồ sơ bị hạn chế những gì? (code hiện không chặn gửi đề xuất hay nhận hợp đồng)"
  - "Trang hồ sơ gia sư đang hiển thị trạng thái bằng tiếng Anh; admin xác nhận nhãn tiếng Việt: Chờ duyệt, Đã xác minh, Không được duyệt."
---

# Duyệt hồ sơ gia sư và trạng thái hồ sơ

## Ai duyệt hồ sơ gia sư

Nhân viên chăm sóc khách hàng xét duyệt hồ sơ gia sư. Hồ sơ mới đăng ký và hồ sơ vừa sửa thông tin giảng dạy đều ở trạng thái chờ duyệt.

## Khi hồ sơ gia sư được duyệt

Khi được duyệt, hồ sơ chuyển sang đã xác minh. Bạn nhận thông báo "Tutor profile approved" với nội dung học viên đã có thể tìm thấy bạn trong phần gợi ý. Hồ sơ đã xác minh được cộng điểm trong điểm đề xuất của gia sư.

## Khi hồ sơ gia sư bị từ chối

Khi bị từ chối, bạn nhận thông báo "Tutor profile rejected" kèm lý do (từ 10 đến 500 ký tự). Trang **Hồ sơ của tôi** hiện câu "Your profile was not approved." và hướng dẫn "Update your teaching details and save to send it for review again."

Cách gửi duyệt lại:

1. Vào **Hồ sơ của tôi**.
2. Sửa mục **Hồ sơ giảng dạy** theo lý do bị từ chối.
3. Bấm **Lưu hồ sơ**. Hồ sơ tự chuyển về chờ duyệt.

## Sửa hồ sơ sau khi đã được duyệt

Nếu sửa mục **Hồ sơ giảng dạy** sau khi đã được duyệt, hồ sơ quay về chờ duyệt và mất phần điểm cộng cho hồ sơ đã xác minh cho tới khi được duyệt lại.

## Chờ duyệt (Waiting for review)

Trang **Hồ sơ của tôi** hiện trạng thái hồ sơ bằng tiếng Anh, ghi trong ngoặc ở từng mục.

Hồ sơ "Chờ duyệt" đang chờ nhân viên chăm sóc khách hàng xét duyệt. Đây là trạng thái của hồ sơ mới đăng ký và hồ sơ vừa sửa thông tin giảng dạy. Trang hiện "Your profile is waiting for review. Students can find you once it is approved." Bạn chỉ cần chờ kết quả.

## Đã xác minh (Verified profile)

Hồ sơ "Đã xác minh" đã được nhân viên chăm sóc khách hàng duyệt. Học viên tìm thấy bạn trong phần gợi ý, và bạn được cộng điểm trong điểm đề xuất. Không cần làm gì thêm. Nếu sửa thông tin giảng dạy, hồ sơ quay về "Chờ duyệt".

## Không được duyệt (Not approved)

Hồ sơ "Không được duyệt" bị nhân viên chăm sóc khách hàng từ chối, kèm lý do trong thông báo. Bước tiếp theo: bạn sửa mục **Hồ sơ giảng dạy** theo lý do và bấm **Lưu hồ sơ** để gửi duyệt lại.
