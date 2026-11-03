---
id: tai-khoan-dang-ky
title: "Đăng ký tài khoản và cập nhật hồ sơ học viên"
type: guide
audience: [PUBLIC]
features: [account]
routes: [/register, /login, /student/profile]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: AuthController POST /api/v1/auth/register -> AuthService.register"
  - "backend: dto/request/RegisterRequest; dto/enums/RegistrationRole"
  - "frontend: pages/RegisterPage.tsx (validate, role-picker)"
  - "frontend: pages/student/StudentProfilePage.tsx (My Profile; First name, Last name, Email, Phone, Date of birth, Gender, Province, District, Avatar URL, Introduction, Learning style, Current level, Goals, Personality tags, Weak points; Save profile)"
  - "backend: StudentProfileController GET/PATCH /api/v1/students/me -> StudentProfileService"
error_codes: [EMAIL_ALREADY_EXISTS, CITIZEN_ID_ALREADY_EXISTS, INVALID_REGISTRATION_ROLE, INVALID_PROVINCE, INVALID_DISTRICT, DISTRICT_PROVINCE_MISMATCH, ADDRESS_IDS_REQUIRED_TOGETHER, EMPTY_UPDATE_REQUEST]
keywords: [đăng ký, tạo tài khoản, mở tài khoản, CCCD, căn cước, dang ky, tao tai khoan, sign up, register, hồ sơ học viên, sửa hồ sơ, thông tin cá nhân, đổi số điện thoại, ho so hoc vien, sua ho so, thong tin ca nhan, my profile]
todo:
  - "Vì sao PTutor yêu cầu số CCCD khi đăng ký và dùng số này vào việc gì? (để trả lời người dùng hỏi về quyền riêng tư)"
  - "Người dưới 18 tuổi hoặc chưa có CCCD (ví dụ học sinh tiểu học) đăng ký học viên bằng cách nào?"
  - "Nhập sai CCCD lúc đăng ký thì người dùng liên hệ ai để sửa?"
  - "Học viên muốn đổi email hoặc số CCCD đã đăng ký thì làm thế nào?"
  - "Thông tin học tập trong hồ sơ (phong cách học, trình độ, mục tiêu, điểm yếu) được dùng vào việc gì, ai xem được?"
---

# Đăng ký tài khoản và cập nhật hồ sơ học viên

## Các bước đăng ký tài khoản

Đăng ký tài khoản PTutor là miễn phí. Bạn làm như sau:

1. Mở trang **Tạo tài khoản** (bấm **Đăng ký miễn phí** ở trang đăng nhập).
2. Chọn **Tôi là học viên** hoặc **Tôi là gia sư**.
3. Điền **Họ**, **Tên**, **Email**, **Số CCCD**, **Số điện thoại**.
4. Nhập **Mật khẩu** và **Xác nhận mật khẩu**.
5. Chọn **Ngày sinh**, **Giới tính**, **Tỉnh/thành phố** và **Quận/huyện**.
6. Bấm **Đăng ký tài khoản**.

Khi đăng ký thành công, PTutor đưa bạn về trang đăng nhập với thông báo "Đăng ký thành công. Vui lòng đăng nhập để tiếp tục."

## Yêu cầu với từng thông tin khi đăng ký

Khi đăng ký tài khoản, các thông tin phải đúng các điều kiện sau:

- **Email** đúng định dạng và chưa được dùng cho tài khoản nào khác.
- **Số CCCD** gồm đúng 12 chữ số và chưa được dùng để đăng ký tài khoản nào khác.
- **Mật khẩu** dài từ 8 đến 72 ký tự. **Xác nhận mật khẩu** phải trùng với mật khẩu.
- **Ngày sinh** không được là ngày trong tương lai.
- **Quận/huyện** phải thuộc **Tỉnh/thành phố** bạn đã chọn.
- **Họ**, **Tên**, **Số điện thoại** không được để trống.

## Sau khi đăng ký tài khoản

Mỗi tài khoản mới có sẵn một ví với số dư 0đ. Tài khoản gia sư mới có hồ sơ gia sư ở trạng thái chờ xét duyệt; bạn nên hoàn thiện hồ sơ ngay sau khi đăng nhập.

Mỗi email và mỗi số CCCD chỉ đăng ký được một tài khoản. Vì vậy, một người không thể dùng cùng email hoặc cùng số CCCD để mở thêm tài khoản thứ hai với vai trò khác.

## Các bước cập nhật hồ sơ học viên

1. Trong menu bên trái, chọn **My Profile**.
2. Sửa thông tin cá nhân: **First name** (tên), **Last name** (họ), **Phone** (số điện thoại), **Date of birth** (ngày sinh), **Gender** (giới tính), **Province** (tỉnh/thành phố), **District** (quận/huyện), **Avatar URL** (đường dẫn ảnh đại diện).
3. Sửa thông tin học tập: **Introduction** (giới thiệu), **Learning style** (phong cách học), **Current level** (trình độ hiện tại), **Goals** (mục tiêu), **Personality tags** (tính cách), **Weak points** (điểm cần cải thiện).
4. Bấm **Save profile**.

Lưu thành công, trang báo "Your profile has been saved." (đã lưu hồ sơ).

## Quy định khi cập nhật hồ sơ học viên

- **Email** chỉ để xem, không sửa được trong hồ sơ.
- **Province** và **District** phải chọn cùng nhau, và quận/huyện phải thuộc tỉnh/thành phố đã chọn.

## Hồ sơ học viên dùng vào việc gì

Địa chỉ trong hồ sơ được điền sẵn khi bạn tạo tin tìm gia sư. Bạn vẫn sửa được địa chỉ riêng cho từng tin.
