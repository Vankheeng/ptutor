---
id: ho-so-gia-su
title: Hoàn thiện hồ sơ gia sư và gửi duyệt
type: guide
audience: [TUTOR]
features: [tutor-profile]
routes: [/tutor/profile]
status: draft
version: 1
updated: 2026-11-02
source:
  - "frontend: pages/tutor/TutorProfilePage.tsx (Chỉnh sửa hồ sơ, Lưu hồ sơ), components/TutorPersonalInformationSection.tsx, TutorProfilePhotoSection.tsx (JPG/PNG, tối đa 10 MB), TutorTeachingProfileSection.tsx"
  - "backend: TutorProfileController GET/PATCH /api/v1/tutors/me -> TutorProfileService.updateMine (sửa thông tin chuyên môn -> PENDING)"
  - "backend: dto/request/UpdateTutorProfileRequest.hasReviewableProfileUpdate"
error_codes: [EMPTY_UPDATE_REQUEST, ADDRESS_IDS_REQUIRED_TOGETHER, DISTRICT_PROVINCE_MISMATCH, TUTOR_PROFILE_NOT_FOUND]
keywords: [hồ sơ gia sư, sửa hồ sơ, ảnh đại diện, gửi duyệt hồ sơ, kinh nghiệm, ho so gia su, sua ho so, anh dai dien, gui duyet]
todo: []
---

# Hoàn thiện hồ sơ gia sư và gửi duyệt

## Các bước cập nhật hồ sơ gia sư

1. Trong menu, chọn **Hồ sơ của tôi**. Trang **Chỉnh sửa hồ sơ** mở ra.
2. Ở mục **Thông tin cá nhân**, sửa **Tên**, **Họ**, **Số điện thoại**, **Ngày sinh**, **Giới tính**, **Tỉnh/thành phố**, **Quận/huyện**, **Địa chỉ cụ thể**.
3. Ở mục **Ảnh hồ sơ**, bấm **Chọn ảnh** để tải ảnh đại diện.
4. Ở mục **Hồ sơ giảng dạy**, điền **Giới thiệu bản thân**, **Số năm kinh nghiệm**, **Trình độ học vấn**, **Môn học giảng dạy**, **Từ khóa phong cách giảng dạy**, **Phương pháp giảng dạy**, **Đối tượng học viên phù hợp**.
5. Bấm **Lưu hồ sơ**.

Lưu thành công, trang báo "Hồ sơ của bạn đã được lưu."

## Ảnh đại diện

Ảnh đại diện phải là JPG hoặc PNG, tối đa 10 MB. Sau khi tải ảnh, trang báo "Đã tải ảnh lên. Hãy lưu hồ sơ để áp dụng ảnh này." Bạn phải bấm **Lưu hồ sơ** thì ảnh mới được áp dụng.

## Khi nào hồ sơ gia sư được gửi duyệt lại

Khi bạn sửa bất kỳ thông tin nào trong mục **Hồ sơ giảng dạy** và lưu, hồ sơ tự chuyển về trạng thái chờ duyệt, kể cả khi hồ sơ đã được duyệt trước đó. Trang ghi "Thông tin chuyên môn có thể được Ptutor xem xét."

Sửa thông tin cá nhân hoặc ảnh đại diện không làm hồ sơ phải duyệt lại.

## Quy định khi cập nhật hồ sơ gia sư

- **Tỉnh/thành phố** và **Quận/huyện** chọn cả hai hoặc để trống cả hai.
- **Số năm kinh nghiệm** không được âm.
- Các ô đã điền không được để toàn khoảng trắng.
