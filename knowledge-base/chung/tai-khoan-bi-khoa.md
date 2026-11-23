---
id: tai-khoan-bi-khoa
title: "Tài khoản bị tạm khóa hoặc khóa vĩnh viễn"
type: policy
audience: [STUDENT, TUTOR]
features: [account]
routes: [/login, /student/notifications, /tutor/notifications]
status: draft
version: 1
updated: 2026-11-03
source:
  - "backend: AccountSuspensionService.suspend/reactivate (lý do 10–1000 ký tự; thu hồi mọi phiên đăng nhập)"
  - "backend: UserAccessService.refreshAccessState, reactivateExpiredAccounts (tự mở khóa khi hết hạn)"
  - "backend: AccountSuspensionScheduler (app.account-suspension.expiration-interval-ms, mặc định 60000 ms)"
  - "backend: AuthService.accountUnavailable; security/AccountStatusFilter"
  - "backend: TutorRecommendationScoreService (trừ điểm theo số lần bị khóa)"
  - "enum: UserStatus, SuspensionType; NotificationEventType ACCOUNT_SUSPENDED, ACCOUNT_REACTIVATED"
  - "enum: UserStatus (ACTIVE, INACTIVE, BLOCKED); SuspensionType (TEMPORARY, PERMANENT)"
  - "frontend: utils/tutorDisplay.ts statusLabels (Đang hoạt động, Ngừng hoạt động, Đã khóa)"
  - "backend: AuthService.accountUnavailable; AccountStatusFilter"
error_codes: [ACCOUNT_TEMPORARILY_SUSPENDED, ACCOUNT_PERMANENTLY_SUSPENDED, ACCOUNT_SUSPENDED, ACCOUNT_INACTIVE, USER_INACTIVE]
keywords: [tài khoản bị khóa, tạm khóa, khóa vĩnh viễn, mở khóa tài khoản, bị chặn, tai khoan bi khoa, tam khoa, khoa vinh vien, mo khoa, account suspended, trạng thái tài khoản, đang hoạt động, ngừng hoạt động, đã khóa, trang thai tai khoan, dang hoat dong, ngung hoat dong, da khoa]
todo:
  - "Những hành vi nào dẫn tới tạm khóa và những hành vi nào dẫn tới khóa vĩnh viễn?"
  - "Người dùng muốn khiếu nại quyết định khóa tài khoản thì liên hệ kênh nào, trong bao lâu?"
  - "Khi tài khoản bị khóa, hợp đồng đang hiệu lực, buổi học và số dư ví của người đó được xử lý thế nào?"
  - "Khi nào một tài khoản chuyển sang trạng thái Ngừng hoạt động? (code không có thao tác nào đặt trạng thái này) Người dùng làm gì để kích hoạt lại?"
  - "Nhãn 'Tạm khóa' và 'Khóa vĩnh viễn' do người soạn đặt vì giao diện người dùng không hiển thị; admin xác nhận nhãn."
---

# Tài khoản bị tạm khóa hoặc khóa vĩnh viễn

## Ai khóa tài khoản và có những kiểu khóa nào

Nhân viên chăm sóc khách hàng hoặc quản trị viên PTutor có thể khóa tài khoản học viên hoặc gia sư. Mỗi lần khóa phải ghi lý do. Có hai kiểu khóa:

- **Tạm khóa**: có thời điểm kết thúc. Hết thời điểm đó, tài khoản tự mở lại.
- **Khóa vĩnh viễn**: không có thời điểm kết thúc. Chỉ nhân viên hoặc quản trị viên mở lại được.

## Điều gì xảy ra khi tài khoản bị khóa

Khi tài khoản bị khóa:

- PTutor đăng xuất tài khoản khỏi mọi thiết bị ngay lập tức.
- Bạn không đăng nhập được. Trang đăng nhập hiện câu tiếng Anh cho biết tài khoản bị khóa, thời điểm hết khóa (với tạm khóa) và lý do.
- Bạn nhận một thông báo "Account suspended" trong mục thông báo, ghi kiểu khóa và lý do.
- Trên giao diện, trạng thái tài khoản hiện là "Đã khóa".
- Mọi thao tác khác đều bị từ chối, kể cả khai báo tài khoản ngân hàng và rút tiền.

## Khi nào tài khoản được mở lại

Với tạm khóa, PTutor kiểm tra mỗi phút và tự mở tài khoản khi đã qua thời điểm kết thúc. Bạn cũng được mở ngay nếu đăng nhập sau thời điểm đó. Khi tài khoản được mở lại, bạn nhận thông báo "Account reactivated".

Với khóa vĩnh viễn, tài khoản chỉ được mở lại khi nhân viên hoặc quản trị viên xem xét và mở khóa, kèm lý do.

## Ảnh hưởng tới gia sư

Với gia sư, mỗi lần bị khóa làm giảm điểm đề xuất của gia sư, tối đa trừ 15 điểm. Phần trừ này vẫn tính sau khi tài khoản đã được mở lại.

## Thời điểm hết khóa hiển thị theo giờ quốc tế

Câu báo lỗi khi đăng nhập ghi thời điểm hết tạm khóa theo giờ quốc tế (có chữ UTC). Giờ Việt Nam nhanh hơn 7 tiếng. Ví dụ "until 2026-11-05T03:00 UTC" nghĩa là tài khoản mở lại lúc 10 giờ sáng ngày 05/11/2026 giờ Việt Nam.

## Đang hoạt động

Tài khoản "Đang hoạt động" dùng được đầy đủ các chức năng của vai trò. Đây là trạng thái của mọi tài khoản ngay sau khi đăng ký. Bạn không cần làm gì thêm.

## Ngừng hoạt động

Tài khoản "Ngừng hoạt động" không đăng nhập được và mọi thao tác bị từ chối. Khi đăng nhập, bạn thấy câu tiếng Anh "Account is inactive". Bạn cần liên hệ PTutor để được hỗ trợ.

## Đã khóa

Tài khoản "Đã khóa" bị nhân viên hoặc quản trị viên PTutor khóa, kèm lý do. Tài khoản không đăng nhập được. Trạng thái này có hai kiểu:

### Tạm khóa

Tài khoản tạm khóa có thời điểm kết thúc. Khi qua thời điểm đó, tài khoản tự trở lại "Đang hoạt động" và bạn nhận thông báo. Bạn chỉ cần chờ.

### Khóa vĩnh viễn

Tài khoản khóa vĩnh viễn không tự mở lại. Chỉ nhân viên hoặc quản trị viên mở khóa được sau khi xem xét. Chi tiết xem mục "Khi nào tài khoản được mở lại" trong tài liệu này.
